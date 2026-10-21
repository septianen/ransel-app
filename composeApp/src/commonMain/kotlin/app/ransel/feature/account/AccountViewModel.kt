package app.ransel.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.AuthRepository
import app.ransel.data.credit.CreditsStore
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.notification.PushRegistration
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.repository.AppLanguage
import app.ransel.data.repository.AppPreferences
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.sync.SyncStatus
import app.ransel.ui.theme.ThemeMode
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
) : ViewModel() {
    val state: StateFlow<AppPreferences> = preferences.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences(loaded = false))

    val sessionState: StateFlow<SessionState> = session.state

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }
    fun setCurrency(code: String) = viewModelScope.launch { preferences.setDefaultCurrency(code) }
    fun setLanguage(language: AppLanguage) = viewModelScope.launch { preferences.setLanguage(language) }
    fun setConfirmReorder(confirm: Boolean) = viewModelScope.launch { preferences.setSkipReorderConfirmation(!confirm) }

    fun signOut() = viewModelScope.launch {
        runCatching { push.unregister() } // before the token is dropped, so this phone stops getting this user's push
        credits.update(null)
        notifications.clear()
        auth.signOut()
    }
}
