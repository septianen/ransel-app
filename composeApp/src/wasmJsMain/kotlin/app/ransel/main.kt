package app.ransel

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.ransel.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // named zones for overseas trips must be loaded before any TimeZone.of("Asia/Tokyo")
    app.ransel.platform.jsJodaTimeZones
    initKoin()
    ComposeViewport { App() }
}
