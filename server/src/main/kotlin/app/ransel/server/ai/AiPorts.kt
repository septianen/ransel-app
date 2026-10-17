package app.ransel.server.ai

import app.ransel.shared.dto.OpeningHoursLine
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
    /** Local price edges for this destination and month; null = unknown, the prompt then has no numbers. */
    val priceGuide: PriceGuide?,
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
    /** Typical cost per person in USD (a meal, a ticket); null for free places. The price filter compares it with [PriceGuide]. */
    val costUsd: Double? = null,
    val suggestedDurationMin: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    // Detail text, written in the same call (docs/specs/voting-spec.md "Place detail"). Missing or unsure = null/empty.
    val about: String? = null,
    val highlights: List<String> = emptyList(),
    val openingHours: List<OpeningHoursLine> = emptyList(),
    val bestTime: String? = null,
)

/** Detail text of one place asked on its own (the itinerary reopened a place whose cached text expired). */
@Serializable
data class LlmPlaceText(
    val about: String? = null,
    val highlights: List<String> = emptyList(),
    val openingHours: List<OpeningHoursLine> = emptyList(),
    val bestTime: String? = null,
    val costUsd: Double? = null,
)

/** Spend per person in USD at the edge between `$` and `$$` ([lowMaxUsd]) and between `$$` and `$$$` ([midMaxUsd]). */
data class PriceGuide(val lowMaxUsd: Double, val midMaxUsd: Double)

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

    /** Detail text for one known place (name and address come from Google or our cache). Default: not supported. */
    suspend fun describe(name: String, address: String?): LlmPlaceText =
        throw UnsupportedOperationException("describe is not supported")
}

data class PlaceDetails(
    val placeId: String,
    val lat: Double,
    val lng: Double,
    val address: String?,
    /** Author credit of the first photo; only filled when asked for. */
    val photoAttribution: String? = null,
)

/** Image bytes of a place photo, as Google serves them. */
class PlacePhoto(val bytes: ByteArray, val contentType: String)

/** Google refused the photo request for quota or permission reasons (429/403); the proxy answers 404 and counts it. */
class PlacesQuotaException(status: Int) : RuntimeException("Places photo HTTP $status")

interface PlacesClient {
    /** Text Search (IDs Only: free). Null = no such place, which drops the candidate. */
    suspend fun findPlaceId(name: String, destination: String, focusArea: String? = null): String?

    /**
     * Place Details with a minimal field mask, Essentials tier only (no rating or price: those are Enterprise).
     * [withPhotoCredit] adds the photo author, which is for the cards the user actually sees.
     */
    suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails?

    /** First photo of the place, null if it has none. Throws [PlacesQuotaException] when Google rejects for quota. */
    suspend fun photo(placeId: String): PlacePhoto?
}

/** A validated suggestion as kept in `recommendation_cache`. No photo: it is fetched when shown. */
@Serializable
data class CachedPlace(
    val placeId: String,
    val name: String,
    val category: Category,
    val reason: String,
    val priceLevel: Int? = null,
    /** The LLM's cost estimate in USD per person, kept so the price filter can run on cached entries. */
    val costUsd: Double? = null,
    val suggestedDurationMin: Int,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val about: String? = null,
    val highlights: List<String> = emptyList(),
    val openingHours: List<OpeningHoursLine> = emptyList(),
    val bestTime: String? = null,
)

/** What `place_detail_cache` keeps per place, so the itinerary can open it again later. */
@Serializable
data class CachedDetail(
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val about: String? = null,
    val highlights: List<String> = emptyList(),
    val openingHours: List<OpeningHoursLine> = emptyList(),
    val bestTime: String? = null,
    val costUsd: Double? = null,
)

class AiServices(val llm: LlmClient, val places: PlacesClient)
