package app.ransel.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.screenNameOf
import org.koin.compose.koinInject

/** Logs a screen view each time [navController] shows another destination (the route's class name, no arguments). */
@Composable
fun TrackScreens(navController: NavHostController) {
    val analytics: Analytics = koinInject()
    val entry by navController.currentBackStackEntryAsState()
    LaunchedEffect(entry?.id) {
        val route = entry?.destination?.route ?: return@LaunchedEffect
        analytics.screen(screenNameOf(route))
    }
}
