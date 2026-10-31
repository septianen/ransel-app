package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.RefreshTokens
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.requireValid
import app.ransel.server.security.PasswordHasher
import app.ransel.server.security.TokenService
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.checkPassword
import app.ransel.shared.domain.isValidEmail
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.LoginRequest
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.RegisterRequest
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lowerCase
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
        if (!ok || row == null) throw invalidCredentials()
        return transaction { startSession(row[Users.id], body.deviceName) }
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
        val refresh = tokens.newOpaqueToken()
        RefreshTokens.insert {
            it[tokenHash] = TokenService.sha256(refresh)
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.deviceName] = deviceName?.take(120)
            it[expiresAt] = Instant.now().plus(config.refreshTokenTtl.toJavaDuration())
        }
        val access = tokens.issueAccessToken(userId)
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

    private fun invalidCredentials() =
        ApiException(HttpStatusCode.Unauthorized, ErrorCode.INVALID_CREDENTIALS, "Wrong email or password")

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
