package app.ransel.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/**
 * Web: mirrors the back stack into the browser history, so the browser's back button and the PWA's swipe back walk
 * through screens instead of leaving the app. No-op on Android and iOS.
 */
@Composable
expect fun BindBrowserHistory(navController: NavHostController)

/** System back press, for the "press again to exit" hint. Android only: iOS has no back button, the web leaves through the browser. */
@Composable
expect fun ExitBackHandler(enabled: Boolean, onBack: () -> Unit)
