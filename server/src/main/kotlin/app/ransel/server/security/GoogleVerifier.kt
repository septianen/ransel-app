package app.ransel.server.security

import app.ransel.server.AppConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/** What Google vouches for after a token was verified. [emailVerified] comes from Google's `email_verified` claim. */
data class GoogleIdentity(
    val subject: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
)

/** Verifies Google sign-in proofs. null = invalid, expired, issued for another app, or Google cannot be asked. */
interface GoogleVerifier {
    suspend fun verifyIdToken(idToken: String): GoogleIdentity?

    /** Web: swaps the one-time auth code of the popup flow for an ID token and verifies it. */
    suspend fun verifyAuthCode(code: String): GoogleIdentity?
}

/** Google sign-in is off (no client id): every proof is refused. */
object NoGoogleVerifier : GoogleVerifier {
    override suspend fun verifyIdToken(idToken: String): GoogleIdentity? = null
    override suspend fun verifyAuthCode(code: String): GoogleIdentity? = null
}

/**
 * Checks the RS256 signature against Google's published keys (cached for an hour), `iss`, `exp` and `aud` (only our own
 * client ids). The ID token alone is the proof: nothing it says is trusted before the signature and audience pass.
 */
class GoogleTokenVerifier(
    private val config: AppConfig,
    private val http: HttpClient,
    private val now: () -> Long = System::currentTimeMillis,
) : GoogleVerifier {
    private val log = LoggerFactory.getLogger(GoogleTokenVerifier::class.java)
    private val audiences = setOf(config.googleWebClientId, config.googleIosClientId).filter { it.isNotBlank() }
    @Volatile private var keys: Map<String, RSAPublicKey> = emptyMap()
    @Volatile private var keysFetchedAt = 0L

    override suspend fun verifyIdToken(idToken: String): GoogleIdentity? = try {
        verify(idToken)
    } catch (e: Exception) {
        log.info("Google ID token rejected: {}", e.javaClass.simpleName)
        null
    }

    override suspend fun verifyAuthCode(code: String): GoogleIdentity? {
        if (config.googleWebClientId.isBlank() || config.googleWebClientSecret.isBlank()) return null
        return try {
            val response = http.submitForm(
                "https://oauth2.googleapis.com/token",
                parameters {
                    append("code", code)
                    append("client_id", config.googleWebClientId)
                    append("client_secret", config.googleWebClientSecret)
                    append("redirect_uri", "postmessage")
                    append("grant_type", "authorization_code")
                },
            )
            if (!response.status.isSuccess()) return null
            val idToken = Json.parseToJsonElement(response.bodyAsText()).jsonObject["id_token"]?.jsonPrimitive?.content ?: return null
            verify(idToken)
        } catch (e: Exception) {
            log.info("Google code exchange failed: {}", e.javaClass.simpleName)
            null
        }
    }

    private suspend fun verify(idToken: String): GoogleIdentity? {
        if (audiences.isEmpty()) return null
        val unverified = JWT.decode(idToken)
        val key = publicKey(unverified.keyId ?: return null) ?: return null
        val verified = JWT.require(Algorithm.RSA256(key, null))
            .withIssuer("https://accounts.google.com", "accounts.google.com")
            .withAudience(*audiences.toTypedArray()) // any one of our ids
            .build()
            .verify(unverified)
        val email = verified.getClaim("email").asString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return GoogleIdentity(
            subject = verified.subject ?: return null,
            email = email,
            emailVerified = verified.getClaim("email_verified").let { claim ->
                claim.asBoolean() ?: (claim.asString() == "true") // old tokens carry the string "true"
            },
            name = verified.getClaim("name").asString()?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private suspend fun publicKey(keyId: String): RSAPublicKey? {
        val stale = now() - keysFetchedAt > KEY_TTL_MILLIS
        if (stale || keyId !in keys) refreshKeys()
        return keys[keyId]
    }

    private suspend fun refreshKeys() {
        val response = http.get("https://www.googleapis.com/oauth2/v3/certs")
        if (!response.status.isSuccess()) return
        val decoder = Base64.getUrlDecoder()
        val parsed = Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("keys").jsonArray.mapNotNull { element ->
            val jwk = element.jsonObject
            if (jwk["kty"]?.jsonPrimitive?.content != "RSA") return@mapNotNull null
            val id = jwk["kid"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val spec = RSAPublicKeySpec(
                BigInteger(1, decoder.decode(jwk.getValue("n").jsonPrimitive.content)),
                BigInteger(1, decoder.decode(jwk.getValue("e").jsonPrimitive.content)),
            )
            id to (KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey)
        }.toMap()
        if (parsed.isNotEmpty()) {
            keys = parsed
            keysFetchedAt = now()
        }
    }

    private companion object {
        const val KEY_TTL_MILLIS = 60 * 60 * 1000L
    }
}
