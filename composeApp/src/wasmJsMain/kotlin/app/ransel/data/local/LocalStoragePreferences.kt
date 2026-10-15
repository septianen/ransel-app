package app.ransel.data.local

import app.ransel.data.repository.AppLanguage
import app.ransel.data.repository.AppPreferences
import app.ransel.data.repository.PreferencesRepository
import app.ransel.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@JsFun("(key) => { const v = window.localStorage.getItem(key); return v === null ? '' : v; }")
private external fun storageGet(key: String): String

@JsFun("(key, value) => window.localStorage.setItem(key, value)")
private external fun storageSet(key: String, value: String)

/**
 * Per-device preferences in the browser's `localStorage` (the web app has no local database). Same
 * keys as the Room version. There is no onboarding on the web, so it is always "completed".
 */
internal class LocalStoragePreferencesRepository : PreferencesRepository {
    private val state = MutableStateFlow(read())

    override fun observe(): Flow<AppPreferences> = state as StateFlow<AppPreferences>

    override suspend fun setThemeMode(mode: ThemeMode) = write(THEME, mode.name) { it.copy(themeMode = mode) }
    override suspend fun setDefaultCurrency(currency: String) =
        write(CURRENCY, currency) { it.copy(defaultCurrency = currency) }
    override suspend fun setSkipReorderConfirmation(skip: Boolean) =
        write(SKIP_REORDER_CONFIRM, skip.toString()) { it.copy(skipReorderConfirmation = skip) }
    override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
    override suspend fun setLanguage(language: AppLanguage) =
        write(LANGUAGE, language.name) { it.copy(language = language) }

    private fun write(key: String, value: String, change: (AppPreferences) -> AppPreferences) {
        storageSet(key, value)
        state.update(change)
    }

    private fun read(): AppPreferences = AppPreferences(
        themeMode = ThemeMode.entries.firstOrNull { it.name == storageGet(THEME) } ?: ThemeMode.SYSTEM,
        defaultCurrency = storageGet(CURRENCY).ifEmpty { "IDR" },
        skipReorderConfirmation = storageGet(SKIP_REORDER_CONFIRM) == "true",
        onboardingCompleted = true,
        language = AppLanguage.entries.firstOrNull { it.name == storageGet(LANGUAGE) } ?: AppLanguage.SYSTEM,
    )

    private companion object {
        const val THEME = "ransel.theme_mode"
        const val CURRENCY = "ransel.default_currency"
        const val SKIP_REORDER_CONFIRM = "ransel.skip_reorder_confirmation"
        const val LANGUAGE = "ransel.language"
    }
}
