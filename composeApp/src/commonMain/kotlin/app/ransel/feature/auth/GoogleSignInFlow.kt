package app.ransel.feature.auth

import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.auth.AuthRepository
import app.ransel.data.auth.GoogleResult
import app.ransel.data.auth.GoogleSignInLauncher
import app.ransel.data.remote.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [available] stays false until the server's config has a Google client id, so the button never shows for a server
 * (or a moment) where it cannot work.
 */
data class GoogleUiState(
    val available: Boolean = false,
    val loading: Boolean = false,
    val problem: AuthProblem? = null,
)

/**
 * "Lanjutkan dengan Google" for one screen's ViewModel. On success the session flips to signed-in and the app swaps the
 * screen; a cancelled Google sheet changes nothing.
 */
class GoogleSignInFlow(
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
    private val analytics: Analytics,
) {
    private val _state = MutableStateFlow(GoogleUiState())
    val state: StateFlow<GoogleUiState> = _state.asStateFlow()

    init {
        scope.launch { _state.update { it.copy(available = auth.googleConfig() != null) } }
    }

    fun start(launcher: GoogleSignInLauncher) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, problem = null) }
        scope.launch {
            val config = auth.googleConfig()
            if (config == null) {
                _state.update { it.copy(loading = false, available = false, problem = AuthProblem.GOOGLE_FAILED) }
                return@launch
            }
            when (val google = launcher.request(config)) {
                GoogleResult.Cancelled -> _state.update { it.copy(loading = false) }
                GoogleResult.Failed -> _state.update { it.copy(loading = false, problem = AuthProblem.GOOGLE_FAILED) }
                is GoogleResult.Success -> when (val result = auth.signInWithGoogle(google.proof)) {
                    is ApiResult.Success -> {
                        analytics.event(if (result.value) AnalyticsEvent.SignUpGoogle else AnalyticsEvent.LoginGoogle)
                        _state.update { it.copy(loading = false) }
                    }
                    is ApiResult.Failure -> _state.update { it.copy(loading = false, problem = googleProblemOf(result.error.code)) }
                }
            }
        }
    }
}
