package app.ransel.platform

import androidx.compose.runtime.Composable

/**
 * The strings follow the browser language, which index.html replaces with the stored choice before the app starts.
 * So a new choice needs a reload. [tag] is not compared directly: before the preferences load it is the default, so
 * the stored choice (written before this runs) is what counts.
 */
actual fun applyAppLanguage(tag: String?) {
    if (storedLanguageTag() != startedLanguageTag()) reloadPage()
}

@JsFun("() => { try { return ({ INDONESIAN: 'id', ENGLISH: 'en' })[localStorage.getItem('ransel.language')] || null; } catch (e) { return null; } }")
private external fun storedLanguageTag(): String?

@JsFun("() => window.RANSEL_LANGUAGE_TAG || null")
private external fun startedLanguageTag(): String?

@JsFun("() => window.location.reload()")
private external fun reloadPage()

@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) = Unit
