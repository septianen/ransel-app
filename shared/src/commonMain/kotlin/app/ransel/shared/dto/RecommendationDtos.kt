package app.ransel.shared.dto

import app.ransel.shared.model.BudgetLevel
import app.ransel.shared.model.Category
import app.ransel.shared.model.FeedbackAction
import app.ransel.shared.model.Interest
import app.ransel.shared.model.TravelStyle
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Phase 5 — Explore AI. Signed-in users only; always tied to one trip.
 *
 * Swipe right (LIKE): the CLIENT creates the itinerary item (dayDate = null, source = AI,
 * placeId/lat/lng/durationMin from the card) through its normal path (local DB + sync on
 * mobile, POST itinerary on web), and sends FeedbackRequest(LIKE). The unique index
 * uq_itinerary_trip_place_ai stops duplicates.
 * Swipe left (SKIP): only FeedbackRequest(SKIP).
 * Undo: DELETE /recommendations/feedback/{tripId}/{placeId} (+ delete the item it created).
 */

/** POST /recommendations. 429 AI_QUOTA_EXCEEDED when the 10 daily searches are used up. */
@Serializable
data class RecommendationRequest(
    val tripId: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val budget: BudgetLevel,
    val interests: List<Interest>,
    val style: TravelStyle,
    val notes: String? = null,
    /** placeIds already liked or skipped in this session; the server also excludes stored feedback. */
    val excludePlaceIds: List<String> = emptyList(),
    /** false for the background "next batch" request (does not count against the quota). */
    val newSearch: Boolean = true,
)

@Serializable
data class RecommendationResponse(
    /** Empty = "Belum ada yang cocok" state. */
    val groups: List<RecommendationGroup>,
    val quota: QuotaDto,
)

@Serializable
data class RecommendationGroup(
    val category: Category,
    val items: List<RecommendationItem>,
)

@Serializable
data class RecommendationItem(
    val placeId: String,
    val name: String,
    /** Why it matches, shown in the AI reason box. */
    val reason: String,
    /** 0–4 like Google Places, null if unknown. */
    val priceLevel: Int? = null,
    val suggestedDurationMin: Int,
    val lat: Double,
    val lng: Double,
    /** Filled lazily for the top cards only (cost control). */
    val rating: Double? = null,
    val photoUrl: String? = null,
    val address: String? = null,
)

@Serializable
data class QuotaDto(
    val used: Int,
    val limit: Int,
    /** Next 00:00 WIB. */
    val resetsAt: Timestamp,
)

/** POST /recommendations/feedback → 204. Upsert on (tripId, user, placeId). */
@Serializable
data class FeedbackRequest(
    val tripId: String,
    val placeId: String,
    val placeName: String,
    val category: Category,
    val action: FeedbackAction,
)
