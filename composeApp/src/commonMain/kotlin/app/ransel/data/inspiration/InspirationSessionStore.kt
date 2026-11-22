package app.ransel.data.inspiration

import app.ransel.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists only the active server session id; all session data stays on the server. */
class InspirationSessionStore(private val preferences: PreferencesRepository) {
    private val mutableId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = mutableId.asStateFlow()
    suspend fun restore(): String? = preferences.getString(KEY).also { mutableId.value = it }
    suspend fun set(id: String?) { mutableId.value = id; preferences.putString(KEY, id) }
    private companion object { const val KEY = "inspiration_active_session" }
}
