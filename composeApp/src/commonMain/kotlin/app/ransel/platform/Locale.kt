package app.ransel.platform

import androidx.compose.runtime.Composable

/**
 * Applies the in-app language ("id", "en", or null = follow the system) so Compose Resources pick the
 * matching `values-*` folder. Called before the UI is composed and again when the user changes it.
 * The web target follows the browser language and ignores this.
 */
expect fun applyAppLanguage(tag: String?)

/** Keeps the system bar icons readable when the app theme differs from the system theme. */
@Composable
expect fun SystemBarsAppearance(darkTheme: Boolean)
