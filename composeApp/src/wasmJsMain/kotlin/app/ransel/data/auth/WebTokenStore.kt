package app.ransel.data.auth

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@JsFun("(key) => { const v = window.localStorage.getItem(key); return v === null ? '' : v; }")
private external fun storageGet(key: String): String

@JsFun("(key, value) => window.localStorage.setItem(key, value)")
private external fun storageSet(key: String, value: String)

@JsFun("(key) => window.localStorage.removeItem(key)")
private external fun storageRemove(key: String)

/**
 * Web: the refresh token and profile live in `localStorage` (accepted risk, TRD "Auth and security");
 * the access token is kept in memory only and is renewed after a reload.
 */
class WebTokenStore : TokenStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(): StoredSession? {
        val raw = storageGet(KEY)
        if (raw.isEmpty()) return null
        return runCatching { json.decodeFromString<StoredSession>(raw) }.getOrNull()
    }

    override suspend fun write(session: StoredSession) {
        storageSet(KEY, json.encodeToString(session.copy(accessToken = "")))
    }

    override suspend fun clear() = storageRemove(KEY)

    private companion object {
        const val KEY = "ransel.session"
    }
}
