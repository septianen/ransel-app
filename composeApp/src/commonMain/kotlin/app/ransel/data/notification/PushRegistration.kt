package app.ransel.data.notification

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.AppLanguage
import app.ransel.data.repository.PreferencesRepository
import app.ransel.shared.dto.RegisterDeviceRequest
import kotlinx.coroutines.flow.first

/**
 * Tells the server which phone belongs to the signed-in user, so it can send push. Called after sign-in, at app
 * start while signed in, and when Firebase hands out a new token (the permission is asked separately, from the explanation sheet); [unregister] runs before sign-out so the next
 * person on this phone does not get the previous user's notifications.
 */
class PushRegistration(
    private val registrar: PushRegistrar,
    private val api: RanselApi,
    private val preferences: PreferencesRepository,
) {
    private var lastRegistered: String? = null

    suspend fun register() {
        val platform = registrar.platform ?: return
        if (!registrar.available) return
        val token = registrar.token() ?: return
        send(platform, token)
    }

    /** Firebase rotated the token. */
    suspend fun onNewToken(token: String) {
        val platform = registrar.platform ?: return
        send(platform, token)
    }

    suspend fun unregister() {
        if (!registrar.available) return
        val token = lastRegistered ?: registrar.token() ?: return
        if (api.unregisterDevice(token) is ApiResult.Success) lastRegistered = null
    }

    private suspend fun send(platform: app.ransel.shared.model.DevicePlatform, token: String) {
        val language = when (preferences.observe().first().language) {
            AppLanguage.ENGLISH -> "en"
            AppLanguage.INDONESIAN -> "id"
            AppLanguage.SYSTEM -> registrar.deviceLanguage()
        }
        val result = api.registerDevice(RegisterDeviceRequest(platform, token, language, registrar.appVersion))
        if (result is ApiResult.Success) lastRegistered = token
    }
}
