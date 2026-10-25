package app.ransel.shared.dto

import app.ransel.shared.model.PollPriceUnit
import kotlinx.serialization.Serializable

/*
 * Ways to fill the options of a poll besides typing them (PRD "Adding options"): a pasted link, and Jelajah (the AI
 * search of Explore, narrowed to one kind of place). The results of either become ordinary options.
 */

/** POST /link-preview. Signed-in users; 30 requests a minute. */
@Serializable
data class LinkPreviewRequest(val url: String)

/**
 * What the page says about itself (Open Graph and schema.org). The price is never read: the user types it.
 * 422 LINK_UNREADABLE when the link is not a public web page, is too big or slow, or says nothing useful.
 */
@Serializable
data class LinkPreviewDto(
    /** The address after redirects, without the fragment. */
    val url: String,
    val title: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    /** From schema.org Hotel, LodgingBusiness, Restaurant and similar. */
    val address: String? = null,
    val ratingValue: Double? = null,
    val ratingCount: Int? = null,
)

/**
 * POST /polls/{id}/search (OWNER or EDITOR, STAY and FOOD polls that are open): Jelajah for the poll. The category is fixed
 * by the poll, the dates and the number of people come from the trip. Costs the credits of a new Explore search.
 * 402 INSUFFICIENT_CREDITS, 403 EMAIL_NOT_VERIFIED, 503 AI_UNAVAILABLE, 409 POLL_CLOSED, 422 for other kinds of poll.
 */
@Serializable
data class PollSearchRequest(
    /** Where to look, at most 200 characters; null = the destination of the trip. */
    val area: String? = null,
    /** STAY: per night. FOOD: per person. In the trip currency, minor units. Both null = no budget. */
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
    /** Free text such as "kolam renang, dekat pantai", at most 200 characters; treated as preferences, not instructions. */
    val needs: String? = null,
)

@Serializable
data class PollSearchResult(
    val placeId: String,
    val name: String,
    val reason: String,
    val address: String? = null,
    /** The AI estimate (Google gives no prices), per night or per person; null when unknown. Show it as an estimate. */
    val priceMinor: Long? = null,
    val priceUnit: PollPriceUnit? = null,
)

@Serializable
data class PollSearchResponse(
    /** Empty = nothing matched. Places already among the options are left out. */
    val results: List<PollSearchResult>,
    /** Balance after this search. */
    val credits: CreditsDto,
)

/** POST /polls/{id}/options/bulk → 201 PollDto: several options at once (the ticked Jelajah results). */
@Serializable
data class BulkPollOptionsRequest(val options: List<PollOptionInput>)
