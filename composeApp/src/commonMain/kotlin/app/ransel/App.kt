package app.ransel

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import app.ransel.feature.account.AccountScreen
import app.ransel.feature.onboarding.OnboardingScreen
import app.ransel.platform.SystemBarsAppearance
import app.ransel.platform.applyAppLanguage
import app.ransel.ui.theme.ThemeMode
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.debug.seedSampleTrip
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.ransel.data.repository.AppPreferences
import app.ransel.data.repository.PreferencesRepository
import app.ransel.feature.home.HomeScreen
import app.ransel.feature.itinerary.ActivityFormScreen
import app.ransel.feature.home.TripSection
import app.ransel.feature.trip.TripScreen
import app.ransel.feature.tripform.TripFormScreen
import app.ransel.feature.trips.TripListScreen
import app.ransel.navigation.AccountRoute
import app.ransel.navigation.ActivityFormRoute
import app.ransel.navigation.ComponentGalleryRoute
import app.ransel.navigation.CreateTripRoute
import app.ransel.navigation.EditTripRoute
import app.ransel.navigation.ExploreRoute
import app.ransel.navigation.HomeRoute
import app.ransel.navigation.TripListRoute
import app.ransel.navigation.TripRoute
import app.ransel.platform.isDebugBuild
import app.ransel.ui.components.TripTab
import app.ransel.ui.gallery.ComponentGalleryScreen
import app.ransel.ui.shell.MainTab
import app.ransel.ui.shell.PlaceholderScreen
import app.ransel.ui.shell.RanselBottomBar
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.debug_component_gallery
import ransel.composeapp.generated.resources.debug_seed_sample
import ransel.composeapp.generated.resources.placeholder_account_title
import ransel.composeapp.generated.resources.placeholder_body
import ransel.composeapp.generated.resources.placeholder_explore_title

/** App shell: theme and language from preferences, first-launch onboarding, then the tabs. */
@Composable
fun App() {
    val preferencesRepository: PreferencesRepository = koinInject()
    val preferences by preferencesRepository.observe().collectAsState(initial = AppPreferences(loaded = false))
    val scope = rememberCoroutineScope()

    // Apply the chosen language before any string is read; `key` rebuilds the UI when it changes.
    remember(preferences.language) { applyAppLanguage(preferences.language.tag) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    key(preferences.language) {
        IrisTheme(mode = preferences.themeMode) {
            SystemBarsAppearance(darkTheme = dark)
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    !preferences.loaded -> Unit
                    !preferences.onboardingCompleted -> OnboardingScreen(
                        onFinish = { scope.launch { preferencesRepository.setOnboardingCompleted(true) } },
                    )
                    else -> RanselNavigation()
                }
            }
        }
    }
}

@Composable
private fun RanselNavigation() {
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
        // Each screen applies its own system-bar insets (tabs: below; full-screen routes: themselves).
        contentWindowInsets = WindowInsets(0),
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
            tabs(navController)
            trips(navController)
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.tabs(navController: NavHostController) {
    composable<HomeRoute> {
        HomeScreen(
            modifier = Modifier.statusBarsPadding(),
            onOpenTrip = { id, section ->
                val tab = when (section) {
                    TripSection.ITINERARY -> TripTab.ITINERARY
                    TripSection.PACKING -> TripTab.PACKING
                    TripSection.EXPENSES -> TripTab.EXPENSES
                }
                navController.navigate(TripRoute(id, tab.name))
            },
            onCreateTrip = { navController.navigate(CreateTripRoute) },
        )
    }
    composable<TripListRoute> {
        TripListScreen(
            modifier = Modifier.statusBarsPadding(),
            onOpenTrip = { navController.navigate(TripRoute(it)) },
            onCreateTrip = { navController.navigate(CreateTripRoute) },
        )
    }
    composable<ExploreRoute> {
        PlaceholderScreen(
            stringResource(Res.string.placeholder_explore_title),
            stringResource(Res.string.placeholder_body),
            modifier = Modifier.statusBarsPadding(),
        )
    }
    composable<AccountRoute> {
        AccountScreen(
            modifier = Modifier.statusBarsPadding(),
            debugContent = if (!isDebugBuild) null else ({
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { navController.navigate(ComponentGalleryRoute) }) {
                        Text(stringResource(Res.string.debug_component_gallery))
                    }
                    val scope = rememberCoroutineScope()
                    val trips: TripRepository = koinInject()
                    val itinerary: ItineraryRepository = koinInject()
                    val packing: PackingRepository = koinInject()
                    val expenses: ExpenseRepository = koinInject()
                    Button(onClick = { scope.launch { seedSampleTrip(trips, itinerary, packing, expenses) } }) {
                        Text(stringResource(Res.string.debug_seed_sample))
                    }
                }
            }),
        )
    }
    composable<ComponentGalleryRoute> {
        ComponentGalleryScreen(onBack = { navController.popBackStack() }) // handles its own insets
    }
}

private fun androidx.navigation.NavGraphBuilder.trips(navController: NavHostController) {
    composable<TripRoute> { entry ->
        val route = entry.toRoute<TripRoute>()
        TripScreen(
            tripId = route.tripId,
            initialTab = TripTab.entries.firstOrNull { it.name == route.tab } ?: TripTab.ITINERARY,
            onBack = { navController.popBackStack() },
            onEditTrip = { navController.navigate(EditTripRoute(route.tripId)) },
            onAddActivity = { day -> navController.navigate(ActivityFormRoute(route.tripId, null, day)) },
            onEditActivity = { id -> navController.navigate(ActivityFormRoute(route.tripId, id, null)) },
        )
    }
    composable<ActivityFormRoute> { entry ->
        val route = entry.toRoute<ActivityFormRoute>()
        ActivityFormScreen(
            tripId = route.tripId,
            itemId = route.itemId,
            initialDay = route.dayDate,
            onClose = { navController.popBackStack() },
        )
    }
    composable<CreateTripRoute> {
        TripFormScreen(
            tripId = null,
            onClose = { navController.popBackStack() },
            onSaved = { id ->
                // Replace the form with the new trip so Back returns to where the user came from.
                navController.navigate(TripRoute(id)) { popUpTo<CreateTripRoute> { inclusive = true } }
            },
            onDeleted = {},
        )
    }
    composable<EditTripRoute> { entry ->
        val route = entry.toRoute<EditTripRoute>()
        TripFormScreen(
            tripId = route.tripId,
            onClose = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            onDeleted = {
                // The trip is gone: back to the list, skipping the trip screen that no longer exists.
                navController.popBackStack<TripRoute>(inclusive = true)
            },
        )
    }
}
