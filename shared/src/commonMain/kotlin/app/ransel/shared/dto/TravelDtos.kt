package app.ransel.shared.dto

import app.ransel.shared.domain.LegKind
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/**
 * How the trip is travelled between two itinerary items (Getting there sheet). One live row per
 * (fromItemId, toItemId). When the two items are no longer neighbours after a reorder, the app ignores
 * this row and the capsule falls back to Auto.
 */
@Serializable
data class TravelModeDto(
    val id: String,
    val tripId: String,
    val fromItemId: String,
    val toItemId: String,
    /** WALK, MOTORCYCLE, CAR or PUBLIC_TRANSPORT. Never FAR. */
    val mode: LegKind,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)

/** PUT /trips/{id}/travel-modes (create or replace the live row for this pair). */
@Serializable
data class TravelModeInput(
    val id: String,
    val fromItemId: String,
    val toItemId: String,
    val mode: LegKind,
)
