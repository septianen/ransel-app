package app.ransel

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.window.OnFocusBehavior
import app.ransel.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
        // App() applies WindowInsets.ime at the nav shell and on sheets. The default iOS behavior also pans the
        // whole Compose view, which would double-shift a focused field.
        onFocusBehavior = OnFocusBehavior.DoNothing
    },
) { App() }
