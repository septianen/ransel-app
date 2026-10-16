package app.ransel.server.ai

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Signs place-photo links so the proxy can be called without a bearer token but not abused for arbitrary places.
 * The signature is HMAC-SHA256 over `placeId|exp` (exp in epoch seconds), base64url without padding.
 */
class PhotoSigner(secret: String, private val validFor: Duration = 7.days, private val clock: Clock = Clock.System) {
    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

    private fun mac(placeId: String, exp: Long): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal("$placeId|$exp".toByteArray())

    private fun encode(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** Path relative to `ApiRoutes.BASE`, as stored in `RecommendationItem.photoUrl`. */
    fun signedPath(placeId: String): String {
        val exp = (clock.now() + validFor).epochSeconds
        return "${app.ransel.shared.api.ApiRoutes.placePhoto(placeId)}?exp=$exp&sig=${encode(mac(placeId, exp))}"
    }

    /** Constant-time comparison; false for a malformed signature or an expired link. */
    fun verify(placeId: String, exp: Long?, sig: String?): Boolean {
        if (exp == null || sig.isNullOrEmpty() || exp < clock.now().epochSeconds) return false
        val given = runCatching { java.util.Base64.getUrlDecoder().decode(sig) }.getOrNull() ?: return false
        return MessageDigest.isEqual(given, mac(placeId, exp))
    }
}
