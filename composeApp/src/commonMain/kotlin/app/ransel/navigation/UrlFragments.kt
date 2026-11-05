package app.ransel.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute

/*
 * Web addresses of the screens (owner, 3 Nov 2026): `#/home`, `#/trips/<id>/itinerary`, `#/account/credits`, ...
 * instead of the route class names Navigation writes by default (`#app.ransel.navigation.SignInRoute`). Emails,
 * reset and verification tokens and other screen state never go into the address. A reload opens the screen again
 * when [routeForUrlFragment] knows it; other screens (forms, sign-in steps, swipe and vote) start over on Home.
 */

/** The address of the screen [entry] shows, with its `#`; "#/" for a destination this list does not know. */
fun urlFragmentFor(entry: NavBackStackEntry): String = routeOf(entry)?.let(::urlFragmentForRoute) ?: "#/"

/** The address of [route]; "#/" for a route this list does not know. */
fun urlFragmentForRoute(route: Any): String = "#/" + when (route) {
    HomeRoute -> "home"
    TripListRoute -> "trips"
    ExploreRoute -> "explore"
    is ExplorePreferencesRoute -> route.tripId?.let { "trips/${enc(it)}/explore/new" } ?: "explore/new"
    AccountRoute -> "account"
    OnboardingRoute -> "welcome"
    is TripRoute -> "trips/${enc(route.tripId)}" + tabPath(route.tab)
    CreateTripRoute -> "trips/new"
    is InviteFriendsRoute -> "trips/${enc(route.tripId)}/invite"
    is PollRoute -> "trips/${enc(route.tripId)}/polls/${enc(route.pollId)}"
    is CreatePollRoute -> "trips/${enc(route.tripId)}/polls/new"
    is DocumentsRoute -> "trips/${enc(route.tripId)}/documents"
    is AlbumsRoute -> "trips/${enc(route.tripId)}/albums"
    is AlbumGalleryRoute -> "trips/${enc(route.tripId)}/albums/${enc(route.albumId)}"
    is EditTripRoute -> "trips/${enc(route.tripId)}/edit"
    is RecapRoute -> "trips/${enc(route.tripId)}/recap"
    is ActivityFormRoute -> "trips/${enc(route.tripId)}/activity"
    is MembersRoute -> "trips/${enc(route.tripId)}/members"
    is SwipeRoute -> "trips/${enc(route.tripId)}/explore"
    is VoteRoute -> "trips/${enc(route.tripId)}/votes/${enc(route.roundId)}"
    is VotingHistoryRoute -> "trips/${enc(route.tripId)}/votes"
    is AllTransactionsRoute -> "trips/${enc(route.tripId)}/transactions"
    is PlaceDetailRoute -> "place"
    CreditsRoute -> "account/credits"
    DeleteAccountRoute -> "account/delete"
    ChangePasswordRoute -> "account/password"
    PaymentMethodsRoute -> "account/payment-methods"
    NotificationsRoute -> "notifications"
    NotificationSettingsRoute -> "notifications/settings"
    ComponentGalleryRoute -> "gallery"
    SignInRoute -> "sign-in"
    SignUpRoute -> "sign-up"
    ForgotPasswordRoute -> "forgot-password"
    is CheckEmailRoute -> "check-email"
    is NewPasswordRoute -> "new-password"
    SessionExpiredRoute -> "session-expired"
    is VerifyEmailRoute -> "verify-email"
    is EmailVerifiedRoute -> "email-verified"
    is JoinTripRoute -> "join"
    else -> ""
}

/**
 * The screen to open again for an address after a reload (signed in only), or null: only screens that need nothing
 * but ids. "#app.ransel..." addresses from before 3 Nov 2026 are not read.
 */
fun routeForUrlFragment(fragment: String): Any? {
    val parts = fragment.removePrefix("#").removePrefix("/").substringBefore('?').split('/').filter { it.isNotEmpty() }.map(::dec)
    return when {
        parts == listOf("home") -> HomeRoute
        parts == listOf("trips") -> TripListRoute
        parts == listOf("explore") -> ExploreRoute
        parts == listOf("account") -> AccountRoute
        parts == listOf("account", "credits") -> CreditsRoute
        parts == listOf("account", "payment-methods") -> PaymentMethodsRoute
        parts == listOf("account", "password") -> ChangePasswordRoute
        parts == listOf("notifications") -> NotificationsRoute
        parts == listOf("notifications", "settings") -> NotificationSettingsRoute
        parts.size >= 2 && parts[0] == "trips" && parts[1] != "new" -> tripRoute(parts[1], parts.drop(2))
        else -> null
    }
}

private fun tripRoute(id: String, rest: List<String>): Any? = when {
    rest.isEmpty() -> TripRoute(id)
    rest.size == 1 && rest[0] in TAB_PATHS -> TripRoute(id, rest[0].uppercase())
    rest == listOf("members") -> MembersRoute(id)
    rest == listOf("documents") -> DocumentsRoute(id)
    rest == listOf("albums") -> AlbumsRoute(id)
    rest == listOf("recap") -> RecapRoute(id)
    rest == listOf("votes") -> VotingHistoryRoute(id)
    rest == listOf("transactions") -> AllTransactionsRoute(id)
    rest == listOf("invite") -> InviteFriendsRoute(id)
    rest == listOf("edit") -> EditTripRoute(id)
    rest.size == 2 && rest[0] == "polls" && rest[1] != "new" -> PollRoute(id, rest[1])
    else -> null
}

/** The trip tabs below Ringkasan (OVERVIEW is the trip's own address). */
private val TAB_PATHS = setOf("itinerary", "packing", "expenses")

private fun tabPath(tab: String): String = tab.lowercase().takeIf { it in TAB_PATHS }?.let { "/$it" }.orEmpty()

/** Ids are UUIDs, but a path segment must never carry a "/" or "#". */
private fun enc(value: String): String = buildString {
    value.forEach { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') append(c) else append('%').append(c.code.toString(16).padStart(2, '0').uppercase()) }
}

private fun dec(value: String): String =
    Regex("%([0-9A-Fa-f]{2})").replace(value) { it.groupValues[1].toInt(16).toChar().toString() }

/** The route object of [entry], for the routes [urlFragmentForRoute] knows. */
private fun routeOf(entry: NavBackStackEntry): Any? {
    val d = entry.destination
    return when {
        d.hasRoute(HomeRoute::class) -> HomeRoute
        d.hasRoute(TripListRoute::class) -> TripListRoute
        d.hasRoute(ExploreRoute::class) -> ExploreRoute
        d.hasRoute(ExplorePreferencesRoute::class) -> entry.toRoute<ExplorePreferencesRoute>()
        d.hasRoute(AccountRoute::class) -> AccountRoute
        d.hasRoute(OnboardingRoute::class) -> OnboardingRoute
        d.hasRoute(TripRoute::class) -> entry.toRoute<TripRoute>()
        d.hasRoute(CreateTripRoute::class) -> CreateTripRoute
        d.hasRoute(InviteFriendsRoute::class) -> entry.toRoute<InviteFriendsRoute>()
        d.hasRoute(PollRoute::class) -> entry.toRoute<PollRoute>()
        d.hasRoute(CreatePollRoute::class) -> entry.toRoute<CreatePollRoute>()
        d.hasRoute(DocumentsRoute::class) -> entry.toRoute<DocumentsRoute>()
        d.hasRoute(AlbumsRoute::class) -> entry.toRoute<AlbumsRoute>()
        d.hasRoute(AlbumGalleryRoute::class) -> entry.toRoute<AlbumGalleryRoute>()
        d.hasRoute(EditTripRoute::class) -> entry.toRoute<EditTripRoute>()
        d.hasRoute(RecapRoute::class) -> entry.toRoute<RecapRoute>()
        d.hasRoute(ActivityFormRoute::class) -> entry.toRoute<ActivityFormRoute>()
        d.hasRoute(MembersRoute::class) -> entry.toRoute<MembersRoute>()
        d.hasRoute(SwipeRoute::class) -> entry.toRoute<SwipeRoute>()
        d.hasRoute(VoteRoute::class) -> entry.toRoute<VoteRoute>()
        d.hasRoute(VotingHistoryRoute::class) -> entry.toRoute<VotingHistoryRoute>()
        d.hasRoute(AllTransactionsRoute::class) -> entry.toRoute<AllTransactionsRoute>()
        d.hasRoute(PlaceDetailRoute::class) -> PlaceDetailRoute("")
        d.hasRoute(CreditsRoute::class) -> CreditsRoute
        d.hasRoute(DeleteAccountRoute::class) -> DeleteAccountRoute
        d.hasRoute(ChangePasswordRoute::class) -> ChangePasswordRoute
        d.hasRoute(PaymentMethodsRoute::class) -> PaymentMethodsRoute
        d.hasRoute(NotificationsRoute::class) -> NotificationsRoute
        d.hasRoute(NotificationSettingsRoute::class) -> NotificationSettingsRoute
        d.hasRoute(ComponentGalleryRoute::class) -> ComponentGalleryRoute
        d.hasRoute(SignInRoute::class) -> SignInRoute
        d.hasRoute(SignUpRoute::class) -> SignUpRoute
        d.hasRoute(ForgotPasswordRoute::class) -> ForgotPasswordRoute
        d.hasRoute(CheckEmailRoute::class) -> CheckEmailRoute("")
        d.hasRoute(NewPasswordRoute::class) -> NewPasswordRoute("")
        d.hasRoute(SessionExpiredRoute::class) -> SessionExpiredRoute
        d.hasRoute(VerifyEmailRoute::class) -> VerifyEmailRoute("")
        d.hasRoute(EmailVerifiedRoute::class) -> EmailVerifiedRoute("")
        d.hasRoute(JoinTripRoute::class) -> JoinTripRoute("")
        else -> null
    }
}
