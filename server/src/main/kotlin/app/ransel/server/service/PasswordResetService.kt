package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.PasswordResetTokens
import app.ransel.server.db.RefreshTokens
import app.ransel.server.db.Users
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.requireValid
import app.ransel.server.security.EmailSender
import app.ransel.server.security.PasswordHasher
import app.ransel.server.security.TokenService
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.checkPassword
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import kotlin.time.toJavaDuration

/**
 * Forgot / reset password (TRD "Auth and security").
 * `forgot` is deliberately fire-and-forget: the HTTP answer is always 204 and takes the same time whether
 * or not the email exists, so it cannot be used to find out who has an account.
 */
class PasswordResetService(
    private val config: AppConfig,
    private val hasher: PasswordHasher,
    private val tokens: TokenService,
    private val email: EmailSender,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(PasswordResetService::class.java)

    fun forgot(rawEmail: String) {
        val address = rawEmail.trim()
        scope.launch {
            try {
                val user = transaction {
                    Users.selectAll()
                        .where { (Users.email.lowerCase() eq address.lowercase()) and Users.deletedAt.isNull() }
                        .singleOrNull()
                } ?: return@launch
                email.sendPasswordReset(
                    toEmail = user[Users.email].orEmpty(),
                    displayName = user[Users.displayName],
                    link = issueLink(user[Users.id], config.passwordResetTtl),
                )
            } catch (e: Exception) {
                // Never surface this to the caller: that would reveal whether the email exists.
                log.error("Password reset request failed", e)
            }
        }
    }

    /**
     * An admin created [userId] in Karung: email a link that sets the first password, valid [AppConfig.inviteTtl].
     * Same token table and `/reset-password` page as a reset. Fire-and-forget like [forgot]; a failure is only logged
     * (the admin can send a fresh link with "Kirim email reset kata sandi").
     */
    fun invite(userId: UUID) {
        scope.launch {
            try {
                val user = transaction { Users.selectAll().where { Users.id eq userId }.single() }
                email.sendAccountInvite(
                    toEmail = user[Users.email].orEmpty(),
                    displayName = user[Users.displayName],
                    link = issueLink(userId, config.inviteTtl),
                )
            } catch (e: Exception) {
                log.error("Account invite failed", e)
            }
        }
    }

    /** Stores the hash of a new single-use token and returns the link carrying the token itself. */
    private fun issueLink(userId: UUID, ttl: kotlin.time.Duration): String {
        val token = tokens.newOpaqueToken()
        transaction {
            PasswordResetTokens.insert {
                it[PasswordResetTokens.userId] = userId
                it[tokenHash] = TokenService.sha256(token)
                it[expiresAt] = Instant.now().plus(ttl.toJavaDuration())
            }
        }
        return "${config.appBaseUrl}/reset-password?token=$token"
    }

    fun reset(token: String, newPassword: String) {
        requireValid(
            buildMap {
                if (!checkPassword(newPassword).isValid) put("newPassword", "WEAK_PASSWORD")
                else if (PasswordHasher.isTooLong(newPassword)) put("newPassword", "TOO_LONG")
            },
        )
        val hash = hasher.hash(newPassword)
        transaction {
            val now = Instant.now()
            val row = PasswordResetTokens.selectAll()
                .where { PasswordResetTokens.tokenHash eq TokenService.sha256(token) }
                .forUpdate()
                .singleOrNull()
            if (row == null || row[PasswordResetTokens.usedAt] != null || row[PasswordResetTokens.expiresAt] <= now) {
                throw ApiException(HttpStatusCode.BadRequest, ErrorCode.RESET_TOKEN_INVALID, "This reset link is no longer valid")
            }
            val userId: UUID = row[PasswordResetTokens.userId]
            Users.update({ Users.id eq userId }) { it[passwordHash] = hash }
            // Single use, and any other link requested for this account dies with it.
            PasswordResetTokens.update({ (PasswordResetTokens.userId eq userId) and PasswordResetTokens.usedAt.isNull() }) {
                it[usedAt] = now
            }
            // Every device is signed out.
            RefreshTokens.update({ (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull() }) {
                it[revokedAt] = now
            }
        }
    }
}
