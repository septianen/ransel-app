package app.ransel.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
actual fun BindBrowserHistory(navController: NavHostController, restoreFromUrl: Boolean) {
    LaunchedEffect(navController) {
        // Read the address before the binding does: it would try the fragment as a Navigation route (and warn).
        val restored = routeForUrlFragment(currentHash())
        clearHash()
        // Bind first and wait until it has written Home's entry, so the restored screen gets an entry of its own on top
        // and back leads Home instead of out of the app (navigating earlier puts both screens in one entry).
        launch { navController.bindToBrowserNavigation(::urlFragmentFor) }
        // Home is the start destination already; anything else opens on top of it
        if (restoreFromUrl && restored != null && restored != HomeRoute) {
            withTimeoutOrNull(2_000) { while (historyState() == null) delay(16) }
            runCatching { navController.navigate(restored) { launchSingleTop = true } }
        }
    }
}

@JsFun("() => window.location.hash")
private external fun currentHash(): String

/**
 * Drops the fragment and the state a reload kept, without a new history entry: the binding writes the current
 * screen's next, which is how [BindBrowserHistory] knows it has started.
 */
@JsFun("() => window.history.replaceState(null, '', window.location.pathname + window.location.search)")
private external fun clearHash()

@Composable
actual fun ExitBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/** An open sheet, with the history entry it was opened on. */
private class OpenSheet(val onBack: () -> Unit, val state: JsAny?, val url: String)

/** Open sheets, the newest last: back closes the newest one. */
private val openSheets = mutableListOf<OpenSheet>()
private var listening = false

@Composable
actual fun SheetBackHandler(onBack: () -> Unit) {
    val latest by rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        if (!listening) {
            listening = true
            listenPopState { onBrowserBack() }
        }
        val sheet = OpenSheet({ latest() }, historyState(), currentUrl())
        openSheets += sheet
        onDispose { openSheets -= sheet }
    }
}

/** The browser already stepped back: put the screen's entry back (so the screen stays) and close the sheet. */
private fun onBrowserBack(): Boolean {
    val sheet = openSheets.lastOrNull() ?: return false
    pushHistory(sheet.state, sheet.url)
    sheet.onBack()
    return true
}

// A capturing listener on window runs before the navigation's own popstate listener, so it can stop it.
@JsFun("(onPop) => window.addEventListener('popstate', (e) => { if (onPop()) e.stopImmediatePropagation(); }, true)")
private external fun listenPopState(onPop: () -> Boolean)

@JsFun("() => window.history.state")
private external fun historyState(): JsAny?

@JsFun("() => window.location.href")
private external fun currentUrl(): String

@JsFun("(state, url) => window.history.pushState(state, '', url)")
private external fun pushHistory(state: JsAny?, url: String)
