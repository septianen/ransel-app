package app.ransel.shared.dto

import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/*
 * A trip's Seleramu on the server (plan explore-trip-one-tap, owner 22 Nov 2026): the answers of the "Ubah seleramu" sheet,
 * kept with the trip so "Cari tempat" starts prefilled on every device and for every member. Server-only like travel modes:
 * not part of TripDto, not synced through /sync, not in the phone's database (searching needs a connection anyway).
 */

/** The Seleramu of one trip. Every field optional: an empty taste is valid (nothing chosen yet). */
@Serializable
data class TripTasteDto(
    /** Core interests ("Umum" chips). */
    val interests: List<Interest> = emptyList(),
    /** Ticked "Khas <destination>" chips: at most 5, each at most 40 characters (the same limits as `RecommendationRequest`). */
    val extraInterests: List<String> = emptyList(),
    /** Null = not chosen. */
    val style: TravelStyle? = null,
    /** Null = not chosen (the search then uses the budget, else MEDIUM). */
    val priceLevel: PriceLevel? = null,
    /** At most 500 characters (`TASTE_NOTES_MAX`). */
    val notes: String? = null,
)

/**
 * GET /trips/{tripId}/taste. [taste] is null when nobody has saved a Seleramu for this trip yet (a trip made before
 * 22 Nov 2026, or on a device that kept it locally): the sheet then opens empty.
 */
@Serializable
data class TripTasteResponse(
    val taste: TripTasteDto? = null,
    /** Last save; null with [taste]. */
    val updatedAt: Timestamp? = null,
)
