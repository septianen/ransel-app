package app.ransel.admin.data

import app.ransel.shared.dto.AdminMeDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AdminState {
    /** Restoring the stored session / checking `GET /admin/me`. */
    data object Loading : AdminState

    data object SignedOut : AdminState

    /** Signed in with a RANSEL account whose email is not in `ADMIN_EMAILS` (403): "Bukan akun admin". */
    data class NotAdmin(val email: String) : AdminState

    data class Ready(val me: AdminMeDto) : AdminState

    /** `GET /admin/me` failed for another reason (offline, server error): retry. */
    data class Unavailable(val error: ApiFailure) : AdminState
}

/** Where the refresh token lives between reloads (sessionStorage in the browser, a map in tests). */
interface RefreshTokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

/**
 * Sign-in for Karung: a normal RANSEL account (`/auth/login`), then `GET /admin/me` decides whether it is an admin.
 * The access token is kept in memory only; the refresh token in [store].
 */
class AdminSession(
    private val api: AdminApi,
    private val store: RefreshTokenStore,
) : TokenSource {
    private val _state = MutableStateFlow<AdminState>(AdminState.Loading)
    val state: StateFlow<AdminState> = _state

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var email: String = ""
    private val refreshLock = Mutex()

    init {
        api.tokens = this
    }

    override fun accessToken(): String? = accessToken

    /** At startup: a refresh token from this tab's earlier visit becomes a session again (the first call renews it). */
    suspend fun restore() {
        val stored = store.read()
        if (stored.isNullOrEmpty()) {
            _state.value = AdminState.SignedOut
            return
        }
        refreshToken = stored
        checkAdmin()
    }

    /** Signs in; on success the state moves on by itself. Returns the failure for the form (wrong password, ...). */
    suspend fun signIn(email: String, password: String): ApiFailure? =
        when (val result = api.login(email, password)) {
            is ApiResult.Failure -> result.error
            is ApiResult.Success -> {
                accessToken = result.value.accessToken
                refreshToken = result.value.refreshToken
                this.email = result.value.user.email
                store.write(result.value.refreshToken)
                checkAdmin()
                null
            }
        }

    /** `GET /admin/me`: admin → Ready, 403 → NotAdmin, 401 (refresh failed) → SignedOut. */
    suspend fun checkAdmin() {
        _state.value = AdminState.Loading
        _state.value = when (val result = api.me()) {
            is ApiResult.Success -> AdminState.Ready(result.value)
            is ApiResult.Failure -> when {
                result.error.status == 403 -> AdminState.NotAdmin(email)
                result.error.status == 401 -> {
                    forget()
                    AdminState.SignedOut
                }
                else -> AdminState.Unavailable(result.error)
            }
        }
    }

    /** A screen got 401 after the renewal failed, or 403 because the email left `ADMIN_EMAILS`: re-check who we are. */
    suspend fun onAccessLost(error: ApiFailure) {
        if (error.status == 401 || error.status == 403) checkAdmin()
    }

    override suspend fun refresh(): Boolean = refreshLock.withLock {
        val token = refreshToken ?: return false
        when (val result = api.refresh(token)) {
            is ApiResult.Success -> {
                accessToken = result.value.accessToken
                refreshToken = result.value.refreshToken
                email = result.value.user.email
                store.write(result.value.refreshToken)
                true
            }
            is ApiResult.Failure -> {
                // A rejected refresh token (expired, revoked, account disabled) ends the session; offline does not.
                if (!result.error.isConnection) {
                    forget()
                    _state.value = AdminState.SignedOut
                }
                false
            }
        }
    }

    suspend fun signOut() {
        val token = refreshToken
        forget()
        _state.value = AdminState.SignedOut
        if (token != null) api.logout(token)
    }

    private fun forget() {
        accessToken = null
        refreshToken = null
        store.clear()
    }
}
