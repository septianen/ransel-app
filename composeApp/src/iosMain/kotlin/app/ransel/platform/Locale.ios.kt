package app.ransel.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSUserDefaults

actual fun applyAppLanguage(tag: String?) {
    val defaults = NSUserDefaults.standardUserDefaults
    if (tag == null) defaults.removeObjectForKey("AppleLanguages") else defaults.setObject(listOf(tag), "AppleLanguages")
}

@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) {
    // The status bar style follows the app's interface style on iOS; nothing to do here yet.
}
