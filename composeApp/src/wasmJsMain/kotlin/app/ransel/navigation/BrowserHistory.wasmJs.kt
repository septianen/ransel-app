package app.ransel.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation

@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
actual fun BindBrowserHistory(navController: NavHostController) {
    LaunchedEffect(navController) { navController.bindToBrowserNavigation() }
}

@Composable
actual fun ExitBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
