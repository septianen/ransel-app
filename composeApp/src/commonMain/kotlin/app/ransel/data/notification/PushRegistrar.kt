package app.ransel.data.notification

import app.ransel.shared.model.DevicePlatform

/**
 * What the platform knows about push. Android implements it with Firebase Cloud Messaging; iOS and web have none yet
 * ([NoPushRegistrar]), so [available] is false and nothing is registered with the server.
 */
interface PushRegistrar {
    val platform: DevicePlatform?
    val appVersion: String?

    /** False when this build has no push (no google-services.json, iOS, web). */
    val available: Boolean

    /** The device's push token; null when push is not available or the token could not be fetched. */
    suspend fun token(): String?

    fun hasPermission(): Boolean

    /** Asks the user once for the permission to show notifications (Android 13+); a no-op elsewhere. */
    fun requestPermission()

    /** "id" or "en": the device language, for the push text. */
    fun deviceLanguage(): String
}

object NoPushRegistrar : PushRegistrar {
    override val platform: DevicePlatform? = null
    override val appVersion: String? = null
    override val available = false
    override suspend fun token(): String? = null
    override fun hasPermission() = false
    override fun requestPermission() = Unit
    override fun deviceLanguage() = "id"
}
