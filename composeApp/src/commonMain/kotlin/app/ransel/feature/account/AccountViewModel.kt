package app.ransel.feature.account

import app.ransel.data.analytics.CrashReporter
import app.ransel.data.analytics.NoCrashReporter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.AuthRepository
import app.ransel.data.credit.CreditsStore
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.voting.VotingStore
import app.ransel.data.notification.PushRegistration
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.repository.AppLanguage
import app.ransel.data.repository.AppPreferences
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.sync.SyncStatus
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.MeDto
import app.ransel.ui.components.PhotoProblem
import app.ransel.ui.components.photoProblem
import app.ransel.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val preferences: PreferencesRepository,
    private val session: SessionManager,
    private val auth: AuthRepository,
    val sync: SyncStatus,
    private val credits: CreditsStore,
    private val push: PushRegistration,
    private val notifications: NotificationsStore,
    private val voting: VotingStore,
    private val api: RanselApi,
    private val crashes: CrashReporter = NoCrashReporter,
) : ViewModel() {
    val state: StateFlow<AppPreferences> = preferences.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences(loaded = false))

    val sessionState: StateFlow<SessionState> = session.state

    /** Profile photo (B19.1): an upload or removal under way, and why the last one failed. */
    private val _photo = MutableStateFlow(PhotoState())
    val photo: StateFlow<PhotoState> = _photo.asStateFlow()

    fun changePhoto(jpeg: ByteArray) = photoCall { api.setAvatar(jpeg) }
    fun removePhoto() = photoCall { api.clearAvatar() }
    fun photoUnreadable() = _photo.update { PhotoState(problem = PhotoProblem.UNREADABLE) }

    private fun photoCall(block: suspend () -> ApiResult<MeDto>) {
        if (_photo.value.busy) return
        _photo.value = PhotoState(busy = true)
        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Success -> {
                    session.updateUser(result.value)
                    _photo.value = PhotoState()
                }
                is ApiResult.Failure -> _photo.value = PhotoState(problem = result.error.photoProblem())
            }
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }
    fun setCurrency(code: String) = viewModelScope.launch { preferences.setDefaultCurrency(code) }
    fun setLanguage(language: AppLanguage) = viewModelScope.launch { preferences.setLanguage(language) }
    fun setConfirmReorder(confirm: Boolean) = viewModelScope.launch { preferences.setSkipReorderConfirmation(!confirm) }

    fun signOut() = viewModelScope.launch {
        runCatching { push.unregister() }.onFailure(crashes::record) // before the token is dropped, so this phone stops getting this user's push
        credits.update(null)
        notifications.clear()
        voting.clear()
        auth.signOut()
    }
}

data class PhotoState(val busy: Boolean = false, val problem: PhotoProblem? = null)
