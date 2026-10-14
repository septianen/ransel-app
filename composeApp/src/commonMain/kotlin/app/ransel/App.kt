package app.ransel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ransel.navigation.AccountRoute
import app.ransel.navigation.ComponentGalleryRoute
import app.ransel.navigation.ExploreRoute
import app.ransel.navigation.HomeRoute
import app.ransel.navigation.TripListRoute
import app.ransel.platform.isDebugBuild
import app.ransel.ui.gallery.ComponentGalleryScreen
import app.ransel.ui.shell.MainTab
import app.ransel.ui.shell.PlaceholderScreen
import app.ransel.ui.shell.RanselBottomBar
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.debug_component_gallery
import ransel.composeapp.generated.resources.placeholder_account_title
import ransel.composeapp.generated.resources.placeholder_body
import ransel.composeapp.generated.resources.placeholder_explore_title
import ransel.composeapp.generated.resources.placeholder_home_title
import ransel.composeapp.generated.resources.placeholder_trips_title

/** App shell: theme, four-tab bottom navigation and the NavHost. Real screens replace the placeholders per phase. */
@Composable
fun App(themeMode: ThemeMode = ThemeMode.SYSTEM) {
    IrisTheme(mode = themeMode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            val backStack by navController.currentBackStackEntryAsState()
            val destination = backStack?.destination

            val selected: MainTab? = when {
                destination == null -> MainTab.HOME
                destination.hasRoute<HomeRoute>() -> MainTab.HOME
                destination.hasRoute<TripListRoute>() -> MainTab.TRIPS
                destination.hasRoute<ExploreRoute>() -> MainTab.EXPLORE
                destination.hasRoute<AccountRoute>() -> MainTab.ACCOUNT
                else -> null // full-screen destinations hide the bar
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (selected != null) {
                        RanselBottomBar(
                            selected = selected,
                            onSelect = { tab ->
                                val route: Any = when (tab) {
                                    MainTab.HOME -> HomeRoute
                                    MainTab.TRIPS -> TripListRoute
                                    MainTab.EXPLORE -> ExploreRoute
                                    MainTab.ACCOUNT -> AccountRoute
                                }
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.padding(padding),
                ) {
                    composable<HomeRoute> {
                        PlaceholderScreen(
                            stringResource(Res.string.placeholder_home_title),
                            stringResource(Res.string.placeholder_body),
                        )
                    }
                    composable<TripListRoute> {
                        PlaceholderScreen(
                            stringResource(Res.string.placeholder_trips_title),
                            stringResource(Res.string.placeholder_body),
                        )
                    }
                    composable<ExploreRoute> {
                        PlaceholderScreen(
                            stringResource(Res.string.placeholder_explore_title),
                            stringResource(Res.string.placeholder_body),
                        )
                    }
                    composable<AccountRoute> {
                        PlaceholderScreen(
                            stringResource(Res.string.placeholder_account_title),
                            stringResource(Res.string.placeholder_body),
                            extra = {
                                if (isDebugBuild) {
                                    Button(onClick = { navController.navigate(ComponentGalleryRoute) }) {
                                        Text(stringResource(Res.string.debug_component_gallery))
                                    }
                                }
                            },
                        )
                    }
                    composable<ComponentGalleryRoute> {
                        ComponentGalleryScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
