package app.ransel.server.ai

import app.ransel.shared.model.Category
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import kotlinx.serialization.Serializable

/*
 * The two outside services behind Explore AI, as interfaces: the pipeline (RecommendationService) is built and
 * tested against fakes, and the real HTTP clients only have to honour these shapes.
 */

/** What the LLM is asked for. */
data class LlmQuery(
    val destination: String,
    /** Narrows the search to an area of the destination. */
    val focusArea: String?,
    val days: Int,
    /** First day of the trip, as `YYYY-MM-DD`: the prompt names the month so seasonal places fit. */
    val startDate: String,
    val priceLevel: PriceLevel,
    val interests: List<Interest>,
    /** Destination interests the user ticked ("Sakura", "Diving"). */
    val extraInterests: List<String>,
    val style: TravelStyle,
    val notes: String?,
    /** Names the user liked / skipped in this trip, as a taste signal. */
    val liked: List<String>,
    val skipped: List<String>,
    val count: Int,
)

/** One suggestion from the LLM. Nothing here is trusted until a place with that name is found. */
@Serializable
data class LlmCandidate(
    val name: String,
    val category: Category = Category.ATTRACTION,
    val reason: String = "",
    val priceLevel: Int? = null,
    val suggestedDurationMin: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** Destination interests for the Explore form plus a price guide, as asked from the LLM. */
data class InterestSuggestions(
    val interests: List<String>,
    /** Typical spend per person per day (USD) at the edge between `$` and `$$`. */
    val lowMaxUsd: Double,
    /** Same, between `$$` and `$$$`. */
    val midMaxUsd: Double,
)

interface LlmClient {
    /** Stored next to cached results. */
    val modelName: String

    /** Returns the parsed suggestions. Throws on transport errors or unparseable output (the caller retries once). */
    suspend fun suggest(query: LlmQuery): List<LlmCandidate>

    /** Short call for the Explore form. [language] is `id` or `en`; [month] is `YYYY-MM`. Throws on failure. */
    suspend fun suggestInterests(destination: String, month: String, language: String): InterestSuggestions
}

data class PlaceDetails(
    val placeId: String,
    val lat: Double,
    val lng: Double,
    val address: String?,
    val rating: Double?,
    /** Google priceLevel 0–4 (FREE = 0 … VERY_EXPENSIVE = 4); null when Google has no price for the place. */
    val priceLevel: Int? = null,
    /** Author credit of the first photo; only filled together with [rating]. */
    val photoAttribution: String? = null,
)

/** Image bytes of a place photo, as Google serves them. */
class PlacePhoto(val bytes: ByteArray, val contentType: String)

/** Google refused the photo request for quota or permission reasons (429/403); the proxy answers 404 and counts it. */
class PlacesQuotaException(status: Int) : RuntimeException("Places photo HTTP $status")

interface PlacesClient {
    /** Text Search (IDs Only: free). Null = no such place, which drops the candidate. */
    suspend fun findPlaceId(name: String, destination: String, focusArea: String? = null): String?

    /** Place Details with a minimal field mask. [withRating] raises the billing tier, so only ask for the top cards. */
    suspend fun details(placeId: String, withRating: Boolean): PlaceDetails?

    /** First photo of the place, null if it has none. Throws [PlacesQuotaException] when Google rejects for quota. */
    suspend fun photo(placeId: String): PlacePhoto?
}

/** A validated suggestion as kept in `recommendation_cache`. No photo, no rating: those are fetched when shown. */
@Serializable
data class CachedPlace(
    val placeId: String,
    val name: String,
    val category: Category,
    val reason: String,
    val priceLevel: Int? = null,
    /** Google's price level (0–4) when it had one; the price filter uses this, never the LLM's guess. */
    val placesPriceLevel: Int? = null,
    val suggestedDurationMin: Int,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
)

class AiServices(val llm: LlmClient, val places: PlacesClient)
