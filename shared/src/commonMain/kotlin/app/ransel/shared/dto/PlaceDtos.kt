package app.ransel.shared.dto

import kotlinx.serialization.Serializable

/*
 * Destination search (PRD "Destination suggestions and current location", TRD "Places"). Every lookup goes through the
 * server so the Google key never reaches the apps. Coordinates from "Gunakan lokasi saat ini" are sent once to
 * /places/reverse and never stored; a picked destination's own coordinates are stored on the trip.
 */

/** What a suggestion is, for its icon and label (kota, kabupaten, provinsi, negara, tempat wisata). */
@Serializable
enum class PlaceKind { CITY, REGENCY, PROVINCE, COUNTRY, ATTRACTION, AREA }

/** A matched part of [PlaceSuggestionDto.name], drawn bold: [start] inclusive, [end] exclusive. */
@Serializable
data class MatchRange(val start: Int, val end: Int)

@Serializable
data class PlaceSuggestionDto(
    /** Google place id; null for a suggestion from the built-in region list (no coordinates, the name is used as typed). */
    val placeId: String? = null,
    val name: String,
    /** "Manggarai Barat, Nusa Tenggara Timur". */
    val secondary: String? = null,
    val kind: PlaceKind,
    val matchRanges: List<MatchRange> = emptyList(),
)

/** GET /places/autocomplete. [fallback] = Google was unavailable and these come from the built-in region list. */
@Serializable
data class PlaceSuggestionsDto(
    val suggestions: List<PlaceSuggestionDto>,
    val fallback: Boolean = false,
)

@Serializable
data class ViewportDto(val lowLat: Double, val lowLng: Double, val highLat: Double, val highLng: Double)

/** GET /places/{placeId}: where the place is. Billed as Place Details Essentials (no name: it comes from the suggestion). */
@Serializable
data class PlaceDto(
    val placeId: String,
    val lat: Double,
    val lng: Double,
    val viewport: ViewportDto? = null,
    /** ISO 3166-1 alpha-2 from the address, e.g. "ID", "JP"; null when Google gives none. */
    val countryCode: String? = null,
    /** IANA zone at the place ("Asia/Makassar" for Bali); null when the lookup failed. */
    val timeZone: String? = null,
)

/** One level offered for the current location: "Kemang, Jakarta Selatan", "Jakarta Selatan", "Sekitar Jakarta". */
@Serializable
data class NearbyAreaDto(
    val placeId: String? = null,
    val name: String,
    val secondary: String? = null,
    val kind: PlaceKind,
    /** "Sekitar <name>": the whole area around [name] (the app writes the words in its own language). */
    val around: Boolean = false,
)

/** GET /places/reverse: 2–3 levels from the most precise to the widest. */
@Serializable
data class ReverseGeocodeDto(val areas: List<NearbyAreaDto>)

/** GET /places/popular: "Populer untuk trip grup", only places chosen by enough different groups. */
@Serializable
data class PopularDestinationDto(val placeId: String, val name: String)

/** The picked destination stored on a trip; [TripDto.destination] keeps its name. */
@Serializable
data class TripPlaceDto(
    val placeId: String,
    val lat: Double,
    val lng: Double,
    val viewport: ViewportDto? = null,
)
