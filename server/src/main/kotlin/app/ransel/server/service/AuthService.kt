package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.RefreshTokens
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.requireValid
import app.ransel.server.security.GoogleIdentity
import app.ransel.server.security.GoogleVerifier
import app.ransel.server.plugins.validationFailed
import app.ransel.server.security.PasswordHasher
import app.ransel.server.security.TokenService
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.checkPassword
import app.ransel.shared.domain.isValidEmail
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.GoogleSignInConfigDto
import app.ransel.shared.dto.GoogleSignInRequest
import app.ransel.shared.dto.ChangePasswordRequest
import app.ransel.shared.dto.LoginRequest
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.RegisterRequest
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import kotlin.time.toJavaDuration

class AuthService(
    private val config: AppConfig,
    private val hasher: PasswordHasher,
    private val tokens: TokenService,
    private val verification: EmailVerificationService,
    private val credits: CreditService,
    private val google: GoogleVerifier,
) {
    fun register(body: RegisterRequest): AuthSessionDto {
        if (!config.registrationEnabled) {
            throw ApiException(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, "Registration is closed")
        }
        val email = body.email.trim()
        requireValid(
            buildMap {
                if (!isValidEmail(email)) put("email", "INVALID_EMAIL")
                if (body.displayName.isBlank()) put("displayName", "REQUIRED")
                val rule = checkPassword(body.password)
                if (!rule.isValid) put("password", "WEAK_PASSWORD")
                else if (PasswordHasher.isTooLong(body.password)) put("password", "TOO_LONG")
            },
        )
        val hash = hasher.hash(body.password)
        val userId = UUID.randomUUID()
        try {
            transaction {
                if (findByEmail(email) != null) throw emailTaken()
                Users.insert {
                    it[id] = userId
                    it[displayName] = body.displayName.trim()
                    it[Users.email] = email
                    it[passwordHash] = hash
                }
            }
        } catch (e: SQLException) {
            // Two sign-ups racing on the same email hit the unique index instead of the check above.
            if (e.sqlState == UNIQUE_VIOLATION || (e.cause as? SQLException)?.sqlState == UNIQUE_VIOLATION) throw emailTaken()
            throw e
        }
        val session = transaction { startSession(userId, body.deviceName) }
        verification.send(userId)
        return session
    }

    /** Creates the configured seed account when no account has its email. Idempotent; never touches an existing one. */
    fun seed(user: AppConfig.SeedUser) {
        requireValid(
            buildMap {
                if (!isValidEmail(user.email)) put("SEED_USER_EMAIL", "INVALID_EMAIL")
                val rule = checkPassword(user.password)
                if (!rule.isValid || PasswordHasher.isTooLong(user.password)) put("SEED_USER_PASSWORD", "WEAK_PASSWORD")
            },
        )
        val hash = hasher.hash(user.password)
        transaction {
            if (findByEmail(user.email) == null) {
                Users.insert {
                    it[id] = UUID.randomUUID()
                    it[displayName] = user.displayName.trim()
                    it[email] = user.email
                    it[passwordHash] = hash
                    // Seed accounts are made by the operator, so their address needs no proof.
                    it[emailVerifiedAt] = Instant.now()
                }
            }
        }
    }

    fun login(body: LoginRequest): AuthSessionDto {
        val row = transaction { findByEmail(body.email.trim()) }
        val stored = row?.get(Users.passwordHash)
        // Always spend one bcrypt verification so response time does not reveal whether the email exists.
        val ok = hasher.verify(body.password, stored ?: hasher.decoy) && stored != null
        // A Google-only account has no password: say so, so the person uses the Google button (owner decision 5 Nov 2026).
        if (stored == null && row != null && row[Users.googleSub] != null) {
            throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.GOOGLE_ACCOUNT_ONLY, "This account signs in with Google")
        }
        if (!ok || row == null) throw invalidCredentials()
        return transaction { startSession(row[Users.id], body.deviceName) }
    }

    /**
     * "Lanjutkan dengan Google" (owner decisions 5 Nov 2026). The proof is verified first; an email Google does not
     * vouch for is refused. Then: a known Google id signs in; a registered email is linked (when it was never verified,
     * Google wins: the password and every other session go, so nobody who pre-registered the address keeps access);
     * an unknown email creates a verified account with the Google name. Signup credits are granted once, as after a
     * verification link.
     */
    suspend fun signInWithGoogle(body: GoogleSignInRequest): AuthSessionDto {
        if (config.googleWebClientId.isBlank()) {
            throw ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.INTERNAL_ERROR, "Google sign-in is not set up")
        }
        val identity = when {
            body.idToken != null -> google.verifyIdToken(body.idToken!!)
            body.authCode != null -> google.verifyAuthCode(body.authCode!!)
            else -> null
        }
        if (identity == null || !identity.emailVerified) {
            throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.GOOGLE_TOKEN_INVALID, "Google sign-in could not be verified")
        }
        val outcome = try {
            transaction { upsertGoogleUser(identity, body.deviceName) }
        } catch (e: SQLException) {
            // Two first sign-ins racing hit a unique index (email or google_sub): the second finds the first's account.
            if (e.sqlState == UNIQUE_VIOLATION || (e.cause as? SQLException)?.sqlState == UNIQUE_VIOLATION) {
                transaction { upsertGoogleUser(identity, body.deviceName) }
            } else {
                throw e
            }
        }
        // Idempotent: 0 when the grant already exists. Also covers a linked account that was never verified.
        if (outcome.grantCredits) credits.grantSignup(UUID.fromString(outcome.session.user.id))
        return outcome.session
    }

    fun googleConfig() = GoogleSignInConfigDto(
        webClientId = config.googleWebClientId.takeIf { it.isNotBlank() },
        iosClientId = config.googleIosClientId.takeIf { it.isNotBlank() },
    )

    private class GoogleOutcome(val session: AuthSessionDto, val grantCredits: Boolean)

    private fun upsertGoogleUser(identity: GoogleIdentity, deviceName: String?): GoogleOutcome {
        val bySub = Users.selectAll().where { (Users.googleSub eq identity.subject) and Users.deletedAt.isNull() }.singleOrNull()
        if (bySub != null) return GoogleOutcome(startSession(bySub[Users.id], deviceName), grantCredits = false)

        val byEmail = findByEmail(identity.email)
        if (byEmail != null) {
            val userId = byEmail[Users.id]
            val wasVerified = byEmail[Users.emailVerifiedAt] != null
            if (byEmail[Users.disabledAt] != null) throw accountDisabled()
            if (wasVerified) {
                Users.update({ Users.id eq userId }) { it[googleSub] = identity.subject }
            } else {
                val now = Instant.now()
                Users.update({ Users.id eq userId }) {
                    it[googleSub] = identity.subject
                    it[emailVerifiedAt] = now
                    it[passwordHash] = null
                }
                RefreshTokens.update({ (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull() }) { it[revokedAt] = now }
            }
            return GoogleOutcome(startSession(userId, deviceName), grantCredits = !wasVerified)
        }

        if (!config.registrationEnabled) {
            throw ApiException(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, "Registration is closed")
        }
        val userId = UUID.randomUUID()
        Users.insert {
            it[id] = userId
            it[displayName] = identity.name ?: identity.email.substringBefore('@')
            it[email] = identity.email
            it[googleSub] = identity.subject
            it[emailVerifiedAt] = Instant.now()
        }
        return GoogleOutcome(startSession(userId, deviceName).copy(newAccount = true), grantCredits = true)
    }

    /** Rotates: the presented token is revoked and a new pair is issued, in one transaction. */
    fun refresh(refreshToken: String): AuthSessionDto = transaction {
        val row = RefreshTokens.selectAll()
            .where { RefreshTokens.tokenHash eq TokenService.sha256(refreshToken) }
            .forUpdate()
            .singleOrNull()
        val now = Instant.now()
        if (row == null || row[RefreshTokens.revokedAt] != null || row[RefreshTokens.expiresAt] <= now) throw tokenExpired()
        RefreshTokens.update({ RefreshTokens.id eq row[RefreshTokens.id] }) { it[revokedAt] = now }
        startSession(row[RefreshTokens.userId], row[RefreshTokens.deviceName])
    }

    /**
     * Signed-in password change. Checks the new password with the same rule as register and reset, then the current
     * password, and only then writes the hash. Other refresh tokens are revoked; [sessionId] (the access token's `sid`)
     * is kept. [issuedAt] is only a fallback for access tokens minted before `sid` existed.
     */
    fun changePassword(userId: UUID, sessionId: UUID?, issuedAt: Instant?, body: ChangePasswordRequest) {
        requireValid(
            buildMap {
                if (!checkPassword(body.newPassword).isValid) put("newPassword", "WEAK_PASSWORD")
                else if (PasswordHasher.isTooLong(body.newPassword)) put("newPassword", "TOO_LONG")
            },
        )
        transaction {
            val user = Users.selectAll()
                .where { (Users.id eq userId) and Users.deletedAt.isNull() }
                .forUpdate()
                .singleOrNull()
            val stored = user?.get(Users.passwordHash)
            if (user == null || stored == null || !hasher.verify(body.currentPassword, stored)) throw wrongCurrentPassword()
            if (body.newPassword == body.currentPassword) throw validationFailed(mapOf("newPassword" to "SAME_AS_CURRENT"))
            val now = Instant.now()
            Users.update({ Users.id eq userId }) { it[passwordHash] = hasher.hash(body.newPassword) }
            val keep = sessionToKeep(userId, sessionId, issuedAt)
            RefreshTokens.update({
                val others = (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull()
                if (keep == null) others else others and (RefreshTokens.id neq keep)
            }) { it[revokedAt] = now }
        }
    }

    /** Idempotent: an unknown or already revoked token is not an error. */
    fun logout(refreshToken: String) {
        transaction {
            RefreshTokens.update(
                { (RefreshTokens.tokenHash eq TokenService.sha256(refreshToken)) and RefreshTokens.revokedAt.isNull() },
            ) { it[revokedAt] = Instant.now() }
        }
    }

    /**
     * Must run inside a transaction. Login reaches it only after the password matched, so ACCOUNT_DISABLED never tells a
     * stranger that the email exists; a refresh of a disabled account fails too and its rollback keeps the old token.
     */
    private fun startSession(userId: UUID, deviceName: String?): AuthSessionDto {
        val user = Users.selectAll().where { (Users.id eq userId) and Users.deletedAt.isNull() }.singleOrNull()
            ?: throw tokenExpired()
        if (user[Users.disabledAt] != null) throw accountDisabled()
        val sessionId = UUID.randomUUID()
        val refresh = tokens.newOpaqueToken()
        RefreshTokens.insert {
            it[id] = sessionId
            it[tokenHash] = TokenService.sha256(refresh)
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.deviceName] = deviceName?.take(120)
            it[expiresAt] = Instant.now().plus(config.refreshTokenTtl.toJavaDuration())
        }
        val access = tokens.issueAccessToken(userId, sessionId)
        return AuthSessionDto(
            accessToken = access.token,
            accessTokenExpiresAt = access.expiresAt.toTimestamp(),
            refreshToken = refresh,
            user = user.toMeDto(),
        )
    }

    private fun findByEmail(email: String) =
        Users.selectAll().where { (Users.email.lowerCase() eq email.lowercase()) and Users.deletedAt.isNull() }.singleOrNull()

    private fun emailTaken() =
        ApiException(HttpStatusCode.Conflict, ErrorCode.EMAIL_ALREADY_REGISTERED, "Email is already registered")

    /** Active refresh row to leave in place: the token's session, or the one created with a pre-`sid` access token. */
    private fun sessionToKeep(userId: UUID, sessionId: UUID?, issuedAt: Instant?): UUID? {
        if (sessionId != null) {
            val row = RefreshTokens.selectAll().where {
                (RefreshTokens.id eq sessionId) and (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull()
            }.singleOrNull()
            if (row != null) return row[RefreshTokens.id]
        }
        if (issuedAt == null) return null
        val from = issuedAt.minusSeconds(2)
        val to = issuedAt.plusSeconds(2)
        val matches = RefreshTokens.selectAll().where {
            (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull() and
                (RefreshTokens.createdAt greaterEq from) and (RefreshTokens.createdAt lessEq to)
        }.toList()
        return matches.singleOrNull()?.get(RefreshTokens.id)
    }

    private fun invalidCredentials() =
        ApiException(HttpStatusCode.Unauthorized, ErrorCode.INVALID_CREDENTIALS, "Wrong email or password")

    private fun wrongCurrentPassword() =
        ApiException(
            HttpStatusCode.BadRequest,
            ErrorCode.CURRENT_PASSWORD_WRONG,
            "Current password is wrong",
            mapOf("currentPassword" to "WRONG"),
        )

    private fun accountDisabled() =
        ApiException(HttpStatusCode.Forbidden, ErrorCode.ACCOUNT_DISABLED, "This account has been deactivated")

    private fun tokenExpired() =
        ApiException(HttpStatusCode.Unauthorized, ErrorCode.TOKEN_EXPIRED, "Session expired, sign in again")

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}

fun org.jetbrains.exposed.v1.core.ResultRow.toMeDto() = MeDto(
    id = this[Users.id].toString(),
    email = this[Users.email].orEmpty(),
    displayName = this[Users.displayName],
    defaultCurrency = this[Users.defaultCurrency],
    emailVerified = this[Users.emailVerifiedAt] != null,
    avatarUrl = this[Users.avatarUrl],
)
