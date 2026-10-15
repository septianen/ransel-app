package app.ransel.navigation

import kotlinx.serialization.Serializable

/** The four bottom tabs (TRD "Client" → Navigation). Routes are named after the English code names. */
@Serializable data object HomeRoute
@Serializable data object TripListRoute
@Serializable data object ExploreRoute
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

/** Invite link or code opened: preview and join, or the invalid-invite state. */
@Serializable data class JoinTripRoute(val code: String)
