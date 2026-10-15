package app.ransel

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.data.remote.ConnectionMonitor
import app.ransel.ui.adaptive.rememberIsExpanded
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.shell.RanselNavigationRail
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import app.ransel.ui.components.BackOnlineToast
import app.ransel.ui.components.LocalPendingIds
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.sync.SyncStatus
import app.ransel.feature.auth.CheckEmailScreen
import app.ransel.feature.auth.ForgotPasswordScreen
import app.ransel.feature.auth.NewPasswordScreen
import app.ransel.feature.auth.SessionExpiredScreen
import app.ransel.feature.auth.SignInScreen
import app.ransel.feature.auth.SignUpScreen
import app.ransel.navigation.CheckEmailRoute
import app.ransel.navigation.ForgotPasswordRoute
import app.ransel.navigation.NewPasswordRoute
import app.ransel.navigation.SessionExpiredRoute
import app.ransel.navigation.SignInRoute
import app.ransel.navigation.SignUpRoute
import app.ransel.platform.clearResetTokenFromUrl
import app.ransel.platform.initialResetToken
import app.ransel.platform.requiresAccount
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
import ransel.composeapp.generated.resources.debug_show_onboarding
import ransel.composeapp.generated.resources.placeholder_account_title
import ransel.composeapp.generated.resources.placeholder_body
import ransel.composeapp.generated.resources.placeholder_explore_title

/** App shell: theme and language from preferences, first-launch onboarding, then the tabs. */
@Composable
fun App() {
    val preferencesRepository: PreferencesRepository = koinInject()
    val preferences by preferencesRepository.observe().collectAsState(initial = AppPreferences(loaded = false))
    val scope = rememberCoroutineScope()
    var openSignInAfterOnboarding by remember { mutableStateOf(false) }

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
                        onSignIn = {
                            openSignInAfterOnboarding = true
                            scope.launch { preferencesRepository.setOnboardingCompleted(true) }
                        },
                    )
                    else -> SessionGate(openSignIn = openSignInAfterOnboarding)
                }
            }
        }
    }
}

/**
 * Reads the stored session, then decides what to show: the web needs an account (sign-in flow until
 * signed in); mobile always opens the app and lets the user sign in from Account / onboarding.
 */
@Composable
private fun SessionGate(openSignIn: Boolean) {
    val session: SessionManager = koinInject()
    val state by session.state.collectAsState()
    koinInject<SyncStatus>() // creates the sync engine (mobile) so it starts with the session
    LaunchedEffect(session) { session.restore() }
    when {
        state is SessionState.Loading -> Unit
        requiresAccount && state !is SessionState.SignedIn -> WebAuthNavigation()
        else -> RanselNavigation(openSignIn = openSignIn)
    }
}

/** Web before sign-in: only the auth screens (no tabs, no guest mode). */
@Composable
private fun WebAuthNavigation() {
    val navController = rememberNavController()
    val resetToken = remember { initialResetToken() }
    NavHost(
        navController = navController,
        startDestination = if (resetToken != null) NewPasswordRoute(resetToken) else SignInRoute,
    ) {
        authGraph(navController, allowGuest = false, onFinished = {})
    }
}

@Composable
private fun RanselNavigation(openSignIn: Boolean = false) {
    val navController = rememberNavController()
    val sessionManager: SessionManager = koinInject()
    val sessionState by sessionManager.state.collectAsState()
    LaunchedEffect(openSignIn) { if (openSignIn) navController.navigate(SignInRoute) }
    LaunchedEffect(sessionState) {
        // Mobile: the refresh token died. Offer "sign in again" once per expiry; other screens stay usable.
        if (sessionState is SessionState.Expired) navController.navigate(SessionExpiredRoute) { launchSingleTop = true }
    }
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val expanded = rememberIsExpanded()
    val monitor: ConnectionMonitor = koinInject()
    val failure by monitor.failure.collectAsState()
    val sync: SyncStatus = koinInject()
    val pendingIds by sync.pendingIds.collectAsState()
    val justSynced by sync.justSynced.collectAsState()

    val selected: MainTab? = when {
        destination == null -> MainTab.HOME
        destination.hasRoute<HomeRoute>() -> MainTab.HOME
        destination.hasRoute<TripListRoute>() -> MainTab.TRIPS
        destination.hasRoute<ExploreRoute>() -> MainTab.EXPLORE
        destination.hasRoute<AccountRoute>() -> MainTab.ACCOUNT
        else -> null // full-screen destinations hide the bar
    }
    val showTabs = selected != null || (expanded && destination != null &&
        (destination.hasRoute<TripRoute>() || destination.hasRoute<CreateTripRoute>() || destination.hasRoute<EditTripRoute>()))

    fun openTab(tab: MainTab) {
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
    }

    CompositionLocalProvider(LocalPendingIds provides pendingIds) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // Wide web windows: a navigation rail on the left; phones: the bottom bar below.
            if (expanded) {
                RanselNavigationRail(
                    selected = selected ?: if (destination?.hasRoute<TripRoute>() == true) MainTab.TRIPS else null,
                    onSelect = ::openTab,
                    onCreateTrip = { navController.navigate(CreateTripRoute) },
                )
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                // Each screen applies its own system-bar insets (tabs: below; full-screen routes: themselves).
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (!expanded && selected != null) {
                        RanselBottomBar(selected = selected, onSelect = ::openTab)
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.padding(padding),
                    enterTransition = {
                        if (betweenTabs()) EnterTransition.None
                        else slideInHorizontally(tween(SLIDE_MILLIS, easing = FastOutSlowInEasing)) { it }
                    },
                    exitTransition = {
                        if (betweenTabs()) ExitTransition.None
                        else slideOutHorizontally(tween(SLIDE_MILLIS, easing = FastOutSlowInEasing)) { -it / 4 }
                    },
                    popEnterTransition = {
                        if (betweenTabs()) EnterTransition.None
                        else slideInHorizontally(tween(SLIDE_MILLIS, easing = FastOutSlowInEasing)) { -it / 4 }
                    },
                    popExitTransition = {
                        if (betweenTabs()) ExitTransition.None
                        else slideOutHorizontally(tween(SLIDE_MILLIS, easing = FastOutSlowInEasing)) { it }
                    },
                ) {
                    tabs(navController)
                    trips(navController)
                    authGraph(navController, allowGuest = !requiresAccount, onFinished = {
                        // Signed in (or chose offline): back to the app's home, dropping the auth screens.
                        navController.navigate(HomeRoute) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    })
                }
            }
        }

        BackOnlineToast(justSynced, onShown = sync::acknowledgeJustSynced, modifier = Modifier.align(Alignment.BottomCenter))

        // Web: a failed request replaces the screen with the connection / server state (design "State").
        failure?.let { error ->
            val back = {
                monitor.dismiss()
                navController.navigate(HomeRoute) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
                Unit
            }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (error.isConnection) {
                    ConnectionErrorState(onRetry = monitor::retry, errorCode = error.code, onBack = back)
                } else {
                    ServerErrorState(onRetry = monitor::retry, errorCode = error.code, onBack = back)
                }
            }
        }
    }
    }
}

private fun NavGraphBuilder.tabs(navController: NavHostController) {
    page<HomeRoute> {
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
    page<TripListRoute> {
        TripListScreen(
            modifier = Modifier.statusBarsPadding(),
            onOpenTrip = { navController.navigate(TripRoute(it)) },
            onCreateTrip = { navController.navigate(CreateTripRoute) },
        )
    }
    page<ExploreRoute> {
        PlaceholderScreen(
            stringResource(Res.string.placeholder_explore_title),
            stringResource(Res.string.placeholder_body),
            modifier = Modifier.statusBarsPadding(),
        )
    }
    page<AccountRoute> {
        AccountScreen(
            modifier = Modifier.statusBarsPadding(),
            onSignIn = { navController.navigate(SignInRoute) },
            onSignUp = { navController.navigate(SignUpRoute) },
            debugContent = if (!isDebugBuild) null else ({
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { navController.navigate(ComponentGalleryRoute) }) {
                        Text(stringResource(Res.string.debug_component_gallery))
                    }
                    val scope = rememberCoroutineScope()
                    val preferences: PreferencesRepository = koinInject()
                    Button(onClick = { scope.launch { preferences.setOnboardingCompleted(false) } }) {
                        Text(stringResource(Res.string.debug_show_onboarding))
                    }
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
    page<ComponentGalleryRoute> {
        ComponentGalleryScreen(onBack = { navController.popBackStack() }) // handles its own insets
    }
}

private fun NavGraphBuilder.trips(navController: NavHostController) {
    page<TripRoute>(maxContentWidth = null) { entry ->
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
    page<ActivityFormRoute> { entry ->
        val route = entry.toRoute<ActivityFormRoute>()
        ActivityFormScreen(
            tripId = route.tripId,
            itemId = route.itemId,
            initialDay = route.dayDate,
            onClose = { navController.popBackStack() },
        )
    }
    page<CreateTripRoute> {
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
    page<EditTripRoute> { entry ->
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

/** Auth screens shared by the mobile app (pushed on top of the tabs) and the web sign-in flow. */
private fun NavGraphBuilder.authGraph(navController: NavHostController, allowGuest: Boolean, onFinished: () -> Unit) {
    page<SignInRoute> {
        SignInScreen(
            onSignUp = { navController.navigate(SignUpRoute) },
            onForgotPassword = { navController.navigate(ForgotPasswordRoute) },
            onContinueWithoutAccount = if (allowGuest) ({ onFinished() }) else null,
            onBack = if (allowGuest && navController.previousBackStackEntry != null) ({ navController.popBackStack() }) else null,
        )
    }
    page<SignUpRoute> {
        SignUpScreen(
            onSignIn = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }
    page<ForgotPasswordRoute> {
        ForgotPasswordScreen(
            onBack = { navController.popBackStack() },
            onSent = { email -> navController.navigate(CheckEmailRoute(email)) },
        )
    }
    page<CheckEmailRoute> { entry ->
        CheckEmailScreen(
            email = entry.toRoute<CheckEmailRoute>().email,
            onChangeEmail = { navController.popBackStack() },
        )
    }
    page<NewPasswordRoute> { entry ->
        NewPasswordScreen(
            token = entry.toRoute<NewPasswordRoute>().token,
            onDone = {
                clearResetTokenFromUrl()
                navController.navigate(SignInRoute) { popUpTo(navController.graph.findStartDestination().id) { inclusive = true } }
            },
            onRequestNewLink = {
                clearResetTokenFromUrl()
                navController.navigate(ForgotPasswordRoute)
            },
        )
    }
    page<SessionExpiredRoute> {
        val session: SessionManager = koinInject()
        val state by session.state.collectAsState()
        val user = (state as? SessionState.Expired)?.user
        val pending: SyncStatus = koinInject()
        val pendingCount by pending.pendingCount.collectAsState()
        // Leave this screen as soon as the session is no longer expired (signed in again, or offline).
        LaunchedEffect(state) { if (state !is SessionState.Expired) navController.popBackStack() }
        SessionExpiredScreen(
            email = user?.email.orEmpty(),
            pendingChanges = pendingCount,
            onSignIn = { navController.navigate(SignInRoute) },
            onUseOffline = { session.continueOffline() },
        )
    }
}

private const val SLIDE_MILLIS = 280

private fun NavBackStackEntry.isTab(): Boolean =
    destination.hasRoute<HomeRoute>() || destination.hasRoute<TripListRoute>() ||
        destination.hasRoute<ExploreRoute>() || destination.hasRoute<AccountRoute>()

/** Switching bottom tabs swaps content in place (no motion); everything else slides like a page push. */
private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
    initialState.isTab() && targetState.isTab()

/**
 * A destination with an opaque page background. Pages slide over each other, so each one must paint
 * its own background or the page underneath would show through.
 */
private inline fun <reified T : Any> NavGraphBuilder.page(
    maxContentWidth: Dp? = PageMaxWidth,
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            // On wide web windows single-column pages (Home, lists, forms) stay a readable width.
            val width = if (maxContentWidth != null) Modifier.widthIn(max = maxContentWidth) else Modifier
            Box(width.fillMaxSize()) { content(entry) }
        }
    }
}

/** Single-column pages never get wider than this, however wide the window. */
private val PageMaxWidth = 720.dp
