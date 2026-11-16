package app.ransel.shared.domain

import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.MainTravelMode
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The questions of "Buat trip" and what branches them (plan "Wawancara Buat trip", 16 Nov 2026). Pure functions, no AI and
 * no network, so the interview runs offline for guests and gives the same choices on every device. The lists of cities
 * (with a train station, where motorbikes are rented, with public transport on Google Maps) are managed in Karung: the
 * caller looks the two cities up and passes the answers in as booleans.
 */

// ── Distance ────────────────────────────────────────────────────────────────

/** Great-circle distance in kilometres. */
fun greatCircleKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = (lat2 - lat1) * PI / 180
    val dLng = (lng2 - lng1) * PI / 180
    val a = sin(dLat / 2).let { it * it } +
        cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLng / 2).let { it * it }
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
}

private const val EARTH_RADIUS_KM = 6371.0

/** A road is longer than the straight line between two places: the plans use 1.3 for every distance (W2 bands and trip legs). */
const val ROAD_FACTOR = 1.3

/** Straight-line distance times [ROAD_FACTOR]: "jarak jalan", an estimate and never a route. */
fun roadKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double = greatCircleKm(lat1, lng1, lat2, lng2) * ROAD_FACTOR

// ── W2: how to get there ────────────────────────────────────────────────────

/** Below this road distance (km) on one island W2 offers the own motorbike first. */
const val DEPARTURE_SHORT_KM = 150.0

/** From [DEPARTURE_SHORT_KM] up to this the own car, train or plane come first; beyond it the plane does. */
const val DEPARTURE_MEDIUM_KM = 700.0

/**
 * How far the origin is from the destination, which decides the W2 choices. [OTHER_ISLAND] is another big island of
 * Indonesia at any distance; [LONG] is one island but over [DEPARTURE_MEDIUM_KM] (the plan's table has no row for it, so
 * it asks for the plane first); [UNKNOWN] = no origin or no destination point to measure.
 */
enum class DepartureBand { UNKNOWN, SHORT, MEDIUM, LONG, OTHER_ISLAND, OVERSEAS }

/**
 * The band for a trip from [origin] to a destination at [destLat], [destLng] in [destCountryCode] (null = Indonesia).
 * Overseas = either end outside Indonesia. Two points on different big islands ([islandOf]) are [DepartureBand.OTHER_ISLAND];
 * when an island is not known the road distance alone decides.
 */
fun departureBand(origin: TripOriginDto?, destLat: Double?, destLng: Double?, destCountryCode: String?): DepartureBand {
    if (isOverseas(destCountryCode) || (origin != null && isOverseas(origin.countryCode))) return DepartureBand.OVERSEAS
    val fromLat = origin?.lat
    val fromLng = origin?.lng
    if (fromLat == null || fromLng == null || destLat == null || destLng == null) return DepartureBand.UNKNOWN
    val from = islandOf(fromLat, fromLng)
    val to = islandOf(destLat, destLng)
    if (from != null && to != null && from != to) return DepartureBand.OTHER_ISLAND
    val km = roadKm(fromLat, fromLng, destLat, destLng)
    return when {
        km < DEPARTURE_SHORT_KM -> DepartureBand.SHORT
        km <= DEPARTURE_MEDIUM_KM -> DepartureBand.MEDIUM
        else -> DepartureBand.LONG
    }
}

/**
 * The W2 choices, the first one on top. [trainBetweenCities] = both cities have a station (Karung's list); without it
 * the train is not offered. Empty for [DepartureBand.OVERSEAS]: the question is not asked, the plane is understood.
 * [DepartureBand.UNKNOWN] (W1 skipped, or a destination or origin typed as text without a point) offers the whole list in the
 * default order, the train included whatever the cities are, since nothing can be said about the distance (spec 16 Nov).
 */
fun departureOptions(band: DepartureBand, trainBetweenCities: Boolean): List<DepartureMode> {
    val train = if (trainBetweenCities) listOf(DepartureMode.TRAIN) else emptyList()
    return when (band) {
        DepartureBand.SHORT ->
            listOf(DepartureMode.OWN_MOTORCYCLE, DepartureMode.OWN_CAR, DepartureMode.TRAVEL_SHUTTLE, DepartureMode.BUS) + train
        DepartureBand.MEDIUM ->
            listOf(DepartureMode.OWN_CAR) + train + listOf(DepartureMode.BUS, DepartureMode.PLANE, DepartureMode.TRAVEL_SHUTTLE)
        DepartureBand.LONG ->
            listOf(DepartureMode.PLANE, DepartureMode.OWN_CAR, DepartureMode.BUS) + train
        DepartureBand.OTHER_ISLAND -> listOf(DepartureMode.PLANE, DepartureMode.FERRY, DepartureMode.OWN_CAR)
        DepartureBand.UNKNOWN -> listOf(
            DepartureMode.OWN_MOTORCYCLE, DepartureMode.OWN_CAR, DepartureMode.TRAVEL_SHUTTLE, DepartureMode.BUS,
            DepartureMode.TRAIN, DepartureMode.PLANE, DepartureMode.FERRY,
        )
        DepartureBand.OVERSEAS -> emptyList()
    }
}

/** Own motorbike or own car: no W3 question and no rental suggestion anywhere in the trip. */
fun isOwnVehicle(mode: DepartureMode?): Boolean = mode == DepartureMode.OWN_MOTORCYCLE || mode == DepartureMode.OWN_CAR

// ── W3: getting around there ────────────────────────────────────────────────

/** W3 is asked unless W2 was an own vehicle. Not answering W2 (or it never being asked, overseas) still asks it. */
fun asksLocalTransport(departure: DepartureMode?): Boolean = !isOwnVehicle(departure)

/**
 * The W3 choices, the first one on top, by what the destination is: overseas, a city where motorbikes are rented
 * ([motorcycleRentalCity], Karung's list), a city with public transport on Google Maps ([publicTransportCity], Karung's
 * list), or any other place. A city on both lists offers the rental first, then public transport.
 */
fun localTransportOptions(overseas: Boolean, motorcycleRentalCity: Boolean, publicTransportCity: Boolean): List<LocalTransport> = when {
    overseas -> listOf(LocalTransport.PUBLIC_TRANSPORT, LocalTransport.TAXI, LocalTransport.RENT_CAR, LocalTransport.UNKNOWN)
    motorcycleRentalCity && publicTransportCity -> listOf(
        LocalTransport.RENT_MOTORCYCLE, LocalTransport.PUBLIC_TRANSPORT, LocalTransport.RIDE_HAILING, LocalTransport.RENT_CAR,
        LocalTransport.UNKNOWN,
    )
    motorcycleRentalCity -> listOf(LocalTransport.RENT_MOTORCYCLE, LocalTransport.RIDE_HAILING, LocalTransport.RENT_CAR, LocalTransport.UNKNOWN)
    publicTransportCity -> listOf(LocalTransport.PUBLIC_TRANSPORT, LocalTransport.RIDE_HAILING, LocalTransport.UNKNOWN)
    else -> listOf(LocalTransport.RIDE_HAILING, LocalTransport.RENT_CAR, LocalTransport.RENT_MOTORCYCLE, LocalTransport.UNKNOWN)
}

/** The rental suggestion (V2) stays away from a trip whose creator travels and gets around with their own vehicle. */
fun allowsRentalSuggestion(departure: DepartureMode?): Boolean = !isOwnVehicle(departure)

// ── Main travel mode ────────────────────────────────────────────────────────

/**
 * The trip's main way of moving between places, from the answers. An own vehicle decides first; then W3: a rented
 * motorbike is [MainTravelMode.MOTORCYCLE], a rented car or a taxi [MainTravelMode.CAR], public transport
 * [MainTravelMode.PUBLIC_TRANSPORT], ride-hailing the motorbike in Indonesia (ojol) and the car abroad. No answer or
 * "Belum tahu": Motor in Indonesia, Mobil abroad (plan "Perjalanan antar tempat", answer 2).
 */
fun mainTravelModeOf(departure: DepartureMode?, local: LocalTransport?, overseas: Boolean): MainTravelMode {
    val byDefault = if (overseas) MainTravelMode.CAR else MainTravelMode.MOTORCYCLE
    return when (departure) {
        DepartureMode.OWN_MOTORCYCLE -> MainTravelMode.MOTORCYCLE
        DepartureMode.OWN_CAR -> MainTravelMode.CAR
        else -> when (local) {
            LocalTransport.RENT_MOTORCYCLE -> MainTravelMode.MOTORCYCLE
            LocalTransport.RENT_CAR, LocalTransport.TAXI -> MainTravelMode.CAR
            LocalTransport.PUBLIC_TRANSPORT -> MainTravelMode.PUBLIC_TRANSPORT
            LocalTransport.RIDE_HAILING -> byDefault
            LocalTransport.UNKNOWN, null -> byDefault
        }
    }
}

// ── The "X dari Y" indicator ────────────────────────────────────────────────

/** The screens of the interview in order: Layar 1 (destination and dates), W1 to W4. */
enum class InterviewStep { DESTINATION, ORIGIN, DEPARTURE, LOCAL_TRANSPORT, TASTE }

/** Layar 1 plus W1–W4: where "Y" starts and its largest value. */
val MAX_INTERVIEW_STEPS: Int = InterviewStep.entries.size

/**
 * The steps that still apply: W2 is dropped when the trip is overseas, W3 when W2 was an own vehicle. [overseas] is known
 * as soon as the destination (and W1) are.
 */
fun interviewSteps(overseas: Boolean, departure: DepartureMode?): List<InterviewStep> = buildList {
    add(InterviewStep.DESTINATION)
    add(InterviewStep.ORIGIN)
    if (!overseas) add(InterviewStep.DEPARTURE)
    if (asksLocalTransport(departure)) add(InterviewStep.LOCAL_TRANSPORT)
    add(InterviewStep.TASTE)
}

/** "[position] dari [total]". */
data class InterviewProgress(val position: Int, val total: Int)

/**
 * The indicator on [current]: its place among the [steps] that apply to the answers right now. Y is recomputed from the
 * current answers every time (spec 16 Nov): it goes down when a step stops applying (abroad: W2 gone from Layar 1; own
 * vehicle: W3 gone when W2 is picked) and comes back up if the user goes back and changes the answer. Subang → Bandung by
 * own motorbike reads 1 dari 5, 2 dari 5, 3 dari 5 on W2, then 3 dari 4 once it is picked and W4 is 4 dari 4. A [current]
 * that is no longer among the steps (W3 after W2 became an own vehicle) is shown as the last one.
 */
fun interviewProgress(steps: List<InterviewStep>, current: InterviewStep): InterviewProgress {
    val total = steps.size
    val index = steps.indexOf(current)
    return InterviewProgress(if (index < 0) total else index + 1, total)
}

// ── Validation ──────────────────────────────────────────────────────────────

const val TRIP_ORIGIN_NAME_MAX = 120

/** Field name → reason for a trip's origin (W1); null is fine. Keys are `origin.name`, `origin.coordinates`, `origin.countryCode`, `origin.placeId`. */
fun validateTripOrigin(origin: TripOriginDto?): Map<String, String> = buildMap {
    if (origin == null) return@buildMap
    if (origin.name.isBlank()) put("origin.name", "REQUIRED")
    else if (origin.name.length > TRIP_ORIGIN_NAME_MAX) put("origin.name", "TOO_LONG")
    if ((origin.lat == null) != (origin.lng == null) || origin.lat?.let { it !in -90.0..90.0 } == true || origin.lng?.let { it !in -180.0..180.0 } == true) {
        put("origin.coordinates", "INVALID_COORDINATES")
    }
    if (origin.countryCode != null && !Regex("^[A-Za-z]{2}$").matches(origin.countryCode)) put("origin.countryCode", "INVALID_COUNTRY")
    if (origin.placeId != null && (origin.placeId.isBlank() || origin.placeId.length > 300)) put("origin.placeId", "INVALID_PLACE_ID")
}
