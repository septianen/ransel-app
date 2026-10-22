package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.EmailVerificationTokens
import app.ransel.server.db.Users
import app.ransel.server.plugins.ApiException
import app.ransel.server.security.EmailSender
import app.ransel.server.security.TokenService
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.VerifyEmailResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import kotlin.time.toJavaDuration

/**
 * Email verification after sign-up (TRD "Auth and security"). Until the link is opened the account works, except
 * Explore and vouchers ([requireVerifiedEmail]), and the wallet holds no signup grant; [verify] gives it.
 */
class EmailVerificationService(
    private val config: AppConfig,
    private val tokens: TokenService,
    private val email: EmailSender,
    private val credits: CreditService,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(EmailVerificationService::class.java)

    /**
     * Voids the account's open links and emails a new one, in the background so sign-up does not wait for SMTP.
     * Does nothing for an account that is already verified.
     */
    fun send(userId: UUID) {
        scope.launch {
            try {
                val token = tokens.newOpaqueToken()
                val user = transaction {
                    val user = Users.selectAll().where { (Users.id eq userId) and Users.deletedAt.isNull() }.singleOrNull()
                    if (user == null || user[Users.emailVerifiedAt] != null || user[Users.email] == null) return@transaction null
                    val now = Instant.now()
                    EmailVerificationTokens.update({ (EmailVerificationTokens.userId eq userId) and EmailVerificationTokens.usedAt.isNull() }) {
                        it[usedAt] = now
                    }
                    EmailVerificationTokens.insert {
                        it[EmailVerificationTokens.userId] = userId
                        it[tokenHash] = TokenService.sha256(token)
                        it[expiresAt] = now.plus(config.emailVerificationTtl.toJavaDuration())
                    }
                    user
                } ?: return@launch
                email.sendEmailVerification(
                    toEmail = user[Users.email].orEmpty(),
                    displayName = user[Users.displayName],
                    link = "${config.appBaseUrl}/verify-email?token=$token",
                )
            } catch (e: Exception) {
                log.error("Sending the verification email failed", e)
            }
        }
    }

    /**
     * Marks the email verified and gives the signup credits. A link that was already used answers 200 too when its
     * account is verified (someone tapping the link twice), with `creditsGranted = 0`.
     */
    fun verify(token: String): VerifyEmailResponse {
        val userId = transaction {
            val now = Instant.now()
            val row = EmailVerificationTokens.selectAll()
                .where { EmailVerificationTokens.tokenHash eq TokenService.sha256(token) }
                .forUpdate()
                .singleOrNull()
                ?: throw invalid()
            val userId = row[EmailVerificationTokens.userId]
            val user = Users.selectAll().where { (Users.id eq userId) and Users.deletedAt.isNull() }.forUpdate().singleOrNull()
                ?: throw invalid()
            if (user[Users.emailVerifiedAt] != null) return@transaction userId
            if (row[EmailVerificationTokens.usedAt] != null || row[EmailVerificationTokens.expiresAt] <= now) throw invalid()
            Users.update({ Users.id eq userId }) { it[emailVerifiedAt] = now }
            EmailVerificationTokens.update({ (EmailVerificationTokens.userId eq userId) and EmailVerificationTokens.usedAt.isNull() }) {
                it[usedAt] = now
            }
            userId
        }
        val granted = credits.grantSignup(userId)
        val address = transaction { Users.selectAll().where { Users.id eq userId }.single()[Users.email].orEmpty() }
        return VerifyEmailResponse(email = address, creditsGranted = granted)
    }

    private fun invalid() =
        ApiException(HttpStatusCode.BadRequest, ErrorCode.VERIFY_TOKEN_INVALID, "This verification link is no longer valid")
}

/** 403 EMAIL_NOT_VERIFIED until the account's email is verified. For the Explore and voucher routes. */
fun requireVerifiedEmail(userId: UUID) {
    val verified = transaction {
        Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.emailVerifiedAt) != null
    }
    if (!verified) throw ApiException(HttpStatusCode.Forbidden, ErrorCode.EMAIL_NOT_VERIFIED, "Verify your email first")
}
