package app.ransel.feature.itinerary.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A point on the map; kept free of Compose so the camera rules can be tested on every target. */
data class GeoPoint(val lat: Double, val lng: Double)

data class GeoRect(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val centerLat: Double get() = (south + north) / 2
    val centerLng: Double get() = (west + east) / 2
}

data class MiniMapFit(
    val centerLat: Double,
    val centerLng: Double,
    val zoom: Float,
)

const val MINI_MAP_MAX_ZOOM = 18f
const val MINI_MAP_MIN_ZOOM_FLOOR = 10f
const val MINI_MAP_SINGLE_FIT_ZOOM = 15f

/** About 2 km around a lone stop (1 km each side of the pin). */
private const val SINGLE_STOP_KM = 2.0
private const val KM_PER_DEG_LAT = 110.574

fun geoBounds(points: List<GeoPoint>): GeoRect? {
    if (points.isEmpty()) return null
    var south = points[0].lat
    var north = points[0].lat
    var west = points[0].lng
    var east = points[0].lng
    for (point in points) {
        south = min(south, point.lat)
        north = max(north, point.lat)
        west = min(west, point.lng)
        east = max(east, point.lng)
    }
    return GeoRect(south, west, north, east)
}

/**
 * Where the camera target may go: the stops' bounding box grown by 50 % (25 % of the span on each side).
 * One stop (or a collapsed axis) uses a ~2 km box instead of a zero-size pad.
 */
fun dragBounds(points: List<GeoPoint>): GeoRect? {
    val box = geoBounds(points) ?: return null
    val minLat = (SINGLE_STOP_KM / 2) / KM_PER_DEG_LAT
    val cosLat = cos(box.centerLat * PI / 180.0).let { if (it < 0.01) 0.01 else it }
    val minLng = (SINGLE_STOP_KM / 2) / (KM_PER_DEG_LAT * cosLat)
    if (points.size == 1) {
        return GeoRect(box.centerLat - minLat, box.centerLng - minLng, box.centerLat + minLat, box.centerLng + minLng)
    }
    val latSpan = box.north - box.south
    val lngSpan = box.east - box.west
    val padLat = if (latSpan == 0.0) minLat else latSpan * 0.25
    val padLng = if (lngSpan == 0.0) minLng else lngSpan * 0.25
    return GeoRect(box.south - padLat, box.west - padLng, box.north + padLat, box.east + padLng)
}

/** Most-zoomed-out allowed: one level wider than the fit, but never wider than zoom 10. */
fun minZoomPreference(fitZoom: Float): Float = max(fitZoom - 1f, MINI_MAP_MIN_ZOOM_FLOOR)

fun clampZoom(zoom: Float, fitZoom: Float): Float = zoom.coerceIn(minZoomPreference(fitZoom), MINI_MAP_MAX_ZOOM)

fun clampToRect(lat: Double, lng: Double, rect: GeoRect): GeoPoint =
    GeoPoint(lat.coerceIn(rect.south, rect.north), lng.coerceIn(rect.west, rect.east))

/**
 * Camera that shows every stop, using the same numbers as `newLatLngBounds` so the map can start on this
 * position before the SDK is created. One stop stays at [MINI_MAP_SINGLE_FIT_ZOOM].
 */
fun fitCamera(
    points: List<GeoPoint>,
    widthPx: Int,
    heightPx: Int,
    paddingLeftPx: Int,
    paddingTopPx: Int,
    paddingRightPx: Int,
    paddingBottomPx: Int,
    density: Float,
): MiniMapFit? {
    if (points.isEmpty()) return null
    if (points.size == 1) {
        return MiniMapFit(points[0].lat, points[0].lng, MINI_MAP_SINGLE_FIT_ZOOM)
    }
    val box = geoBounds(points) ?: return null
    val innerW = (widthPx - paddingLeftPx - paddingRightPx).coerceAtLeast(1)
    val innerH = (heightPx - paddingTopPx - paddingBottomPx).coerceAtLeast(1)
    val zoom = zoomFor(box, innerW, innerH, density).coerceIn(MINI_MAP_MIN_ZOOM_FLOOR, MINI_MAP_MAX_ZOOM)
    return MiniMapFit(box.centerLat, box.centerLng, zoom)
}

internal fun zoomFor(box: GeoRect, widthPx: Int, heightPx: Int, density: Float): Float {
    val world = 256.0 * density.toDouble().coerceAtLeast(0.5)
    val fracLat = mercatorFraction(box.south, box.north).coerceAtLeast(1e-9)
    val fracLng = ((box.east - box.west) / 360.0).let { if (it <= 0) 1e-9 else it }
    val latZoom = ln(heightPx / world / fracLat) / LN2
    val lngZoom = ln(widthPx / world / fracLng) / LN2
    return min(latZoom, lngZoom).toFloat()
}

private val LN2 = ln(2.0)

/** Mercator Y span of two latitudes, as a fraction of the world map (π of mercator-Y). */
private fun mercatorFraction(south: Double, north: Double): Double {
    val mercNorth = latToMercator(north * PI / 180.0)
    val mercSouth = latToMercator(south * PI / 180.0)
    return (mercNorth - mercSouth) / PI
}

private fun latToMercator(latRad: Double): Double {
    val clamped = latRad.coerceIn(-MAX_MERCATOR, MAX_MERCATOR)
    val s = sin(clamped)
    return ln((1 + s) / (1 - s)) / 2
}

private const val MAX_MERCATOR = 85.05112878 * PI / 180.0
