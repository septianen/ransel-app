package app.ransel.shared.domain

import kotlin.math.roundToInt

/*
 * How a travel leg is written (spec "Perjalanan antar tempat V1"): the numbers only. The words ("mnt", "j", "km") come from
 * the app's string resources, so Indonesian and English share the same rules.
 */

/** Hours and minutes of [minutes] (the minutes are what is left under an hour). */
data class HoursMinutes(val hours: Int, val minutes: Int)

fun hoursMinutes(minutes: Int): HoursMinutes = HoursMinutes(minutes / 60, minutes % 60)

/** A road distance as the capsule shows it. [meters] is set under 1 km; [kmTenths] is the km times 10 (52 = "5,2 km") from 1 km up. */
sealed interface TravelDistance {
    /** "600 m": the nearest 50 m, at least 50. */
    data class Meters(val meters: Int) : TravelDistance

    /** 1 km to under 10 km: one decimal. */
    data class Km(val tenths: Int) : TravelDistance {
        val whole: Int get() = tenths / 10
        val decimal: Int get() = tenths % 10
    }

    /** From 10 km up: whole kilometres. */
    data class WholeKm(val km: Int) : TravelDistance
}

/** The distance in [roadKm] (already x 1.3) as written on the capsule: under 1 km in metres to the nearest 50, 1-10 km one decimal, 10 km up whole. */
fun travelDistanceOf(roadKm: Double): TravelDistance {
    if (roadKm < 1.0) {
        val meters = ((roadKm * 1000 / 50).roundToInt() * 50).coerceIn(50, 1000)
        // 975 m rounds to 1000 m: that is 1 km
        return if (meters >= 1000) TravelDistance.Km(10) else TravelDistance.Meters(meters)
    }
    if (roadKm < 10.0) {
        val tenths = (roadKm * 10).roundToInt()
        return if (tenths >= 100) TravelDistance.WholeKm(10) else TravelDistance.Km(tenths)
    }
    return TravelDistance.WholeKm(roadKm.roundToInt())
}

// ── "Buka rute hari ini" ────────────────────────────────────────────────────

/** Points per route the spec uses (Benn, 16 Nov: 9 per route; Google would take 11 on the app, 5 in a mobile browser). */
const val DAY_ROUTE_MAX_POINTS = 9

/** The same in a browser on a phone, where the spec allows 3 points (Google's own limit there is 3 waypoints, so 5 points). */
const val DAY_ROUTE_MAX_POINTS_MOBILE_BROWSER = 3

/** One Maps link of a day's route: it covers stops [from]..[to] (1-based, both included); the last stop of a part starts the next one. */
data class DayRoutePart(val from: Int, val to: Int, val url: String)

/**
 * The parts of a day's route through [points], at most [maxPoints] stops each (stops 1-9, then 9-17, ...). Two stops in a row
 * at the same place are one stop, so [from] and [to] count the distinct ones. Empty for fewer than two distinct stops.
 */
fun dayRouteParts(points: List<MapsPoint>, mode: MapsTravelMode, maxPoints: Int = DAY_ROUTE_MAX_POINTS): List<DayRoutePart> {
    val perPart = maxPoints.coerceIn(2, MAPS_MAX_WAYPOINTS + 2)
    val urls = mapsDayRouteUrls(points, mode, maxWaypoints = perPart - 2)
    return urls.mapIndexed { index, url ->
        val from = index * (perPart - 1) + 1
        val distinctCount = points.fold(emptyList<MapsPoint>()) { kept, p -> if (kept.lastOrNull() == p) kept else kept + p }.size
        DayRoutePart(from, minOf(from + perPart - 1, distinctCount), url)
    }
}
