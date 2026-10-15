package app.ransel.data.auth

import app.ransel.shared.dto.MeDto
import kotlinx.serialization.Serializable

/** What is kept on the device between launches (tokens go to secure storage, see [TokenStore]). */
@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val user: MeDto,
)

/**
 * Secure storage for the session (TRD "Auth and security" → Client storage):
 *  - Android: AES-GCM key in the Android Keystore;
 *  - iOS: Keychain;
 *  - web: refresh token in `localStorage` (accepted risk), access token only in memory.
 */
interface TokenStore {
    suspend fun read(): StoredSession?
    suspend fun write(session: StoredSession)
    suspend fun clear()
}

sealed interface SessionState {
    /** The stored session has not been read yet (first frames). */
    data object Loading : SessionState

    /** Guest on mobile ("Pakai tanpa akun dulu"), or not signed in on the web. */
    data object SignedOut : SessionState

    data class SignedIn(val user: MeDto) : SessionState

    /** The refresh token no longer works: sign in again. Local data and pending changes stay. */
    data class Expired(val user: MeDto) : SessionState
}

/** What [app.ransel.data.remote.RanselApi] needs from the session: a token, and a way to renew it. */
interface ApiAuth {
    /** The current access token, or null when signed out. */
    fun accessToken(): String?

    /** Exchanges the refresh token for new tokens. False when it failed (session expired or offline). */
    suspend fun refreshAccessToken(): Boolean
}
