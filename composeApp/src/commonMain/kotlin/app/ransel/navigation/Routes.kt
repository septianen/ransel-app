package app.ransel.navigation

import kotlinx.serialization.Serializable

/** The four bottom tabs (TRD "Client" → Navigation). Routes are named after the English code names. */
@Serializable data object HomeRoute
@Serializable data object TripListRoute
@Serializable data object ExploreRoute
@Serializable data object AccountRoute

/** Debug only. */
@Serializable data object ComponentGalleryRoute
