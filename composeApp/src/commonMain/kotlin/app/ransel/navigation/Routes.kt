package app.ransel.navigation

import kotlinx.serialization.Serializable

/** The four bottom tabs (TRD "Client" → Navigation). Routes are named after the English code names. */
@Serializable data object HomeRoute
@Serializable data object TripListRoute
@Serializable data object ExploreRoute
/** Explore step 2: the form for one trip; null = a new trip (destination and dates are asked in the form). */
@Serializable data class ExplorePreferencesRoute(val tripId: String? = null)
@Serializable data object AccountRoute

/** First-launch introduction (3 pages), shown once. */
@Serializable data object OnboardingRoute

/** Trip detail: header + Itinerary / Packing / Expenses tabs. [tab] is a `TripTab` name. */
@Serializable data class TripRoute(val tripId: String, val tab: String = "ITINERARY")
@Serializable data object CreateTripRoute
@Serializable data class EditTripRoute(val tripId: String)

/** Add (`itemId == null`) or edit an activity. [dayDate] is an ISO date preselecting the day. */
@Serializable data class ActivityFormRoute(val tripId: String, val itemId: String? = null, val dayDate: String? = null)

/** Debug only. */
@Serializable data object ComponentGalleryRoute

// Auth (Phase 3). Names follow docs/design/README.md "Code name".
@Serializable data object SignInRoute
@Serializable data object SignUpRoute
@Serializable data object ForgotPasswordRoute
@Serializable data class CheckEmailRoute(val email: String)
@Serializable data class NewPasswordRoute(val token: String)
@Serializable data object SessionExpiredRoute

// Members (Phase 4).
@Serializable data class MembersRoute(val tripId: String)

/** Swipe through the recommendations of the unfinished Explore session of [tripId] (a user can have one per trip). */
@Serializable data class SwipeRoute(val tripId: String)

/**
 * Full-screen detail of a recommended place. From a swipe card the card is read from the Explore session and the
 * Skip / Save buttons show. From the itinerary ([fromItinerary]) it is loaded by [placeId] and there are no buttons;
 * [name] (the item title) lets the server rebuild an expired text, [currency] is the trip's base currency.
 */
@Serializable data class PlaceDetailRoute(
    val placeId: String,
    /** The trip of the swipe session the card belongs to (null from the itinerary). */
    val tripId: String? = null,
    val fromItinerary: Boolean = false,
    val name: String? = null,
    val currency: String? = null,
)

/** Invite link or code opened: preview and join, or the invalid-invite state. */
@Serializable data class JoinTripRoute(val code: String)
