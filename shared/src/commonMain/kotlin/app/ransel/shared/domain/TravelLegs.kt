package app.ransel.shared.domain

import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.TripDto
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
@kotlinx.serialization.Serializable
enum class LegKind { WALK, MOTORCYCLE, CAR, PUBLIC_TRANSPORT, FAR }

/** Modes that can be saved on a leg. [LegKind.FAR] is never stored. */
val SAVED_LEG_KINDS: Set<LegKind> = setOf(LegKind.WALK, LegKind.MOTORCYCLE, LegKind.CAR, LegKind.PUBLIC_TRANSPORT)

/**
 * One leg between two points. [minutes] is what the capsule shows ("±15 mnt"): null for [LegKind.PUBLIC_TRANSPORT] (no
 * estimate, the capsule only opens Maps) and [LegKind.FAR]. [warningMinutes] is what the gap is compared with: the same
 * as [minutes] when there is one, a 50 km/h car for [LegKind.FAR], null for public transport.
 */
data class TravelLeg(val roadKm: Double, val kind: LegKind, val minutes: Int?, val warningMinutes: Int?)

/** The leg for [roadKm] travelled as [kind]. [LegKind.FAR] and public transport have no time; FAR still has a warning time. */
fun travelLegForKind(roadKm: Double, kind: LegKind): TravelLeg = when (kind) {
    LegKind.WALK -> estimated(roadKm, LegKind.WALK, WALK_SPEED_KMH)
    LegKind.MOTORCYCLE -> estimated(roadKm, LegKind.MOTORCYCLE, MOTORCYCLE_SPEED_KMH)
    LegKind.CAR -> estimated(roadKm, LegKind.CAR, CAR_SPEED_KMH)
    LegKind.PUBLIC_TRANSPORT -> TravelLeg(roadKm, LegKind.PUBLIC_TRANSPORT, null, null)
    LegKind.FAR -> TravelLeg(roadKm, LegKind.FAR, null, roundLegMinutes(roadKm / FAR_WARNING_SPEED_KMH * 60))
}

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

/** What [kind] would take over [roadKm], for the mode cards of the sheet; null for public transport and a leg too far to guess. */
fun travelMinutesFor(roadKm: Double, kind: LegKind): Int? = when (kind) {
    LegKind.WALK -> roundLegMinutes(roadKm / WALK_SPEED_KMH * 60)
    LegKind.MOTORCYCLE -> roundLegMinutes(roadKm / MOTORCYCLE_SPEED_KMH * 60)
    LegKind.CAR -> roundLegMinutes(roadKm / CAR_SPEED_KMH * 60)
    LegKind.PUBLIC_TRANSPORT, LegKind.FAR -> null
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
data class TravelWarning(val gapMinutes: Int, val needMinutes: Int) {
    /** The two activities touch or overlap (gap of 0 or less): "Jam bentrok dengan aktivitas sebelumnya. Jam tidak diubah." (Benn, 16 Nov) */
    val overlaps: Boolean get() = gapMinutes <= 0
}

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
fun legBetween(
    from: ItineraryItemDto,
    to: ItineraryItemDto,
    mainMode: MainTravelMode,
    /** A saved mode for this pair; ignored when the auto leg is [LegKind.FAR] or the kind cannot be stored. */
    savedKind: LegKind? = null,
): LegDisplay {
    if (from.category == Category.TRANSPORT) return LegDisplay.None
    if (to.category == Category.TRANSPORT && !to.isPointPlace()) return LegDisplay.None
    if (!from.hasCoordinates() || !to.hasCoordinates()) return LegDisplay.AddLocationPrompt
    if (!from.isPointPlace() || !to.isPointPlace()) return LegDisplay.None
    val auto = travelLegBetween(from.lat!!, from.lng!!, to.lat!!, to.lng!!, mainMode)
    val leg = if (auto.kind != LegKind.FAR && savedKind != null && savedKind in SAVED_LEG_KINDS) {
        travelLegForKind(auto.roadKm, savedKind)
    } else {
        auto
    }
    return LegDisplay.Leg(leg, travelWarning(leg, gapMinutesBetween(from, to)))
}

/** Saved modes keyed by (from item id, to item id). A pair that is no longer consecutive is simply absent. */
fun savedKindOf(overrides: Map<Pair<String, String>, LegKind>, fromId: String, toId: String): LegKind? =
    overrides[fromId to toId]

/** The capsules of one day: [items] in timeline order, one entry for each consecutive pair (so `items.size - 1`, none for fewer than two). */
fun dayLegs(
    items: List<ItineraryItemDto>,
    mainMode: MainTravelMode,
    overrides: Map<Pair<String, String>, LegKind> = emptyMap(),
): List<LegDisplay> =
    items.zipWithNext { from, to -> legBetween(from, to, mainMode, savedKindOf(overrides, from.id, to.id)) }

/**
 * An activity that is a stop on the map and on "Buka rute hari ini": it has coordinates, except a Perjalanan activity whose
 * place is a city or area ("OTW Bandung"), which has no pin and no number (spec "Perjalanan antar tempat V1", section 4).
 */
fun ItineraryItemDto.isMapStop(): Boolean = hasCoordinates() && !(category == Category.TRANSPORT && !isPointPlace())

/** The stops of a day, in order. */
fun dayStops(items: List<ItineraryItemDto>): List<ItineraryItemDto> = items.filter { it.isMapStop() }

/** The Google Maps points of a day for "Buka rute hari ini": the stops of the day, in order. */
fun dayRoutePoints(items: List<ItineraryItemDto>): List<MapsPoint> =
    dayStops(items).map { MapsPoint(it.lat!!, it.lng!!) }

// ── The city of a place ─────────────────────────────────────────────────────

/**
 * The city of a place picked from a suggestion, for the city chip of a day (spec "Perjalanan antar tempat V1", mini peta).
 * A city or a regency is its own city; a province or a country has none; any other place takes the first part of the
 * suggestion's second line ("Labuan Bajo, Manggarai Barat" gives "Labuan Bajo"). Null when nothing is known.
 */
fun placeCityOf(kind: PlaceKind?, name: String, secondary: String?): String? = when (kind) {
    PlaceKind.CITY, PlaceKind.REGENCY -> name.trim().takeIf { it.isNotEmpty() }
    PlaceKind.PROVINCE, PlaceKind.COUNTRY -> null
    else -> secondary?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
}

// ── The trip's main mode ────────────────────────────────────────────────────

/**
 * The trip's main mode for the capsules: the one stored, else what the interview answers say, else the default (Motor in
 * Indonesia, Mobil abroad) (plan "Perjalanan antar tempat", answer 2).
 */
fun TripDto.effectiveMainTravelMode(): MainTravelMode =
    mainTravelMode ?: mainTravelModeOf(departureMode, localTransport, isOverseas(countryCode))

// ── The mini map: lines and cities ──────────────────────────────────────────

/** A straight line of the mini map between two consecutive stops that have a capsule between them (spec "Perjalanan antar tempat V1", section 5). */
data class DaySegment(val from: ItineraryItemDto, val to: ItineraryItemDto, val leg: TravelLeg)

/**
 * The lines of a day's mini map: [stops] (the day's [dayStops]) two by two, the ones where a capsule would be (so none after
 * a Perjalanan activity, none to an area). The leg is the same as the capsule's.
 */
fun daySegments(
    stops: List<ItineraryItemDto>,
    mainMode: MainTravelMode,
    overrides: Map<Pair<String, String>, LegKind> = emptyMap(),
): List<DaySegment> =
    stops.zipWithNext { from, to ->
        (legBetween(from, to, mainMode, savedKindOf(overrides, from.id, to.id)) as? LegDisplay.Leg)?.let { DaySegment(from, to, it.leg) }
    }.filterNotNull()

/** A city of the day and how many of its stops have a place there ("Jakarta · 2"). */
data class DayCity(val name: String, val count: Int)

private fun String.cityKey() = trim().lowercase()

/** The cities of a day's [stops] in the order they first appear. A stop whose city is not known counts for none. */
fun dayCities(stops: List<ItineraryItemDto>): List<DayCity> {
    val names = LinkedHashMap<String, String>()
    val counts = HashMap<String, Int>()
    stops.forEach { stop ->
        val city = stop.placeCity?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
        val key = city.cityKey()
        names.getOrPut(key) { city }
        counts[key] = (counts[key] ?: 0) + 1
    }
    return names.map { (key, name) -> DayCity(name, counts.getValue(key)) }
}

/** The city the map starts on: the trip destination's when it is one of [cities] (by name), else the first. Null without cities. */
fun defaultDayCity(cities: List<DayCity>, destination: String): String? {
    val wanted = destination.cityKey()
    return (cities.firstOrNull { it.name.cityKey() == wanted } ?: cities.firstOrNull())?.name
}

/** The stops of [city]; a stop whose city is not known stays in every city (spec: no city is known = one city). */
fun stopsInCity(stops: List<ItineraryItemDto>, city: String): List<ItineraryItemDto> {
    val key = city.cityKey()
    return stops.filter { stop -> stop.placeCity?.trim()?.takeIf { it.isNotEmpty() }?.cityKey().let { it == null || it == key } }
}

/** A Perjalanan activity that takes the day from one city to the next ("Jakarta → Bandung · OTW 09.00–12.00 · 3 jam"). */
data class IntercityTransfer(val item: ItineraryItemDto, val fromCity: String, val toCity: String)

/**
 * The moves between cities of a day: two stops in a row (by timeline [items]) with different known cities, and the last
 * Perjalanan activity from the first of them up to just before the second. No such activity, no row.
 */
fun intercityTransfers(items: List<ItineraryItemDto>): List<IntercityTransfer> {
    val located = items.withIndex().filter { (_, item) -> item.isMapStop() && !item.placeCity.isNullOrBlank() }
    return located.zipWithNext().mapNotNull { (a, b) ->
        val from = a.value.placeCity!!.trim()
        val to = b.value.placeCity!!.trim()
        if (from.cityKey() == to.cityKey()) return@mapNotNull null
        val transfer = items.subList(a.index, b.index).lastOrNull { it.category == Category.TRANSPORT } ?: return@mapNotNull null
        IntercityTransfer(transfer, from, to)
    }
}
