package app.ransel.data.auth

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the signed-in state: restores it at startup, renews the access token (rotating refresh
 * token, one refresh at a time), and ends the session on sign-out or when the refresh token dies.
 *
 * [api] is a provider because [RanselApi] itself needs this object (circular by nature).
 */
class SessionManager(
    private val store: TokenStore,
    private val api: () -> RanselApi,
) : ApiAuth {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state

    private var current: StoredSession? = null
    private val refreshLock = Mutex()

    val user: MeDto? get() = current?.user

    /** Reads the stored session. Call once at startup; the state leaves [SessionState.Loading]. */
    suspend fun restore() {
        val stored = runCatching { store.read() }.getOrNull()
        current = stored
        _state.value = if (stored == null) SessionState.SignedOut else SessionState.SignedIn(stored.user)
    }

    override fun accessToken(): String? = current?.accessToken?.takeIf { it.isNotEmpty() }

    /** Called after register / login. */
    suspend fun start(session: AuthSessionDto) {
        val stored = StoredSession(session.accessToken, session.refreshToken, session.user)
        current = stored
        store.write(stored)
        _state.value = SessionState.SignedIn(session.user)
    }

    suspend fun updateUser(user: MeDto) {
        val stored = current?.copy(user = user) ?: return
        current = stored
        store.write(stored)
        _state.value = SessionState.SignedIn(user)
    }

    override suspend fun refreshAccessToken(): Boolean = refreshLock.withLock {
        val stored = current ?: return false
        when (val result = api().refresh(stored.refreshToken)) {
            is ApiResult.Success -> {
                val next = StoredSession(result.value.accessToken, result.value.refreshToken, result.value.user)
                current = next
                store.write(next)
                true
            }
            is ApiResult.Failure -> {
                // Only a rejected refresh token ends the session; being offline just fails this request.
                if (!result.error.isConnection) {
                    val code = result.error.code
                    if (code == ErrorCode.TOKEN_EXPIRED || code == ErrorCode.UNAUTHORIZED || result.error.status == 401) {
                        markExpired()
                    }
                }
                false
            }
        }
    }

    private fun markExpired() {
        val user = current?.user ?: return
        _state.value = SessionState.Expired(user)
    }

    /** Signs out this device: tells the server (best effort), forgets the tokens. */
    suspend fun signOut() {
        val stored = current
        current = null
        _state.value = SessionState.SignedOut
        runCatching { store.clear() }
        if (stored != null) runCatching { api().logout(stored.refreshToken) }
    }

    /** "Nanti saja, pakai offline": keep working locally without a session. */
    fun continueOffline() {
        if (_state.value is SessionState.Expired) _state.value = SessionState.SignedOut
    }
}
