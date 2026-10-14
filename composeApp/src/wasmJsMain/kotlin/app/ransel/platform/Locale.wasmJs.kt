package app.ransel.platform

import androidx.compose.runtime.Composable

actual fun applyAppLanguage(tag: String?) {
    // The browser language decides; there is no per-app override on the web.
}

@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) = Unit
