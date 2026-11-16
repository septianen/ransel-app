package app.ransel.shared.domain

import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.model.Category
import app.ransel.shared.model.MainTravelMode
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

/*
 * The travel capsule between two activities of a day (plan "Perjalanan antar tempat V1", 16 Nov 2026): how far, how
 * long, by what, and whether the gap between the two activities is enough. Pure and free: the distance is the straight
 * line times 1.3, the speeds are fixed averages (always shown as "±"), no routing API, so it works offline and gives the
 * same number on every device. Activity times are never changed here: a tight gap is only a warning.
 */

/** Below this road distance (km) the leg is walked. */
const val WALK_MAX_KM = 1.0

/** Up to this road distance (km) the leg is guessed with the trip's main mode; beyond it nothing is guessed. */
const val GUESS_MAX_KM = 50.0

const val WALK_SPEED_KMH = 4.5
const val MOTORCYCLE_SPEED_KMH = 22.0
const val CAR_SPEED_KMH = 18.0

/** Only for the "too tight" warning on a leg beyond [GUESS_MAX_KM]; it is never shown as a time. */
const val FAR_WARNING_SPEED_KMH = 50.0

/** Times are rounded to the nearest 5 minutes, and a leg is never shown as less than 5. */
const val LEG_ROUNDING_MINUTES = 5

/** What the leg is travelled by. [FAR] = too far to guess ("Jarak jauh ±N km"). */
enum class LegKind { WALK, MOTORCYCLE, CAR, PUBLIC_TRANSPORT, FAR }

/**
 * One leg between two points. [minutes] is what the capsule shows ("±15 mnt"): null for [LegKind.PUBLIC_TRANSPORT] (no
 * estimate, the capsule only opens Maps) and [LegKind.FAR]. [warningMinutes] is what the gap is compared with: the same
 * as [minutes] when there is one, a 50 km/h car for [LegKind.FAR], null for public transport.
 */
data class TravelLeg(val roadKm: Double, val kind: LegKind, val minutes: Int?, val warningMinutes: Int?)

/** The leg for [roadKm] (already ×1.3, see [roadKm]) with the trip's [mainMode]. */
fun travelLegFor(roadKm: Double, mainMode: MainTravelMode): TravelLeg = when {
    roadKm < WALK_MAX_KM -> estimated(roadKm, LegKind.WALK, WALK_SPEED_KMH)
    roadKm <= GUESS_MAX_KM -> when (mainMode) {
        MainTravelMode.MOTORCYCLE -> estimated(roadKm, LegKind.MOTORCYCLE, MOTORCYCLE_SPEED_KMH)
        MainTravelMode.CAR -> estimated(roadKm, LegKind.CAR, CAR_SPEED_KMH)
        MainTravelMode.PUBLIC_TRANSPORT -> TravelLeg(roadKm, LegKind.PUBLIC_TRANSPORT, null, null)
    }
    else -> TravelLeg(roadKm, LegKind.FAR, null, roundLegMinutes(roadKm / FAR_WARNING_SPEED_KMH * 60))
}

private fun estimated(roadKm: Double, kind: LegKind, speedKmh: Double): TravelLeg {
    val minutes = roundLegMinutes(roadKm / speedKmh * 60)
    return TravelLeg(roadKm, kind, minutes, minutes)
}

/** Nearest 5 minutes, at least 5. */
fun roundLegMinutes(minutes: Double): Int =
    ((minutes / LEG_ROUNDING_MINUTES).roundToInt() * LEG_ROUNDING_MINUTES).coerceAtLeast(LEG_ROUNDING_MINUTES)

/** The leg between two coordinates. */
fun travelLegBetween(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mainMode: MainTravelMode): TravelLeg =
    travelLegFor(roadKm(fromLat, fromLng, toLat, toLng), mainMode)

// ── Warning ─────────────────────────────────────────────────────────────────

/** The leg needs [needMinutes] but the plan leaves [gapMinutes] (negative = the activities overlap): "Jeda cuma 15 mnt. Jam tidak diubah." */
data class TravelWarning(val gapMinutes: Int, val needMinutes: Int)

/**
 * Minutes between the end of [from] and the start of [to], both on the same day. An activity that ends after midnight
 * ends at its end time plus a day. Null when either time is missing: there is nothing to compare.
 */
fun gapMinutesBetween(from: ItineraryItemDto, to: ItineraryItemDto): Int? {
    val end = from.endTime ?: return null
    val start = to.startTime ?: return null
    val endAbsolute = end.minutes() + if (endsNextDay(from.startTime, end)) MINUTES_IN_DAY else 0
    return start.minutes() - endAbsolute
}

private const val MINUTES_IN_DAY = 24 * 60

/** A warning when the leg's [TravelLeg.warningMinutes] is more than [gapMinutes]; none without a gap or without an estimate. */
fun travelWarning(leg: TravelLeg, gapMinutes: Int?): TravelWarning? {
    val need = leg.warningMinutes ?: return null
    if (gapMinutes == null || need <= gapMinutes) return null
    return TravelWarning(gapMinutes, need)
}

// ── Points and areas ────────────────────────────────────────────────────────

/** A city, regency, province or country is an area; every other kind is a point on the map. */
fun PlaceKind.isAreaLevel(): Boolean =
    this == PlaceKind.CITY || this == PlaceKind.REGENCY || this == PlaceKind.PROVINCE || this == PlaceKind.COUNTRY

/** The activity has both coordinates (the contract keeps them as a pair). */
fun ItineraryItemDto.hasCoordinates(): Boolean = lat != null && lng != null

/** A point: it has coordinates and its place is not an area. An unknown kind ([ItineraryItemDto.placeKind] null) counts as a point. */
fun ItineraryItemDto.isPointPlace(): Boolean = hasCoordinates() && placeKind?.isAreaLevel() != true

// ── The capsule between two activities ──────────────────────────────────────

/** What is drawn between two consecutive activities of a day. */
sealed interface LegDisplay {
    /** Nothing: not enough to say, and no invitation either. */
    data object None : LegDisplay

    /** "Tambah lokasi untuk lihat perjalanan": an activity (that is not a Perjalanan one) has no place yet. */
    data object AddLocationPrompt : LegDisplay

    data class Leg(val leg: TravelLeg, val warning: TravelWarning?) : LegDisplay
}

/**
 * The capsule between [from] and [to], two consecutive activities of the same day (plan "Aktivitas Perjalanan"):
 *  - after a Perjalanan activity (category TRANSPORT, "OTW Bandung") there is never one;
 *  - before a Perjalanan activity there is one only when its place is a point (a station, terminal, airport); a city or
 *    area, or no place, gives nothing, and no invitation to add a place either;
 *  - otherwise both need coordinates: when one of the two has none the capsule invites to add a place; when one is an area
 *    (a city centre) there is nothing to measure.
 * The leg uses [mainMode]; the warning compares it with the gap between the two activities' times.
 */
fun legBetween(from: ItineraryItemDto, to: ItineraryItemDto, mainMode: MainTravelMode): LegDisplay {
    if (from.category == Category.TRANSPORT) return LegDisplay.None
    if (to.category == Category.TRANSPORT && !to.isPointPlace()) return LegDisplay.None
    if (!from.hasCoordinates() || !to.hasCoordinates()) return LegDisplay.AddLocationPrompt
    if (!from.isPointPlace() || !to.isPointPlace()) return LegDisplay.None
    val leg = travelLegBetween(from.lat!!, from.lng!!, to.lat!!, to.lng!!, mainMode)
    return LegDisplay.Leg(leg, travelWarning(leg, gapMinutesBetween(from, to)))
}

/** The capsules of one day: [items] in timeline order, one entry for each consecutive pair (so `items.size - 1`, none for fewer than two). */
fun dayLegs(items: List<ItineraryItemDto>, mainMode: MainTravelMode): List<LegDisplay> =
    items.zipWithNext { from, to -> legBetween(from, to, mainMode) }

/** The Google Maps points of a day for "Buka rute hari ini": the activities that are a point, in order. */
fun dayRoutePoints(items: List<ItineraryItemDto>): List<MapsPoint> =
    items.filter { it.isPointPlace() }.map { MapsPoint(it.lat!!, it.lng!!) }
