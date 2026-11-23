package app.ransel

import app.ransel.content.RemoteContentStore
import app.ransel.content.stringResource
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
import app.ransel.platform.devicePlatform
import app.ransel.platform.requiresAccount
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import app.ransel.feature.account.AccountScreen
import app.ransel.feature.inspiration.InspirationHomeScreen
import app.ransel.feature.inspiration.InspirationInterviewScreen
import app.ransel.feature.inspiration.InspirationHomeViewModel
import app.ransel.feature.inspiration.InspirationInterviewViewModel
import app.ransel.feature.inspiration.InspirationResultsScreen
import app.ransel.feature.inspiration.InspirationResultsViewModel
import app.ransel.feature.inspiration.SavedIdeasScreen
import app.ransel.feature.inspiration.SavedIdeasViewModel
import app.ransel.navigation.InspirationHomeRoute
import app.ransel.navigation.InspirationInterviewRoute
import app.ransel.navigation.InspirationResultsRoute
import app.ransel.navigation.InspirationSavedPlaceholderRoute
import app.ransel.navigation.SavedIdeasRoute
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
import app.ransel.feature.itinerary.ActivityDetailScreen
import app.ransel.feature.itinerary.ActivityFormScreen
import app.ransel.feature.home.TripSection
import app.ransel.feature.trip.TripScreen
import app.ransel.feature.tripform.TripFormScreen
import app.ransel.feature.trips.TripListScreen
import app.ransel.navigation.AccountRoute
import app.ransel.navigation.CreditsRoute
import app.ransel.navigation.ChangePasswordRoute
import app.ransel.navigation.DeleteAccountRoute
import app.ransel.navigation.PaymentMethodsRoute
import app.ransel.navigation.PaymentRoute
import app.ransel.navigation.ProfileSecurityRoute
import app.ransel.feature.account.PaymentScreen
import app.ransel.feature.account.ProfileSecurityScreen
import app.ransel.ui.motion.IrisMotion
import app.ransel.navigation.NotificationSettingsRoute
import app.ransel.navigation.NotificationsRoute
import app.ransel.feature.notifications.NotificationPermissionSheet
import app.ransel.feature.update.notificationSheetAllowed
import app.ransel.shared.model.AppUpdateStatus
import app.ransel.feature.notifications.NotificationSettingsScreen
import app.ransel.feature.notifications.NotificationsScreen
import app.ransel.data.notification.PushRegistrar
import app.ransel.feature.credits.CreditsScreen
import app.ransel.navigation.ActivityDetailRoute
import app.ransel.navigation.ActivityFormRoute
import app.ransel.navigation.ComponentGalleryRoute
import app.ransel.navigation.CreateTripRoute
import app.ransel.navigation.InterviewRoute
import app.ransel.navigation.InviteFriendsRoute
import app.ransel.feature.interview.InterviewScreen
import app.ransel.feature.interview.InterviewPrefill
import app.ransel.feature.invite.InviteFriendsScreen
import app.ransel.navigation.EditTripRoute
import app.ransel.navigation.TripAnswerRoute
import app.ransel.feature.tripform.travel.TripAnswerScreen
import app.ransel.feature.tripform.travel.TripTasteScreen
import app.ransel.shared.domain.TravelRow
import app.ransel.navigation.ExplorePreferencesRoute
import app.ransel.navigation.ExploreRoute
import app.ransel.navigation.ExploreTripPickerRoute
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
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.domain.hoursLeft
import app.ransel.shared.model.Vote
import kotlin.time.Clock
import app.ransel.feature.explore.ExploreTripPickerScreen
import app.ransel.feature.explore.ExploreAccountGate
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
import app.ransel.navigation.RecapRoute
import app.ransel.navigation.TripRoute
import app.ransel.navigation.TrackScreens
import app.ransel.platform.isDebugBuild
import app.ransel.ui.components.TripTab
import app.ransel.ui.gallery.ComponentGalleryScreen
import app.ransel.ui.shell.MainTab
import app.ransel.ui.shell.PlaceholderScreen
import app.ransel.ui.shell.RanselBottomBar
import app.ransel.ui.theme.IrisTheme
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
import ransel.composeapp.generated.resources.album_default_drive
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.debug_component_gallery
import ransel.composeapp.generated.resources.explore_results_back
import ransel.composeapp.generated.resources.explore_results_placeholder
import ransel.composeapp.generated.resources.explore_saved_placeholder
import ransel.composeapp.generated.resources.saved_ideas_title
import ransel.composeapp.generated.resources.debug_seed_sample
import ransel.composeapp.generated.resources.debug_show_onboarding
import ransel.composeapp.generated.resources.placeholder_account_title
import ransel.composeapp.generated.resources.placeholder_body
import ransel.composeapp.generated.resources.placeholder_explore_title

/**
 * What shows while the app loads: the logo splash on Android and iOS. The web app (PWA) has no splash of its own, the
 * browser's one (from the manifest) is enough, so it shows the plain background until the first screen is ready.
 */
@Composable
private fun AppSplash() {
    if (devicePlatform != null) SplashScreen() else Box(Modifier.fillMaxSize())
}

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
    // Read at app start, not when SessionGate first shows: otherwise finishing onboarding flashes the splash again.
    val sessionManager: SessionManager = koinInject()
    LaunchedEffect(sessionManager) { sessionManager.restore() }
    val preferencesRepository: PreferencesRepository = koinInject()
    val preferences by preferencesRepository.observe().collectAsState(initial = AppPreferences(loaded = false))
    val scope = rememberCoroutineScope()
    var openSignInAfterOnboarding by remember { mutableStateOf(false) }
    // Karung's published copy (stage E): the saved one at once, a newer one whenever the app comes to the front.
    val remoteContent: RemoteContentStore = koinInject()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { remoteContent.refresh() }

    // Apply the chosen language before any string is read; `key` rebuilds the UI when it changes.
    remember(preferences.language) { applyAppLanguage(preferences.language.tag) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // The key is inside the theme: rebuilding the theme when the saved language is read cancels the font loading on the
    // web, and the app then stays on the fallback font.
    IrisTheme(mode = preferences.themeMode) {
        SystemBarsAppearance(darkTheme = dark)
        key(preferences.language) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    !preferences.loaded -> AppSplash()
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
        state is SessionState.Loading -> AppSplash()
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
    // Android release only: full-screen gate below the minimum, or "Update tersedia" once per latest. Debug, iOS and web are not asked.
    var updateStatus by remember { mutableStateOf<AppUpdateStatus?>(null) }
    app.ransel.feature.update.AppUpdatePrompt(ready = state !is SessionState.Loading, onStatus = { updateStatus = it })
    // the tour waits while the permission explanation is still pending. The sheet itself is a dialog window,
    // so it is not composed over a required update.
    val tour: app.ransel.ui.tour.TourController = koinInject()
    LaunchedEffect(explainPermission) { tour.blocked = explainPermission }
    if (explainPermission && notificationSheetAllowed(updateStatus)) {
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
    BindBrowserHistory(navController, restoreFromUrl = true)
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
            val tab = notificationTarget.tab.value
            notificationTarget.clear()
            // A voting notification opens its round on top of the trip, so back goes to the trip.
            navController.navigate(if (tab != null) TripRoute(tripId, tab.name) else TripRoute(tripId)) { launchSingleTop = true }
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
    val dropped by sync.dropped.collectAsState()

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
                    onCreateTrip = { navController.navigate(InterviewRoute()) },
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
                    // Explore needs an account: a guest who taps "Coba" or a trip's Explore button signs in first
                    val signedIn = { sessionState is SessionState.SignedIn }
                    tabs(navController, signedIn)
                    trips(navController, signedIn)
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
        // after "Kembali online" rather than on top of it
        app.ransel.ui.components.DroppedChangesSnackbar(dropped.takeIf { justSynced == null }, onShown = sync::acknowledgeDropped, modifier = Modifier.align(Alignment.BottomCenter))

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
        // the new-user tour: over every screen and the rail
        app.ransel.ui.tour.TourOverlay()
    }
    }
}

private fun NavGraphBuilder.tabs(navController: NavHostController, signedIn: () -> Boolean) {
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
            onCreateTrip = { navController.navigate(InterviewRoute()) },
            onOpenNotifications = { navController.navigate(NotificationsRoute) },
            onOpenRecap = { navController.navigate(RecapRoute(it)) },
            // "Coba": the preferences step on a new trip ("Trip baru"), full screen over Home.
            onExplore = { navController.openExplore(signedIn()) },
        )
    }
    tabPage<TripListRoute>(MainTab.TRIPS, navController) {
        TripListScreen(
            modifier = Modifier.statusBarsPadding(),
            onOpenTrip = { navController.navigate(TripRoute(it)) },
            onCreateTrip = { navController.navigate(InterviewRoute()) },
        )
    }
    // The Explore tab opens Explore Inspirasi (owner, 21 Nov 2026); guests and unverified accounts see Explore's gates.
    tabPage<ExploreRoute>(MainTab.EXPLORE, navController) {
        val manager: SessionManager = koinInject()
        ExploreAccountGate(
            onSignIn = { navController.navigate(SignInRoute) },
            onSignUp = { navController.navigate(SignUpRoute) },
        ) {
            Box(Modifier.statusBarsPadding()) {
                InspirationHomeScreen(
                    viewModel = koinViewModel(),
                    onStartInterview = { id -> navController.navigate(InspirationInterviewRoute(id)) },
                    onOpenCredits = { navController.navigate(CreditsRoute()) },
                    onVerifyEmail = { navController.navigate(VerifyEmailRoute(manager.user?.email.orEmpty())) },
                    onOpenSavedIdeas = { navController.navigate(SavedIdeasRoute) },
                )
            }
        }
    }
    page<ExploreTripPickerRoute> {
        val exploreRequests: app.ransel.data.explore.TripExploreRequests = koinInject()
        ExploreTripPickerScreen(
            onSignIn = { navController.navigate(SignInRoute) },
            onSignUp = { navController.navigate(SignUpRoute) },
            onPickTrip = { navController.openTripExplore(it, exploreRequests) },
            // "Trip baru": the interview (Layar 1 to the summary "Buat trip dan cari"), not a form of its own
            onNewTrip = { navController.navigate(InterviewRoute(InterviewDoor.EXPLORE.name)) },
            onOpenSwipe = { tripId -> navController.navigate(SwipeRoute(tripId)) },
            onBack = { navController.popBackStack() },
        )
    }
    // Kept for links to the old debug entry: Explore Inspirasi lives in the Explore tab now.
    page<InspirationHomeRoute> {
        LaunchedEffect(Unit) {
            navController.popBackStack()
            navController.openTab(MainTab.EXPLORE)
        }
    }
    page<InspirationInterviewRoute> { entry ->
        val sessionId = entry.toRoute<InspirationInterviewRoute>().sessionId
        InspirationInterviewScreen(
            viewModel = koinViewModel(key = "inspiration-$sessionId") { org.koin.core.parameter.parametersOf(sessionId) },
            onResults = { navController.navigate(InspirationResultsRoute(sessionId)) },
            onOutOfCredits = { navController.navigate(CreditsRoute()) },
            onBack = { navController.popBackStack() },
        )
    }
    page<InspirationResultsRoute> { entry ->
        val sessionId = entry.toRoute<InspirationResultsRoute>().sessionId
        InspirationResultsScreen(
            viewModel = koinViewModel(key = "inspiration-results-$sessionId") { org.koin.core.parameter.parametersOf(sessionId) },
            onBack = { if (!navController.popBackStack<ExploreRoute>(inclusive = false)) navController.openTab(MainTab.EXPLORE) },
            onOpenSavedIdeas = { navController.navigate(SavedIdeasRoute) },
            onOpenCredits = { navController.navigate(CreditsRoute()) },
            // "Bikin trip dari sini": Buat trip with the card's region and the session's interests; dates stay open
            onCreateTrip = { card, interests -> navController.openTripFromIdea(card, interests) },
        )
    }
    page<InspirationSavedPlaceholderRoute> {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.saved_ideas_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.explore_saved_placeholder))
            Button(onClick = { navController.popBackStack() }) { Text(stringResource(Res.string.action_close)) }
        }
    }
    page<SavedIdeasRoute> {
        val savedSessionManager: SessionManager = koinInject()
        val savedSession by savedSessionManager.state.collectAsState()
        SavedIdeasScreen(
            viewModel = koinViewModel(),
            signedIn = savedSession is SessionState.SignedIn,
            onBack = { navController.popBackStack() },
            onSignIn = { navController.navigate(SignInRoute) },
            onSignUp = { navController.navigate(SignUpRoute) },
            onAskAi = { navController.openTab(MainTab.EXPLORE) },
            // the same hand-off as the results feed (plan idea-to-trip-flow v2)
            onCreateTrip = { idea ->
                navController.openTripFromIdea(idea.card, app.ransel.feature.inspiration.interestsFromProfile(idea.profile))
            },
        )
    }
    page<ExplorePreferencesRoute> { entry ->
        val route = entry.toRoute<ExplorePreferencesRoute>()
        if (route.tripId == null) {
            // the old "trip baru" form is gone: an old link or a stored route opens the interview of Jelajah instead
            LaunchedEffect(Unit) {
                navController.navigate(InterviewRoute(InterviewDoor.EXPLORE.name)) { popUpTo<ExplorePreferencesRoute> { inclusive = true } }
            }
        } else {
            // the trip form is gone too (plan explore-trip-one-tap v4): an old link opens the trip with its "Cari tempat" sheet
            val exploreRequests: app.ransel.data.explore.TripExploreRequests = koinInject()
            LaunchedEffect(Unit) { navController.openTripExplore(route.tripId, exploreRequests, replace = true) }
        }
    }
    page<SwipeRoute> { entry ->
        val swipeRoute = entry.toRoute<SwipeRoute>()
        val exploreRequests: app.ransel.data.explore.TripExploreRequests = koinInject()
        SwipeScreen(
            tripId = swipeRoute.tripId,
            onClose = { navController.popBackStack() },
            // Back to the trip's "Cari tempat" sheet, filled in with its Seleramu.
            onChangePreferences = { navController.openTripExplore(swipeRoute.tripId, exploreRequests, replace = true) },
            onOpenItinerary = { id ->
                navController.navigate(TripRoute(id, TripTab.ITINERARY.name)) { popUpTo<SwipeRoute> { inclusive = true } }
            },
            onOpenCredits = { navController.navigate(CreditsRoute(swipeRoute.tripId)) },
            created = swipeRoute.created,
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
    page<CreditsRoute> { entry ->
        val route = entry.toRoute<CreditsRoute>()
        CreditsScreen(
            tripId = route.tripId,
            onBack = { navController.popBackStack() },
            // "Lanjut mencari": the search that waited runs again from the session the interview kept
            onContinueSearch = { id -> navController.navigate(SwipeRoute(id)) { popUpTo<CreditsRoute> { inclusive = true } } },
        )
    }
    page<ProfileSecurityRoute>(emphasizedSlide = true) {
        ProfileSecurityScreen(
            onBack = { navController.popBackStack() },
            onChangePassword = { navController.navigate(ChangePasswordRoute) },
            onDeleteAccount = { navController.navigate(DeleteAccountRoute) },
            modifier = Modifier.statusBarsPadding(),
        )
    }
    page<PaymentRoute>(emphasizedSlide = true) {
        PaymentScreen(
            onBack = { navController.popBackStack() },
            onOpenCredits = { navController.navigate(CreditsRoute()) },
            onOpenPaymentMethods = { navController.navigate(PaymentMethodsRoute) },
            modifier = Modifier.statusBarsPadding(),
        )
    }
    page<DeleteAccountRoute> {
        app.ransel.feature.account.DeleteAccountScreen(onBack = { navController.popBackStack() })
    }
    page<ChangePasswordRoute> {
        app.ransel.feature.account.ChangePasswordScreen(
            onBack = { navController.popBackStack() },
            onChanged = {
                navController.getBackStackEntry<AccountRoute>().savedStateHandle["passwordChanged"] = true
                navController.popBackStack()
            },
        )
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
            onOpenTripTab = { tripId, tab -> navController.navigate(TripRoute(tripId, tab.name)) },
            onOpenDocuments = { navController.navigate(DocumentsRoute(it)) },
            onOpenRecap = { navController.navigate(RecapRoute(it)) },
        )
    }
    page<NotificationSettingsRoute> {
        NotificationSettingsScreen(onBack = { navController.popBackStack() })
    }
    tabPage<AccountRoute>(MainTab.ACCOUNT, navController) {
        val accountEntry = navController.getBackStackEntry<AccountRoute>()
        val passwordChanged by accountEntry.savedStateHandle.getStateFlow("passwordChanged", false).collectAsState()
        AccountScreen(
            modifier = Modifier.statusBarsPadding(),
            onOpenProfileSecurity = { navController.navigate(ProfileSecurityRoute) },
            onOpenPayment = { navController.navigate(PaymentRoute) },
            onOpenCredits = { navController.navigate(CreditsRoute()) },
            onOpenNotifications = { navController.navigate(NotificationSettingsRoute) },
            passwordChanged = passwordChanged,
            onPasswordChangedShown = { accountEntry.savedStateHandle["passwordChanged"] = false },
            onSignIn = { navController.navigate(SignInRoute) },
            onSignUp = { navController.navigate(SignUpRoute) },
            onReplayTour = { navController.openTab(MainTab.HOME) },
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

private fun NavGraphBuilder.trips(navController: NavHostController, signedIn: () -> Boolean) {
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
            onOpenSwipe = { navController.navigate(SwipeRoute(route.tripId)) },
            onAddActivity = { day -> navController.navigate(ActivityFormRoute(route.tripId, null, day)) },
            onOpenActivity = { id -> navController.navigate(ActivityDetailRoute(route.tripId, id)) },
            onFillLocation = { id -> navController.navigate(ActivityFormRoute(route.tripId, id, null, openPlace = true)) },
            onOpenRound = { roundId, showResult -> navController.navigate(VoteRoute(route.tripId, roundId, showResult)) },
            onOpenVotingHistory = { navController.navigate(app.ransel.navigation.VotingHistoryRoute(route.tripId)) },
            onCreatePoll = { kind -> navController.navigate(CreatePollRoute(route.tripId, kind.name)) },
            onOpenPoll = { pollId -> navController.navigate(PollRoute(route.tripId, pollId)) },
            onOpenPaymentMethods = { navController.navigate(PaymentMethodsRoute) },
            onOpenDocuments = { upload -> navController.navigate(DocumentsRoute(route.tripId, upload)) },
            onOpenAlbums = { add -> navController.navigate(app.ransel.navigation.AlbumsRoute(route.tripId, add)) },
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
        app.ransel.feature.recap.RecapScreen(tripId = route.tripId, onBack = { navController.popBackStack() })
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
        app.ransel.feature.documents.DocumentsScreen(
            tripId = route.tripId,
            startUpload = route.upload,
            onBack = { navController.popBackStack() },
            // "Buka pengeluaran" in the viewer of a receipt photo: the trip's Pengeluaran tab
            onOpenExpenses = { navController.navigate(TripRoute(route.tripId, tab = "EXPENSES")) { popUpTo<TripRoute> { inclusive = true } } },
        )
    }
    page<app.ransel.navigation.AlbumsRoute> { entry ->
        val route = entry.toRoute<app.ransel.navigation.AlbumsRoute>()
        app.ransel.feature.albums.AlbumsScreen(
            tripId = route.tripId,
            startAdd = route.add,
            onBack = { navController.popBackStack() },
            onOpenGallery = { album ->
                navController.navigate(
                    app.ransel.navigation.AlbumGalleryRoute(route.tripId, album.id, album.title.orEmpty(), album.addedByName, album.url),
                )
            },
        )
    }
    page<app.ransel.navigation.AlbumGalleryRoute> { entry ->
        val route = entry.toRoute<app.ransel.navigation.AlbumGalleryRoute>()
        app.ransel.feature.albums.AlbumGalleryScreen(
            source = app.ransel.feature.albums.AlbumSource(route.albumId, tripId = route.tripId),
            title = route.title.ifEmpty { stringResource(Res.string.album_default_drive) },
            addedByName = route.addedByName,
            url = route.url,
            onBack = { navController.popBackStack() },
        )
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
    page<ActivityDetailRoute> { entry ->
        val route = entry.toRoute<ActivityDetailRoute>()
        ActivityDetailScreen(
            tripId = route.tripId,
            itemId = route.itemId,
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(ActivityFormRoute(route.tripId, route.itemId, null)) },
        )
    }
    page<ActivityFormRoute> { entry ->
        val route = entry.toRoute<ActivityFormRoute>()
        ActivityFormScreen(
            tripId = route.tripId,
            itemId = route.itemId,
            initialDay = route.dayDate,
            openPlaceOnStart = route.openPlace,
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
    page<InterviewRoute> { entry ->
        val route = entry.toRoute<InterviewRoute>()
        val session: SessionManager = koinInject()
        InterviewScreen(
            door = InterviewDoor.valueOf(route.door),
            prefill = InterviewPrefill.fromRoute(route.destination, route.countryCode, route.interests, route.ideaActivity, route.ideaCard),
            onLeave = { navController.popBackStack() },
            // "Buat trip dan cari": the swipe screen takes over; Back from it leads to where Jelajah was opened
            onCreatedAndSearch = { id -> navController.navigate(SwipeRoute(id, created = true)) { popUpTo<InterviewRoute> { inclusive = true } } },
            onCreated = { id, signedIn ->
                // Replace the interview with the new trip (on Ringkasan) so Back returns to where the user came from.
                navController.navigate(TripRoute(id)) { popUpTo<InterviewRoute> { inclusive = true } }
                // Ajak teman is for everyone who is signed in (not only group trips); a guest's trip lives on this phone only.
                if (signedIn && session.state.value is SessionState.SignedIn) navController.navigate(InviteFriendsRoute(id))
            },
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
            onOpenTravel = { row -> navController.navigate(TripAnswerRoute(route.tripId, row.name)) },
        )
    }
    page<TripAnswerRoute> { entry ->
        val route = entry.toRoute<TripAnswerRoute>()
        val row = TravelRow.valueOf(route.row)
        if (row == TravelRow.TASTE) {
            TripTasteScreen(
                tripId = route.tripId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        } else {
            TripAnswerScreen(
                tripId = route.tripId,
                row = row,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                // an own vehicle was replaced: "Cara keliling" opens straight away, in place of this screen
                onSavedOpenLocalTransport = {
                    navController.navigate(TripAnswerRoute(route.tripId, TravelRow.LOCAL_TRANSPORT.name)) { popUpTo<TripAnswerRoute> { inclusive = true } }
                },
            )
        }
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
/** Jelajah's interview, or sign-in for a guest (Explore needs an account). A trip's search is its own "Cari tempat" sheet. */
private fun NavHostController.openExplore(signedIn: Boolean) {
    if (!signedIn) navigate(SignInRoute) { launchSingleTop = true }
    else navigate(InterviewRoute(InterviewDoor.EXPLORE.name))
}

/**
 * The trip's "Cari tempat" sheet (plan explore-trip-one-tap v4; the old form is gone): back to the trip screen below when it
 * is that trip's, else a new one, which opens the sheet once it shows the trip.
 */
private fun NavHostController.openTripExplore(tripId: String, requests: app.ransel.data.explore.TripExploreRequests, replace: Boolean = false) {
    requests.open(tripId)
    val below = previousBackStackEntry
    if (replace && below != null && below.destination.hasRoute<TripRoute>() && below.toRoute<TripRoute>().tripId == tripId) {
        popBackStack()
        return
    }
    val current = currentBackStackEntry
    navigate(TripRoute(tripId)) {
        if (replace && current != null) popUpTo(current.destination.id) { inclusive = true }
    }
}

/**
 * "Bikin trip dari sini" (results feed and Ide tersimpan): Buat trip with the idea's region, country and interests; dates stay
 * open. After "Buat trip" the idea's activity is one item in "Belum dijadwalkan". Nothing is charged.
 */
private fun NavHostController.openTripFromIdea(card: app.ransel.shared.dto.InspirationCardDto, interests: Set<app.ransel.shared.model.Interest>) {
    navigate(
        InterviewRoute(
            door = InterviewDoor.MY_TRIPS.name,
            destination = card.region,
            countryCode = card.countryCode,
            interests = interests.joinToString(",") { it.name }.ifEmpty { null },
            ideaActivity = card.activity,
            ideaCard = app.ransel.data.remote.ApiJson.encodeToString(app.ransel.shared.dto.InspirationCardDto.serializer(), card.copy(photoUrl = null)),
        ),
    )
}

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
        // Home is the start: nothing of its own to bring back. On the web, after a reload on another tab, what is saved
        // under it is that other tab, so restoring would keep the user there.
        restoreState = tab != MainTab.HOME
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
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // The tab applies its own status-bar inset; the bar takes the navigation-bar inset.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!expanded) {
                    RanselBottomBar(selected = tab, onSelect = navController::openTab)
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
    emphasizedSlide: Boolean = false,
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    val body: @Composable (NavBackStackEntry) -> Unit = { entry ->
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            // On wide web windows single-column pages (Home, lists, forms) stay a readable width.
            val width = if (maxContentWidth != null) Modifier.widthIn(max = maxContentWidth) else Modifier
            Box(width.fillMaxSize()) { content(entry) }
        }
    }
    if (emphasizedSlide) {
        composable<T>(
            enterTransition = { slideInHorizontally(IrisMotion.medium2()) { it } },
            exitTransition = { slideOutHorizontally(IrisMotion.medium2()) { -it / 4 } },
            popEnterTransition = { slideInHorizontally(IrisMotion.medium2()) { -it / 4 } },
            popExitTransition = { slideOutHorizontally(IrisMotion.medium2()) { it } },
        ) { body(it) }
    } else {
        composable<T> { body(it) }
    }
}

/** Single-column pages never get wider than this, however wide the window. */
private val PageMaxWidth = 720.dp
