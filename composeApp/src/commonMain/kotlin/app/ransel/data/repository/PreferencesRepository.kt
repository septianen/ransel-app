package app.ransel.data.repository

import app.ransel.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

/** `null` language = follow the system. */
enum class AppLanguage(val tag: String?) { SYSTEM(null), INDONESIAN("id"), ENGLISH("en") }

/** Per-device preferences, never synced (composeApp/CLAUDE.md "Rules"). */
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Only the default for NEW trips. */
    val defaultCurrency: String = "IDR",
    /** "Jangan tanya lagi" on the reorder confirmation dialog. */
    val skipReorderConfirmation: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** false until the first read finishes, so the app does not flash the wrong theme or onboarding. */
    val loaded: Boolean = true,
)

interface PreferencesRepository {
    fun observe(): Flow<AppPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDefaultCurrency(currency: String)
    suspend fun setSkipReorderConfirmation(skip: Boolean)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setLanguage(language: AppLanguage)

    /** Small per-device blobs (e.g. the unfinished Explore session as JSON). null/blank = not set. */
    suspend fun getString(key: String): String? = null
    suspend fun putString(key: String, value: String?) = Unit
}
