package app.ransel.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.AppLanguage
import app.ransel.data.repository.AppPreferences
import app.ransel.data.repository.PreferencesRepository
import app.ransel.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(private val preferences: PreferencesRepository) : ViewModel() {
    val state: StateFlow<AppPreferences> = preferences.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences(loaded = false))

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }
    fun setCurrency(code: String) = viewModelScope.launch { preferences.setDefaultCurrency(code) }
    fun setLanguage(language: AppLanguage) = viewModelScope.launch { preferences.setLanguage(language) }
    fun setConfirmReorder(confirm: Boolean) = viewModelScope.launch { preferences.setSkipReorderConfirmation(!confirm) }
}
