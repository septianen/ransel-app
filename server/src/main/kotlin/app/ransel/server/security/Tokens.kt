package app.ransel.server.security

import app.ransel.server.AppConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.time.toJavaDuration

class TokenService(private val config: AppConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    private val random = SecureRandom()

    val verifier = JWT.require(algorithm).withIssuer(config.jwtIssuer).build()

    fun issueAccessToken(userId: UUID): IssuedAccessToken {
        val now = Instant.now()
        val expiresAt = now.plus(config.accessTokenTtl.toJavaDuration())
        val token = JWT.create()
            .withIssuer(config.jwtIssuer)
            .withSubject(userId.toString())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)
        return IssuedAccessToken(token, expiresAt)
    }

    /** 256 random bits, URL-safe. Only [sha256] of it is stored. */
    fun newOpaqueToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

data class IssuedAccessToken(val token: String, val expiresAt: Instant)
