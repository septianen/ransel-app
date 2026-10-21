package app.ransel.shared.dto

import app.ransel.shared.model.Category
import app.ransel.shared.model.FeedbackAction
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import app.ransel.shared.serialization.Timestamp
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

/**
 * POST /recommendations. Destination and dates come from the trip (spec: docs/specs/AI-Recommendation-Spec.md).
 * 402 INSUFFICIENT_CREDITS when the search (or next batch) costs more than the user has; 404 TRIP_NOT_FOUND; 403 FORBIDDEN for a VIEWER;
 * 409 ROUND_LIMIT when a multi-member trip already has 3 open rounds; 404 ROUND_NOT_FOUND / 409 VOTING_CLOSED for a next
 * batch whose [RecommendationRequest.roundId] is unknown or no longer open;
 * 422 VALIDATION_FAILED with details.tripId = TRIP_ENDED, details.interests = EMPTY (no core and no extra interest)
 * or details.extraInterests = TOO_MANY.
 */
@Serializable
data class RecommendationRequest(
    val tripId: String,
    /** Narrows one search to an area of the destination, at most 60 characters. Null = the whole destination. */
    val focusArea: String? = null,
    val priceLevel: PriceLevel,
    val interests: List<Interest>,
    /** The ticked destination interests, copied from [ExploreSuggestionsResponse.interests]: at most 5, each at most 40 characters. */
    val extraInterests: List<String> = emptyList(),
    val style: TravelStyle,
    val notes: String? = null,
    /** placeIds already liked or skipped in this session; the server also excludes stored feedback. */
    val excludePlaceIds: List<String> = emptyList(),
    /** false for the background "next batch" request (does not count against the quota). */
    val newSearch: Boolean = true,
    /**
     * Next batch of a voting round: the cards are appended to this round while it is open (only with `newSearch = false`).
     * Null on a new search; ignored on a solo trip.
     */
    val roundId: String? = null,
)

/**
 * GET /explore/suggestions?tripId= (existing trip, role at least VIEWER) or ?destination=&month=YYYY-MM (new trip).
 * Never uses the daily quota; soft limit 60 requests per user per hour (429 RATE_LIMITED). Language follows Accept-Language.
 * A failure is not an error: [interests] is empty and the app hides the "Khas <destination>" box.
 */
@Serializable
data class ExploreSuggestionsResponse(
    /** 3–5 destination interests, each at most 40 characters. Empty = hide the box. */
    val interests: List<String> = emptyList(),
    /** Only with tripId and a trip budget: the price level matching budget / days / members. */
    val suggestedPriceLevel: PriceLevel? = null,
    /** Budget per person per day in the trip's base currency, for the preselect note. Same condition as above. */
    val budgetPerPersonPerDayMinor: Long? = null,
    val currency: String? = null,
)

@Serializable
data class RecommendationResponse(
    /** Empty = "Belum ada yang cocok" state. */
    val groups: List<RecommendationGroup>,
    /** Balance after this request. */
    val credits: CreditsDto,
    /**
     * Set when the trip has two or more members: these cards are a voting round (`RoundDto`). Swipes then go to
     * PUT .../votes instead of creating itinerary items and feedback. Null on a solo trip (swipe saves as before).
     */
    val roundId: String? = null,
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
    /** Not filled any more: Google ratings are an Enterprise-tier field and cost too much. Always null; do not show a rating. */
    val rating: Double? = null,
    /** Signed path relative to `ApiRoutes.BASE` (see `ApiRoutes.placePhoto`); valid 7 days. The image may still 404. */
    val photoUrl: String? = null,
    /** Author credit Google requires next to the photo. Filled for the top cards only, like [rating]. */
    val photoAttribution: String? = null,
    val address: String? = null,
    // ── Place detail text, written by the LLM in the same call as the search (spec "Place detail").
    // All optional with defaults: stored Explore sessions and older servers keep working. Null/empty = not known; hide the block.
    /** Two or three sentences about the place, in the user's language. */
    val about: String? = null,
    /** Up to three short tips ("Perlu diketahui"). */
    val highlights: List<String> = emptyList(),
    /** Typical opening hours, an AI estimate (not Google data): show it labelled as such, next to a Google Maps link. */
    val openingHours: List<OpeningHoursLine> = emptyList(),
    /** Best time to go, one short phrase, for example "Pagi sebelum jam 9". */
    val bestTime: String? = null,
    /** Typical cost per person in [costCurrency]'s minor unit; null = free or unknown. */
    val estimatedCostMinor: Long? = null,
    /** The trip's base currency (the estimate is converted to it). */
    val costCurrency: String? = null,
)

/** One line of opening hours, for example label "Setiap hari" and hours "08.00 – 18.00". Text, not times: it is an estimate. */
@Serializable
data class OpeningHoursLine(
    val label: String,
    val hours: String,
)

/**
 * GET /places/{placeId}/detail: the saved detail text of one place, for opening it again from the itinerary.
 * Served from `place_detail_cache`; rebuilt by one small LLM call when the cached row expired.
 * The Google Maps link is built on the client with `googleMapsPlaceUrl`.
 */
@Serializable
data class PlaceDetailDto(
    val placeId: String,
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** Fresh signed path like [RecommendationItem.photoUrl]. */
    val photoUrl: String? = null,
    val photoAttribution: String? = null,
    val about: String? = null,
    val highlights: List<String> = emptyList(),
    val openingHours: List<OpeningHoursLine> = emptyList(),
    val bestTime: String? = null,
    val estimatedCostMinor: Long? = null,
    val costCurrency: String? = null,
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
