package app.ransel

import androidx.compose.ui.window.ComposeUIViewController
import app.ransel.di.initKoin

fun MainViewController() = ComposeUIViewController(configure = { initKoin() }) { App() }
