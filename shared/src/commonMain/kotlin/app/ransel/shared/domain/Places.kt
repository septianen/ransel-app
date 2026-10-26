package app.ransel.shared.domain

import app.ransel.shared.dto.MatchRange
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.PlaceSuggestionDto
import app.ransel.shared.dto.TripPlaceDto
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/* Destination search rules shared by the server and the apps (TRD "Places"). */

/** Suggestions need at least this many characters. */
const val PLACE_QUERY_MIN = 2

/** "Lokasi saat ini" is offered in Jelajah only this close to the trip destination. */
const val NEAR_DESTINATION_KM = 50.0

/**
 * Regions whose name matches [query] (case and spacing ignored): names starting with it first, then names containing
 * it, cities before regencies before provinces, shorter names first. Fewer than 2 characters finds nothing.
 */
fun searchRegions(query: String, limit: Int = 8): List<Region> {
    val q = normalizeSearch(query)
    if (q.length < PLACE_QUERY_MIN) return emptyList()
    return INDONESIAN_REGIONS.asSequence()
        .map { it to normalizeSearch(it.name) }
        .filter { (_, name) -> q in name }
        .sortedWith(
            compareBy<Pair<Region, String>> { (_, name) -> if (name.startsWith(q)) 0 else 1 }
                .thenBy { (r, _) -> r.kind.ordinal.let { if (r.kind == RegionKind.PROVINCE) 3 else it } }
                .thenBy { (_, name) -> name.length },
        )
        .map { it.first }
        .take(limit)
        .toList()
}

/** Lower case, letters and digits only, single spaces: "  Labuan-Bajo " -> "labuan bajo". */
fun normalizeSearch(text: String): String =
    text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }.joinToString(" ")

/** Where [query] occurs in [name] (case ignored), for drawing the match bold. Empty = no match. */
fun matchRangesOf(name: String, query: String): List<MatchRange> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val at = name.indexOf(q, ignoreCase = true)
    return if (at < 0) emptyList() else listOf(MatchRange(at, at + q.length))
}

/** Suggestions from the built-in list, for the server fallback and the apps offline. */
fun regionSuggestions(query: String, limit: Int = 8): List<PlaceSuggestionDto> =
    searchRegions(query, limit).map { r ->
        PlaceSuggestionDto(
            placeId = null,
            name = r.name,
            secondary = r.province,
            kind = when (r.kind) {
                RegionKind.PROVINCE -> PlaceKind.PROVINCE
                RegionKind.CITY -> PlaceKind.CITY
                RegionKind.REGENCY -> PlaceKind.REGENCY
            },
            matchRanges = matchRangesOf(r.name, query),
        )
    }

/** Within [km] kilometres (great-circle) — "Lokasi saat ini" is only offered near the destination. */
fun withinKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double, km: Double): Boolean {
    val r = 6371.0
    val dLat = (lat2 - lat1) * PI / 180
    val dLng = (lng2 - lng1) * PI / 180
    val a = sin(dLat / 2).let { it * it } +
        cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLng / 2).let { it * it }
    return 2 * r * asin(sqrt(a)) <= km
}

/** Validation of a picked place on a trip. Field name → reason. */
fun validateTripPlace(place: TripPlaceDto?): Map<String, String> = buildMap {
    if (place == null) return@buildMap
    if (place.placeId.isBlank() || place.placeId.length > 300) put("destinationPlace", "INVALID_PLACE_ID")
    if (place.lat !in -90.0..90.0 || place.lng !in -180.0..180.0) put("destinationPlace", "INVALID_COORDINATES")
}
