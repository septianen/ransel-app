package app.ransel

import kotlinx.coroutines.flow.map
import androidx.compose.animation.EnterTransition
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
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
import app.ransel.ui.components.SplashScreen
import kotlinx.coroutines.delay
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.shell.RanselNavigationRail
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import app.ransel.ui.components.BackOnlineToast
import app.ransel.ui.components.ExitHintToast
import app.ransel.navigation.BindBrowserHistory
import app.ransel.navigation.ExitBackHandler
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
import app.ransel.navigation.DocumentsRoute
import app.ransel.navigation.RecapRoute
import app.ransel.navigation.ForgotPasswordRoute
import app.ransel.navigation.NewPasswordRoute
import app.ransel.navigation.SessionExpiredRoute
import app.ransel.navigation.SignInRoute
import app.ransel.navigation.SignUpRoute
import app.ransel.platform.clearResetTokenFromUrl
import app.ransel.platform.initialInviteCode
import app.ransel.platform.initialGuestPollCode
import app.ransel.platform.initialResetToken
import app.ransel.data.auth.EmailVerification
import app.ransel.feature.auth.EmailVerifiedScreen
import app.ransel.feature.auth.VerifyEmailScreen
import app.ransel.navigation.EmailVerifiedRoute
import app.ransel.navigation.VerifyEmailRoute
import app.ransel.platform.initialVerifyToken
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
import app.ransel.navigation.CreditsRoute
import app.ransel.navigation.PaymentMethodsRoute
import app.ransel.navigation.NotificationSettingsRoute
import app.ransel.navigation.NotificationsRoute
import app.ransel.feature.notifications.NotificationPermissionSheet
import app.ransel.feature.notifications.NotificationSettingsScreen
import app.ransel.feature.notifications.NotificationsScreen
import app.ransel.data.notification.PushRegistrar
import app.ransel.feature.credits.CreditsScreen
import app.ransel.navigation.ActivityFormRoute
import app.ransel.navigation.ComponentGalleryRoute
import app.ransel.navigation.CreateTripRoute
import app.ransel.navigation.InviteFriendsRoute
import app.ransel.feature.invite.InviteFriendsScreen
import app.ransel.navigation.EditTripRoute
import app.ransel.navigation.ExplorePreferencesRoute
import app.ransel.navigation.ExploreRoute
import app.ransel.navigation.HomeRoute
import app.ransel.navigation.JoinTripRoute
import app.ransel.navigation.SwipeRoute
import app.ransel.navigation.VoteRoute
import ransel.composeapp.generated.resources.place_detail_vote_strip
import app.ransel.feature.voting.VoteMode
import app.ransel.feature.voting.VoteScreen
import app.ransel.feature.voting.VoteViewModel
import app.ransel.feature.voting.voteViewModelKey
import app.ransel.feature.explore.toDetailInfo
import app.ransel.data.explore.ExploreCard
import app.ransel.shared.domain.hoursLeft
import app.ransel.shared.model.Vote
import kotlin.time.Clock
import app.ransel.feature.explore.ExplorePreferencesScreen
import app.ransel.feature.explore.ExploreTripPickerScreen
import app.ransel.feature.explore.SwipeScreen
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.feature.polls.CreatePollScreen
import app.ransel.feature.polls.PollScreen
import app.ransel.navigation.CreatePollRoute
import app.ransel.navigation.MembersRoute
import app.ransel.navigation.PollRoute
import app.ransel.shared.model.PollKind
import app.ransel.feature.invite.JoinTripScreen
import app.ransel.data.invite.InviteLinks
import app.ransel.data.notification.NotificationTarget
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.notification.PushRegistration
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.CrashReporter
import app.ransel.data.analytics.applySessionContext
import app.ransel.data.remote.RanselApi
import app.ransel.feature.members.MembersScreen
import app.ransel.navigation.TripListRoute
import app.ransel.navigation.TripRoute
import app.ransel.navigation.TrackScreens
import app.ransel.platform.isDebugBuild
import app.ransel.ui.components.TripTab
import app.ransel.ui.gallery.ComponentGalleryScreen
import app.ransel.ui.shell.MainTab
import app.ransel.ui.shell.PlaceholderScreen
import app.ransel.ui.shell.RanselBottomBar
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import app.ransel.feature.explore.PlaceDetailScreen
import app.ransel.feature.explore.SwipeViewModel
import app.ransel.navigation.PlaceDetailRoute
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.debug_component_gallery
import ransel.composeapp.generated.resources.debug_seed_sample
import ransel.composeapp.generated.resources.debug_show_onboarding
import ransel.composeapp.generated.resources.placeholder_account_title
import ransel.composeapp.generated.resources.placeholder_body
import ransel.composeapp.generated.resources.placeholder_explore_title

private const val SPLASH_MIN_MS = 1_200L

/** Back-stack result from "Semua transaksi" to the trip screen. */
private const val OPEN_BALANCES_KEY = "open_balances"

/** App shell: theme and language from preferences, first-launch onboarding, then the tabs. */
@Composable
fun App() {
    // Coil needs its network fetcher registered explicitly outside the JVM; plain Ktor GET, no bearer header.
    remember {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.build()
        }
    }
    val preferencesRepository: PreferencesRepository = koinInject()
    val preferences by preferencesRepository.observe().collectAsState(initial = AppPreferences(loaded = false))
    val scope = rememberCoroutineScope()
    var openSignInAfterOnboarding by remember { mutableStateOf(false) }
    // The splash stays at least this long on a cold start so it does not flash by while preferences load.
    var splashHeld by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(SPLASH_MIN_MS); splashHeld = false }

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
                    !preferences.loaded || splashHeld -> SplashScreen()
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
    val syncEngine = koinInject<SyncStatus>() // creates the sync engine (mobile) so it starts with the session
    LifecycleEventEffect(Lifecycle.Event.ON_START) { syncEngine.setInForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { syncEngine.setInForeground(false) }
    LaunchedEffect(session) { session.restore() }
    val inviteLinks: InviteLinks = koinInject()
    LaunchedEffect(inviteLinks) { initialInviteCode()?.let(inviteLinks::offerCode) } // web: opened /j/<code>
    // Web, signed in, opened /verify-email?token=: the app shows the result. Signed out, WebAuthNavigation starts on it.
    val verification: EmailVerification = koinInject()
    var verifyLinkOffered by remember { mutableStateOf(false) }
    LaunchedEffect(state is SessionState.SignedIn) {
        if (state is SessionState.SignedIn && !verifyLinkOffered) {
            verifyLinkOffered = true
            initialVerifyToken()?.let(verification::offerToken)
        }
    }
    // Analytics and crash reports know the account (its id only), its state and the language; cleared when signed out.
    val analytics: Analytics = koinInject()
    val crashReporter: CrashReporter = koinInject()
    val language by koinInject<PreferencesRepository>().observe().map { it.language }.collectAsState(initial = null)
    LaunchedEffect(state, language) {
        val current = language ?: return@LaunchedEffect
        if (state is SessionState.Loading) return@LaunchedEffect
        applySessionContext(analytics, crashReporter, state, current, requiresAccount)
    }
    // Signed in: tell the server which phone this is (push) and fetch the unread count.
    val pushRegistration: PushRegistration = koinInject()
    val notificationsStore: NotificationsStore = koinInject()
    val api: RanselApi = koinInject()
    val registrar: PushRegistrar = koinInject()
    var explainPermission by remember { mutableStateOf(false) }
    LaunchedEffect(state is SessionState.SignedIn) {
        if (state is SessionState.SignedIn) {
            runCatching { pushRegistration.register() }.onFailure(crashReporter::record)
            notificationsStore.refresh(api)
        }
    }
    // Once the app is up (past the splash), guests included: explain once, then the system dialog (Android 13+, iOS).
    LaunchedEffect(state is SessionState.Loading) {
        if (state !is SessionState.Loading) explainPermission = registrar.shouldExplainPermission()
    }
    // Web: a poll link (/p/<code>) opens the guest page whoever is signed in; an invite link opened while signed out
    // offers "Lihat tanpa akun" next to joining with an account.
    var guest by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var guestChecked by remember { mutableStateOf(false) }
    LaunchedEffect(state is SessionState.Loading) {
        if (state is SessionState.Loading || guestChecked) return@LaunchedEffect
        guestChecked = true
        guest = initialGuestPollCode()?.let { it to false }
            ?: initialInviteCode()?.takeIf { requiresAccount && state !is SessionState.SignedIn }?.let { it to true }
    }
    val pendingPoll: app.ransel.feature.guest.PendingPollLink = koinInject()
    when {
        state is SessionState.Loading -> SplashScreen()
        guest != null -> guest?.let { (code, invite) ->
            app.ransel.feature.guest.GuestPageScreen(
                code = code,
                invite = invite,
                onSignIn = { guest = null },
                onOpenPoll = { tripId, pollId -> pendingPoll.offer(tripId, pollId); guest = null },
            )
        }
        requiresAccount && state !is SessionState.SignedIn -> WebAuthNavigation()
        else -> RanselNavigation(openSignIn = openSignIn)
    }
    if (explainPermission) {
        NotificationPermissionSheet(
            onAllow = { explainPermission = false; registrar.explanationDismissed(); registrar.requestPermission() },
            onLater = { explainPermission = false; registrar.explanationDismissed() },
        )
    }
}

/** Web before sign-in: only the auth screens (no tabs, no guest mode). */
@Composable
private fun WebAuthNavigation() {
    val navController = rememberNavController()
    val resetToken = remember { initialResetToken() }
    val verifyToken = remember { initialVerifyToken() }
    TrackScreens(navController)
    BindBrowserHistory(navController)
    NavHost(
        navController = navController,
        startDestination = when {
            verifyToken != null -> EmailVerifiedRoute(verifyToken)
            resetToken != null -> NewPasswordRoute(resetToken)
            else -> SignInRoute
        },
    ) {
        authGraph(navController, allowGuest = false, onFinished = {})
    }
}

@Composable
private fun RanselNavigation(openSignIn: Boolean = false) {
    val navController = rememberNavController()
    TrackScreens(navController)
    BindBrowserHistory(navController)
    val sessionManager: SessionManager = koinInject()
    val sessionState by sessionManager.state.collectAsState()
    LaunchedEffect(openSignIn) { if (openSignIn) navController.navigate(SignInRoute) }
    // Mobile: an admin deactivated the account. The session ended locally (data stays); sign-in says why.
    val accountDisabled by sessionManager.accountDisabled.collectAsState()
    LaunchedEffect(accountDisabled) { if (accountDisabled) navController.navigate(SignInRoute) { launchSingleTop = true } }
    // An invite link was opened (or `/j/<code>` on the web): show the join screen over whatever is open.
    val exploreStore: ExploreSessionStore = koinInject()
    val inviteLinks: InviteLinks = koinInject()
    val pendingInvite by inviteLinks.pending.collectAsState()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingInvite, sessionState, currentEntry?.id) {
        val code = pendingInvite ?: return@LaunchedEffect
        val here = navController.currentDestination
        val onJoin = here != null && here.hasRoute<JoinTripRoute>()
        val onAuth = here != null && listOf(
            SignInRoute::class, SignUpRoute::class, ForgotPasswordRoute::class, CheckEmailRoute::class,
            NewPasswordRoute::class, SessionExpiredRoute::class, VerifyEmailRoute::class, EmailVerifiedRoute::class,
        ).any { here.hasRoute(it) }
        if (!onJoin && !onAuth) navController.navigate(JoinTripRoute(code)) { launchSingleTop = true }
    }
    // "Buka di RANSEL" on the guest page: a member opened their own poll link.
    val pendingPoll: app.ransel.feature.guest.PendingPollLink = koinInject()
    val pollLink by pendingPoll.poll.collectAsState()
    LaunchedEffect(pollLink, sessionState) {
        val (tripId, pollId) = pollLink ?: return@LaunchedEffect
        if (sessionState !is SessionState.SignedIn) return@LaunchedEffect
        pendingPoll.clear()
        navController.navigate(PollRoute(tripId, pollId)) { launchSingleTop = true }
    }
    // A verification link was opened (App Link, or /verify-email on the web while signed in).
    val verification: EmailVerification = koinInject()
    val verifyToken by verification.token.collectAsState()
    LaunchedEffect(verifyToken) {
        val token = verifyToken ?: return@LaunchedEffect
        verification.clearToken()
        navController.navigate(EmailVerifiedRoute(token)) { launchSingleTop = true }
    }
    // Just signed up somewhere other than an auth screen (web: the sign-up screen was in WebAuthNavigation).
    val verifyPrompt by verification.prompt.collectAsState()
    LaunchedEffect(verifyPrompt, sessionState, currentEntry?.id) {
        val email = verifyPrompt ?: return@LaunchedEffect
        val here = navController.currentDestination ?: return@LaunchedEffect
        val onAuth = listOf(SignInRoute::class, SignUpRoute::class).any { here.hasRoute(it) }
        if (sessionState is SessionState.SignedIn && !onAuth) {
            verification.clearPrompt()
            navController.navigate(VerifyEmailRoute(email)) { launchSingleTop = true }
        }
    }
    // A notification was tapped: open its trip once we are signed in.
    val notificationTarget: NotificationTarget = koinInject()
    val tappedTrip by notificationTarget.tripId.collectAsState()
    LaunchedEffect(tappedTrip, sessionState) {
        val tripId = tappedTrip ?: return@LaunchedEffect
        if (sessionState is SessionState.SignedIn) {
            val round = notificationTarget.roundId.value
            val showResult = notificationTarget.showResult.value
            notificationTarget.clear()
            // A voting notification opens its round on top of the trip, so back goes to the trip.
            navController.navigate(TripRoute(tripId)) { launchSingleTop = true }
            if (round != null) navController.navigate(VoteRoute(tripId, round, showResult)) { launchSingleTop = true }
        }
    }
    LaunchedEffect(sessionState) {
        // Signed in from an auth screen (sign in / sign up): leave the auth flow, back to Home.
        val here = navController.currentDestination
        val onAuthScreen = here != null && listOf(
            SignInRoute::class, SignUpRoute::class, ForgotPasswordRoute::class,
            CheckEmailRoute::class, NewPasswordRoute::class, SessionExpiredRoute::class,
        ).any { here.hasRoute(it) }
        if (sessionState is SessionState.SignedIn && onAuthScreen) {
            // Just signed up: "Satu langkah lagi" instead of Home (it closes to Home).
            val email = verification.prompt.value
            verification.clearPrompt()
            navController.navigate(if (email != null) VerifyEmailRoute(email) else HomeRoute) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
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

    // The rail (wide web windows) marks the open tab; on a trip it marks "Trip".
    val railSelected: MainTab? = when {
        destination == null -> MainTab.HOME
        destination.hasRoute<HomeRoute>() -> MainTab.HOME
        destination.hasRoute<TripListRoute>() || destination.hasRoute<TripRoute>() -> MainTab.TRIPS
        destination.hasRoute<ExploreRoute>() -> MainTab.EXPLORE
        destination.hasRoute<AccountRoute>() -> MainTab.ACCOUNT
        else -> null
    }

    // Mobile: at the root of the back stack (Home) the first back press only warns; a second one within 2 s exits.
    var exitArmed by remember { mutableStateOf(false) }
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(2_000)
            exitArmed = false
        }
    }
    ExitBackHandler(enabled = !requiresAccount && !exitArmed && backStack != null && navController.previousBackStackEntry == null) {
        exitArmed = true
    }

    CompositionLocalProvider(LocalPendingIds provides pendingIds) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // Wide web windows: a navigation rail on the left; phones: the bottom bar below.
            if (expanded) {
                RanselNavigationRail(
                    selected = railSelected,
                    onSelect = navController::openTab,
                    onCreateTrip = { navController.navigate(CreateTripRoute) },
                )
            }
            // Each screen applies its own system-bar insets; the four tabs bring their own bottom bar (TabPage), so
            // every other destination is full screen and slides in over it.
            Box(Modifier.weight(1f).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
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
                    page<VerifyEmailRoute> { entry ->
                        VerifyEmailScreen(
                            email = entry.toRoute<VerifyEmailRoute>().email,
                            onLater = {
                                navController.navigate(HomeRoute) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
            }
        }

        ExitHintToast(exitArmed, modifier = Modifier.align(Alignment.BottomCenter))
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
    tabPage<HomeRoute>(MainTab.HOME, navController) {
        HomeScreen(
            modifier = Modifier.statusBarsPadding(),
            onOpenTrip = { id, section ->
                val tab = when (section) {
                    TripSection.OVERVIEW -> TripTab.OVERVIEW
                    TripSection.ITINERARY -> TripTab.ITINERARY
                    TripSection.PACKING -> TripTab.PACKING
                    TripSection.EXPENSES -> TripTab.EXPENSES
                }
                navController.navigate(TripRoute(id, tab.name))
            },
            onCreateTrip = { navController.navigate(CreateTripRoute) },
            onOpenNotifications = { navController.navigate(NotificationsRoute) },
            onOpenRecap = { navController.navigate(RecapRoute(it)) },
            // "Coba": the preferences step on a new trip ("Trip baru"), full screen over Home.
            onExplore = { navController.navigate(ExplorePreferencesRoute(null)) },
        )
    }
    tabPage<TripListRoute>(MainTab.TRIPS, navController) {
        TripListScreen(
            modifier = Modifier.statusBarsPadding(),
            onOpenTrip = { navController.navigate(TripRoute(it)) },
            onCreateTrip = { navController.navigate(CreateTripRoute) },
        )
    }
    tabPage<ExploreRoute>(MainTab.EXPLORE, navController) {
        ExploreTripPickerScreen(
            onSignIn = { navController.navigate(SignInRoute) },
            onSignUp = { navController.navigate(SignUpRoute) },
            onPickTrip = { navController.navigate(ExplorePreferencesRoute(it)) },
            onNewTrip = { navController.navigate(ExplorePreferencesRoute(null)) },
            onOpenSwipe = { tripId -> navController.navigate(SwipeRoute(tripId)) },
        )
    }
    page<ExplorePreferencesRoute> { entry ->
        val route = entry.toRoute<ExplorePreferencesRoute>()
        ExplorePreferencesScreen(
            tripId = route.tripId,
            onBack = { navController.popBackStack() },
            // "Ganti": back to the picker, whichever way the form was opened (from Home or a trip: switch to the Explore tab).
            onChangeTrip = {
                if (!navController.popBackStack<ExploreRoute>(inclusive = false)) {
                    navController.navigate(ExploreRoute) {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            onOpenSwipe = { tripId -> navController.navigate(SwipeRoute(tripId)) },
        )
    }
    page<SwipeRoute> { entry ->
        val swipeRoute = entry.toRoute<SwipeRoute>()
        SwipeScreen(
            tripId = swipeRoute.tripId,
            onClose = { navController.popBackStack() },
            // Back to the form of the same trip, filled in with the last answers.
            onChangePreferences = {
                navController.navigate(ExplorePreferencesRoute(swipeRoute.tripId)) { popUpTo<SwipeRoute> { inclusive = true } }
            },
            onOpenItinerary = { id ->
                navController.navigate(TripRoute(id, TripTab.ITINERARY.name)) { popUpTo<SwipeRoute> { inclusive = true } }
            },
            onOpenCredits = { navController.navigate(CreditsRoute) },
            onHome = {
                navController.navigate(HomeRoute) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            },
            onOpenDetail = { placeId -> navController.navigate(PlaceDetailRoute(placeId, tripId = swipeRoute.tripId)) },
            // The search became a voting round: the vote screen replaces the swipe screen.
            onOpenRound = { tripId, roundId ->
                navController.navigate(VoteRoute(tripId, roundId)) { popUpTo<SwipeRoute> { inclusive = true } }
            },
        )
    }
    page<VoteRoute> { entry ->
        val route = entry.toRoute<VoteRoute>()
        VoteScreen(
            tripId = route.tripId,
            roundId = route.roundId,
            showResult = route.showResult,
            onClose = { navController.popBackStack() },
            onOpenItinerary = {
                navController.navigate(TripRoute(route.tripId, TripTab.ITINERARY.name)) {
                    popUpTo<VoteRoute> { inclusive = true }
                    launchSingleTop = true
                }
            },
            onOpenMembers = { navController.navigate(MembersRoute(route.tripId)) },
            onOpenDetail = { placeId -> navController.navigate(PlaceDetailRoute(placeId, tripId = route.tripId, roundId = route.roundId)) },
        )
    }
    page<PlaceDetailRoute>(maxContentWidth = null) { entry -> // adapts itself: two columns from 900 dp
        val route = entry.toRoute<PlaceDetailRoute>()
        // From a swipe card: Skip / Save act on the swipe screen's own ViewModel, so they change the same session.
        // Without a swipe screen below (or from the itinerary) there are no buttons.
        val swipeEntry = remember(entry) {
            if (route.fromItinerary || route.tripId == null) null else runCatching { navController.getBackStackEntry<SwipeRoute>() }.getOrNull()
        }
        val swipe: SwipeViewModel? = swipeEntry?.takeIf { route.roundId == null }
            ?.let { koinViewModel<SwipeViewModel>(viewModelStoreOwner = it, key = "swipe-${route.tripId}") { parametersOf(route.tripId) } }
        // From a voting card: the card and the Lewati / Suka votes belong to the vote screen's ViewModel.
        val voteEntry = remember(entry) {
            if (route.roundId == null) null else runCatching { navController.getBackStackEntry<VoteRoute>() }.getOrNull()
        }
        val vote: VoteViewModel? = voteEntry?.let {
            koinViewModel<VoteViewModel>(viewModelStoreOwner = it, key = voteViewModelKey(route.roundId!!)) { parametersOf(route.tripId, route.roundId, false) }
        }
        val voteState = vote?.state?.collectAsState()?.value
        val votePlace = voteState?.round?.places?.firstOrNull { it.item.placeId == route.placeId }
        // Only the card on top of the deck can be voted from here; any other card is read-only.
        val canVote = vote != null && voteState?.mode == VoteMode.VOTING &&
            voteState.round?.places?.getOrNull(voteState.cursor ?: -1)?.item?.placeId == route.placeId
        PlaceDetailScreen(
            route = route,
            onBack = { navController.popBackStack() },
            onSkip = if (vote != null) { if (canVote) ({ vote.voteFromDetail(route.placeId, Vote.SKIP); navController.popBackStack() }) else null }
                else swipe?.let { vm -> { vm.skip(); navController.popBackStack() } },
            onSave = if (vote != null) { if (canVote) ({ vote.voteFromDetail(route.placeId, Vote.LIKE); navController.popBackStack() }) else null }
                else swipe?.let { vm -> { vm.like(); navController.popBackStack() } },
            voteInfo = votePlace?.let { ExploreCard(it.category, it.item).toDetailInfo() },
            voteStrip = voteState?.round?.takeIf { votePlace != null }?.let { round ->
                stringResource(
                    Res.string.place_detail_vote_strip,
                    voteState.destination.ifBlank { voteState.tripTitle },
                    round.createdByName,
                    hoursLeft(Clock.System.now(), round.closesAt),
                )
            },
        )
    }
    page<CreditsRoute> {
        CreditsScreen(onBack = { navController.popBackStack() })
    }
    page<PaymentMethodsRoute> {
        app.ransel.feature.account.PaymentMethodsScreen(onBack = { navController.popBackStack() })
    }
    page<NotificationsRoute> {
        NotificationsScreen(
            onBack = { navController.popBackStack() },
            onOpenTrip = { navController.navigate(TripRoute(it)) },
            onOpenRound = { tripId, roundId, showResult -> navController.navigate(VoteRoute(tripId, roundId, showResult)) },
            onOpenMembers = { tripId, guestId, guestName, claimer -> navController.navigate(MembersRoute(tripId, guestId, guestName, claimer)) },
            onOpenPoll = { tripId, pollId -> navController.navigate(PollRoute(tripId, pollId)) },
            onOpenSettings = { navController.navigate(NotificationSettingsRoute) },
            onOpenExpenses = { navController.navigate(TripRoute(it, TripTab.EXPENSES.name)) },
            onOpenDocuments = { navController.navigate(DocumentsRoute(it)) },
            onOpenRecap = { navController.navigate(RecapRoute(it)) },
        )
    }
    page<NotificationSettingsRoute> {
        NotificationSettingsScreen(onBack = { navController.popBackStack() })
    }
    tabPage<AccountRoute>(MainTab.ACCOUNT, navController) {
        AccountScreen(
            modifier = Modifier.statusBarsPadding(),
            onOpenCredits = { navController.navigate(CreditsRoute) },
            onOpenPaymentMethods = { navController.navigate(PaymentMethodsRoute) },
            onOpenNotifications = { navController.navigate(NotificationSettingsRoute) },
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
        // set by "Semua transaksi" when a payment is tapped: show Saldo on the Biaya tab
        val openBalances by entry.savedStateHandle.getStateFlow(OPEN_BALANCES_KEY, false).collectAsState()
        TripScreen(
            tripId = route.tripId,
            openBalances = openBalances,
            onBalancesOpened = { entry.savedStateHandle[OPEN_BALANCES_KEY] = false },
            initialTab = TripTab.entries.firstOrNull { it.name == route.tab } ?: TripTab.OVERVIEW,
            onBack = { navController.popBackStack() },
            onEditTrip = { navController.navigate(EditTripRoute(route.tripId)) },
            onMembers = { navController.navigate(MembersRoute(route.tripId)) },
            onExplore = { navController.navigate(ExplorePreferencesRoute(route.tripId)) },
            onAddActivity = { day -> navController.navigate(ActivityFormRoute(route.tripId, null, day)) },
            onEditActivity = { id -> navController.navigate(ActivityFormRoute(route.tripId, id, null)) },
            onOpenPlaceDetail = { placeId, name, currency ->
                navController.navigate(PlaceDetailRoute(placeId, fromItinerary = true, name = name, currency = currency))
            },
            onOpenRound = { roundId, showResult -> navController.navigate(VoteRoute(route.tripId, roundId, showResult)) },
            onOpenVotingHistory = { navController.navigate(app.ransel.navigation.VotingHistoryRoute(route.tripId)) },
            onCreatePoll = { kind -> navController.navigate(CreatePollRoute(route.tripId, kind.name)) },
            onOpenPoll = { pollId -> navController.navigate(PollRoute(route.tripId, pollId)) },
            onOpenPaymentMethods = { navController.navigate(PaymentMethodsRoute) },
            onOpenDocuments = { upload -> navController.navigate(DocumentsRoute(route.tripId, upload)) },
            onOpenRecap = { navController.navigate(RecapRoute(route.tripId)) },
            onOpenAllTransactions = { navController.navigate(app.ransel.navigation.AllTransactionsRoute(route.tripId)) },
        )
    }
    page<PollRoute> { entry ->
        val route = entry.toRoute<PollRoute>()
        PollScreen(tripId = route.tripId, pollId = route.pollId, openShare = route.share, onBack = { navController.popBackStack() })
    }
    page<RecapRoute> { entry ->
        val route = entry.toRoute<RecapRoute>()
        app.ransel.feature.recap.RecapScreen(
            tripId = route.tripId,
            onClose = { navController.popBackStack() },
            onNewTrip = { navController.navigate(CreateTripRoute) },
        )
    }
    page<app.ransel.navigation.AllTransactionsRoute> { entry ->
        val route = entry.toRoute<app.ransel.navigation.AllTransactionsRoute>()
        app.ransel.feature.expenses.AllTransactionsScreen(
            tripId = route.tripId,
            onBack = { navController.popBackStack() },
            onOpenBalances = {
                navController.previousBackStackEntry?.savedStateHandle?.set(OPEN_BALANCES_KEY, true)
                navController.popBackStack()
            },
        )
    }
    page<app.ransel.navigation.VotingHistoryRoute> { entry ->
        val route = entry.toRoute<app.ransel.navigation.VotingHistoryRoute>()
        app.ransel.feature.group.VotingHistoryScreen(
            tripId = route.tripId,
            onBack = { navController.popBackStack() },
            onOpenPoll = { pollId -> navController.navigate(PollRoute(route.tripId, pollId)) },
            onOpenRound = { roundId, showResult -> navController.navigate(VoteRoute(route.tripId, roundId, showResult)) },
        )
    }
    page<DocumentsRoute> { entry ->
        val route = entry.toRoute<DocumentsRoute>()
        app.ransel.feature.documents.DocumentsScreen(tripId = route.tripId, startUpload = route.upload, onBack = { navController.popBackStack() })
    }
    page<CreatePollRoute> { entry ->
        val route = entry.toRoute<CreatePollRoute>()
        CreatePollScreen(
            tripId = route.tripId,
            kind = PollKind.entries.firstOrNull { it.name == route.kind } ?: PollKind.CUSTOM,
            onBack = { navController.popBackStack() },
            // the new poll replaces the form, so back goes to the trip
            onCreated = { pollId -> navController.navigate(PollRoute(route.tripId, pollId, share = true)) { popUpTo<CreatePollRoute> { inclusive = true } } },
        )
    }
    page<JoinTripRoute> { entry ->
        val route = entry.toRoute<JoinTripRoute>()
        JoinTripScreen(
            code = route.code,
            onClose = { navController.popBackStack() },
            onSignIn = { navController.navigate(SignInRoute) },
            onJoined = { id ->
                navController.navigate(TripRoute(id)) { popUpTo<JoinTripRoute> { inclusive = true } }
            },
        )
    }
    page<MembersRoute> { entry ->
        val route = entry.toRoute<MembersRoute>()
        MembersScreen(
            tripId = route.tripId,
            claimedGuestId = route.claimedGuestId,
            claimedGuestName = route.claimedGuestName,
            claimerName = route.claimerName,
            openAdd = route.openAdd,
            onBack = { navController.popBackStack() },
            // The trip is no longer ours: back to the list, skipping the trip screen.
            onLeft = { navController.popBackStack<TripRoute>(inclusive = true) },
            // The owner has nobody to hand the trip to: the trip settings hold "Hapus trip".
            onDeleteTrip = { navController.navigate(EditTripRoute(route.tripId)) },
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
        val session: SessionManager = koinInject()
        TripFormScreen(
            tripId = null,
            onClose = { navController.popBackStack() },
            onSaved = { id ->
                // Replace the form with the new trip (on Ringkasan) so Back returns to where the user came from.
                navController.navigate(TripRoute(id)) { popUpTo<CreateTripRoute> { inclusive = true } }
                // Inviting needs an account: a guest's trip lives on this phone only.
                if (session.state.value is SessionState.SignedIn) navController.navigate(InviteFriendsRoute(id))
            },
            onDeleted = {},
        )
    }
    page<InviteFriendsRoute> { entry ->
        val route = entry.toRoute<InviteFriendsRoute>()
        InviteFriendsScreen(
            tripId = route.tripId,
            // "Nanti saja" and close: the trip underneath, on Ringkasan.
            onDone = { navController.popBackStack() },
            onAddByName = {
                navController.navigate(MembersRoute(route.tripId, openAdd = true)) { popUpTo<InviteFriendsRoute> { inclusive = true } }
            },
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
    page<EmailVerifiedRoute> { entry ->
        val session: SessionManager = koinInject()
        val state by session.state.collectAsState()
        fun leaveTo(route: Any) {
            clearResetTokenFromUrl()
            navController.navigate(route) { popUpTo<EmailVerifiedRoute> { inclusive = true } }
        }
        EmailVerifiedScreen(
            token = entry.toRoute<EmailVerifiedRoute>().token,
            signedIn = state is SessionState.SignedIn,
            onExplore = { leaveTo(ExploreRoute) },
            onHome = { leaveTo(HomeRoute) },
            onSignIn = { leaveTo(SignInRoute) },
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

/** Opens a bottom tab, keeping each tab's own back stack and scroll position. */
private fun NavHostController.openTab(tab: MainTab) {
    val route: Any = when (tab) {
        MainTab.HOME -> HomeRoute
        MainTab.TRIPS -> TripListRoute
        MainTab.EXPLORE -> ExploreRoute
        MainTab.ACCOUNT -> AccountRoute
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * One of the four tabs: the page with the bottom bar under it (phones; wide web windows use the rail instead).
 * The bar belongs to the tab pages only, so every other destination covers it while sliding in.
 */
private inline fun <reified T : Any> NavGraphBuilder.tabPage(
    tab: MainTab,
    navController: NavHostController,
    noinline content: @Composable () -> Unit,
) {
    composable<T> {
        val expanded = rememberIsExpanded()
        val exploreSessions by koinInject<ExploreSessionStore>().sessions.collectAsState()
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // The tab applies its own status-bar inset; the bar takes the navigation-bar inset.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!expanded) {
                    RanselBottomBar(selected = tab, onSelect = navController::openTab, exploreHasSession = exploreSessions.values.any { !it.finished })
                }
            },
        ) { padding ->
            // consumeWindowInsets: the bar's height is already out of the way, so a screen's imePadding() must not count it again.
            Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = PageMaxWidth).fillMaxSize()) { content() }
            }
        }
    }
}

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
