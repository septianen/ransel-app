package app.ransel.shared.domain

import app.ransel.shared.model.MainTravelMode
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * Google Maps URLs that open a route (plan "Perjalanan antar tempat", "Buka rute di Google Maps"). No API key, no billing,
 * no turn-by-turn: RANSEL only hands Maps a start, an end, some stops and a travel mode.
 * https://developers.google.com/maps/documentation/urls/get-started (read 16 Nov 2026):
 *  - `travelmode` is driving, walking, bicycling, two-wheeler or transit;
 *  - `waypoints` are separated by `|`, up to nine, but only three on a mobile browser (the Maps app takes nine);
 *  - `origin_place_id` and friends need a matching `origin`, so coordinates alone are used here;
 *  - a URL may not be longer than 2,048 characters;
 *  - two-wheeler works only where Google supports it (Indonesia is on the list, checked 16 Nov 2026).
 * `dir_action=navigate` is left out on purpose: it would start turn-by-turn navigation, a PRD non-goal.
 */

/** The nine waypoints Google Maps takes on the app and on desktop. */
const val MAPS_MAX_WAYPOINTS = 9

/** The three waypoints a mobile browser takes (web on a phone, or no Maps app installed). */
const val MAPS_MAX_WAYPOINTS_MOBILE_BROWSER = 3

/** Google's limit on a Maps URL. */
const val MAPS_MAX_URL_LENGTH = 2048

enum class MapsTravelMode(val parameter: String) {
    DRIVING("driving"),
    WALKING("walking"),
    BICYCLING("bicycling"),
    TWO_WHEELER("two-wheeler"),
    TRANSIT("transit"),
}

data class MapsPoint(val lat: Double, val lng: Double)

/** The Maps mode of the trip's main mode: Motor = two-wheeler, Mobil = driving, public transport = transit. */
fun MainTravelMode.mapsMode(): MapsTravelMode = when (this) {
    MainTravelMode.MOTORCYCLE -> MapsTravelMode.TWO_WHEELER
    MainTravelMode.CAR -> MapsTravelMode.DRIVING
    MainTravelMode.PUBLIC_TRANSPORT -> MapsTravelMode.TRANSIT
}

/** The Maps mode that matches how a leg is travelled; a leg too far to guess uses the trip's [mainMode]. */
fun LegKind.mapsMode(mainMode: MainTravelMode): MapsTravelMode = when (this) {
    LegKind.WALK -> MapsTravelMode.WALKING
    LegKind.MOTORCYCLE -> MapsTravelMode.TWO_WHEELER
    LegKind.CAR -> MapsTravelMode.DRIVING
    LegKind.PUBLIC_TRANSPORT -> MapsTravelMode.TRANSIT
    LegKind.FAR -> mainMode.mapsMode()
}

/**
 * One route URL through [points] in order: the first is the origin, the last the destination, the ones between are
 * waypoints. Null for fewer than two points or more than [MAPS_MAX_WAYPOINTS] waypoints (use [mapsDayRouteUrls] to split).
 */
fun mapsDirectionsUrl(points: List<MapsPoint>, mode: MapsTravelMode): String? {
    if (points.size < 2 || points.size - 2 > MAPS_MAX_WAYPOINTS) return null
    return buildString {
        append("https://www.google.com/maps/dir/?api=1")
        append("&origin=").append(coordinates(points.first()))
        append("&destination=").append(coordinates(points.last()))
        append("&travelmode=").append(mode.parameter)
        val waypoints = points.subList(1, points.size - 1)
        if (waypoints.isNotEmpty()) append("&waypoints=").append(waypoints.joinToString("%7C") { coordinates(it) })
    }
}

/**
 * "Buka rute hari ini": the URLs that together walk through [points] in order, each with at most [maxWaypoints] stops
 * between its origin and destination (pass [MAPS_MAX_WAYPOINTS], or [MAPS_MAX_WAYPOINTS_MOBILE_BROWSER] on a mobile
 * browser). A day with more points is cut into several URLs, and each one starts where the one before ended. Two points
 * in a row at the same place are one point. Empty for fewer than two different points.
 */
fun mapsDayRouteUrls(points: List<MapsPoint>, mode: MapsTravelMode, maxWaypoints: Int = MAPS_MAX_WAYPOINTS): List<String> {
    val distinct = points.fold(emptyList<MapsPoint>()) { kept, point -> if (kept.lastOrNull() == point) kept else kept + point }
    if (distinct.size < 2) return emptyList()
    val perUrl = maxWaypoints.coerceIn(0, MAPS_MAX_WAYPOINTS) + 2
    val urls = mutableListOf<String>()
    var start = 0
    while (start < distinct.size - 1) {
        val end = minOf(start + perUrl, distinct.size)
        mapsDirectionsUrl(distinct.subList(start, end), mode)?.let(urls::add)
        start = end - 1
    }
    return urls
}

/** "lat,lng" with the comma encoded and at most six decimals (about 10 cm), never in scientific notation. */
private fun coordinates(point: MapsPoint): String = percentEncode("${decimal(point.lat)},${decimal(point.lng)}")

private fun decimal(value: Double): String {
    val micro = (abs(value) * 1_000_000).roundToLong()
    val whole = micro / 1_000_000
    val fraction = (micro % 1_000_000).toString().padStart(6, '0').trimEnd('0')
    val sign = if (value < 0 && micro != 0L) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}
