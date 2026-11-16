package app.ransel.shared.domain

import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.model.Category
import app.ransel.shared.model.MainTravelMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

private fun item(
    id: String,
    start: String? = null,
    end: String? = null,
    lat: Double? = null,
    lng: Double? = null,
    category: Category = Category.ATTRACTION,
    kind: PlaceKind? = null,
) = ItineraryItemDto(
    id = id, tripId = "trip", dayDate = LocalDate(2026, 11, 20),
    startTime = start?.let(LocalTime::parse), endTime = end?.let(LocalTime::parse),
    title = id, category = category, lat = lat, lng = lng, placeKind = kind,
    createdAt = Instant.parse("2026-11-01T00:00:00Z"), updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
)

class TravelLegsTest {
    // 0.1 degree of latitude is 11.119 km in a straight line, 14.455 km by road
    private val hotel = item("hotel", "08:00", "09:00", -6.9, 107.6)
    private val farm = item("farm", "10:00", "11:00", -7.0, 107.6)

    // ── distance, mode, minutes ─────────────────────────────────────────────

    @Test
    fun under1KmIsWalkedAtFourPointFiveKmPerHour() {
        val leg = travelLegFor(0.99, MainTravelMode.CAR)
        assertEquals(LegKind.WALK, leg.kind)
        assertEquals(15, leg.minutes) // 0.99 / 4.5 * 60 = 13.2 minutes: nearest 5 is 15
        assertEquals(LegKind.WALK, travelLegFor(0.0, MainTravelMode.MOTORCYCLE).kind)
        assertEquals(5, travelLegFor(0.0, MainTravelMode.MOTORCYCLE).minutes, "never less than 5")
        assertEquals(10, travelLegFor(0.7, MainTravelMode.MOTORCYCLE).minutes) // 9.3 minutes
    }

    @Test
    fun betweenOneAndFiftyKmTheMainModeSetsTheSpeed() {
        val motor = travelLegFor(14.455, MainTravelMode.MOTORCYCLE)
        assertEquals(LegKind.MOTORCYCLE, motor.kind)
        assertEquals(40, motor.minutes) // 14.455 / 22 * 60 = 39.4
        val car = travelLegFor(14.455, MainTravelMode.CAR)
        assertEquals(LegKind.CAR, car.kind)
        assertEquals(50, car.minutes) // 14.455 / 18 * 60 = 48.2
        assertEquals(LegKind.MOTORCYCLE, travelLegFor(1.0, MainTravelMode.MOTORCYCLE).kind, "exactly 1 km is no longer a walk")
        assertEquals(LegKind.CAR, travelLegFor(50.0, MainTravelMode.CAR).kind, "exactly 50 km is still guessed")
    }

    @Test
    fun publicTransportGetsNoTimeButStillWalksUnderOneKm() {
        val leg = travelLegFor(8.0, MainTravelMode.PUBLIC_TRANSPORT)
        assertEquals(LegKind.PUBLIC_TRANSPORT, leg.kind)
        assertNull(leg.minutes)
        assertNull(leg.warningMinutes)
        assertEquals(LegKind.WALK, travelLegFor(0.4, MainTravelMode.PUBLIC_TRANSPORT).kind)
    }

    @Test
    fun beyondFiftyKmNothingIsGuessedButTheWarningUsesACarAtFiftyKmPerHour() {
        val leg = travelLegFor(130.0, MainTravelMode.MOTORCYCLE)
        assertEquals(LegKind.FAR, leg.kind)
        assertNull(leg.minutes)
        assertEquals(155, leg.warningMinutes) // 130 / 50 * 60 = 156 minutes
        assertEquals(LegKind.FAR, travelLegFor(50.01, MainTravelMode.CAR).kind)
    }

    @Test
    fun minutesRoundToTheNearestFiveWithAFloorOfFive() {
        assertEquals(5, roundLegMinutes(0.0))
        assertEquals(5, roundLegMinutes(2.4))
        assertEquals(5, roundLegMinutes(4.9))
        assertEquals(10, roundLegMinutes(12.4))
        assertEquals(15, roundLegMinutes(12.5))
        assertEquals(15, roundLegMinutes(15.0))
        assertEquals(135, roundLegMinutes(136.4))
    }

    @Test
    fun theLegBetweenTwoCoordinatesUsesTheRoadDistance() {
        val leg = travelLegBetween(-6.9, 107.6, -7.0, 107.6, MainTravelMode.MOTORCYCLE)
        assertEquals(14.455, leg.roadKm, 0.01)
        assertEquals(40, leg.minutes)
    }

    // ── warning ─────────────────────────────────────────────────────────────

    @Test
    fun aGapShorterThanTheLegWarnsAndNothingIsMoved() {
        val tight = farm.copy(startTime = LocalTime.parse("09:15")) // hotel ends 09:00: a 15 minute gap
        val shown = legBetween(hotel, tight, MainTravelMode.MOTORCYCLE)
        assertIs<LegDisplay.Leg>(shown)
        assertEquals(TravelWarning(gapMinutes = 15, needMinutes = 40), shown.warning)
        assertEquals(LocalTime.parse("09:15"), tight.startTime)
    }

    @Test
    fun aGapThatIsEnoughOrAnEqualOneDoesNotWarn() {
        assertNull((legBetween(hotel, farm, MainTravelMode.MOTORCYCLE) as LegDisplay.Leg).warning) // 60 minutes
        val exact = farm.copy(startTime = LocalTime.parse("09:40"))
        assertNull((legBetween(hotel, exact, MainTravelMode.MOTORCYCLE) as LegDisplay.Leg).warning)
    }

    @Test
    fun overlappingActivitiesWarnWithANegativeGap() {
        val overlap = farm.copy(startTime = LocalTime.parse("08:30"))
        val warning = (legBetween(hotel, overlap, MainTravelMode.MOTORCYCLE) as LegDisplay.Leg).warning
        assertEquals(-30, warning?.gapMinutes)
    }

    @Test
    fun noTimesOrNoEstimateMeansNoWarning() {
        assertNull((legBetween(hotel.copy(endTime = null), farm, MainTravelMode.CAR) as LegDisplay.Leg).warning)
        assertNull((legBetween(hotel, farm.copy(startTime = null), MainTravelMode.CAR) as LegDisplay.Leg).warning)
        val tightTransit = farm.copy(startTime = LocalTime.parse("09:05"))
        assertNull((legBetween(hotel, tightTransit, MainTravelMode.PUBLIC_TRANSPORT) as LegDisplay.Leg).warning, "no estimate to compare")
    }

    @Test
    fun theGapIsMeasuredFromTheRealEndOfAnActivityThatRunsPastMidnight() {
        val evening = item("makan malam", "20:00", "23:30", -6.9, 107.6)
        assertEquals(15, gapMinutesBetween(evening, farm.copy(startTime = LocalTime.parse("23:45"))))
        // 22:00 to 01:00 ends at 25:00: a start at 23:00 on the same day overlaps it by two hours
        val night = item("bar", "22:00", "01:00", -6.9, 107.6)
        assertEquals(-120, gapMinutesBetween(night, farm.copy(startTime = LocalTime.parse("23:00"))))
    }

    // ── points and areas ────────────────────────────────────────────────────

    @Test
    fun areaKindsAreCityRegencyProvinceAndCountry() {
        listOf(PlaceKind.CITY, PlaceKind.REGENCY, PlaceKind.PROVINCE, PlaceKind.COUNTRY).forEach { assertEquals(true, it.isAreaLevel(), "$it") }
        listOf(PlaceKind.AREA, PlaceKind.ATTRACTION).forEach { assertEquals(false, it.isAreaLevel(), "$it") }
    }

    @Test
    fun anUnknownKindWithCoordinatesIsAPoint() {
        assertEquals(true, item("a", lat = 1.0, lng = 2.0).isPointPlace())
        assertEquals(false, item("b").isPointPlace())
        assertEquals(false, item("c", lat = 1.0, lng = 2.0, kind = PlaceKind.CITY).isPointPlace())
    }

    // ── Perjalanan activities ───────────────────────────────────────────────

    private val otwBandung = item("OTW Bandung", "09:00", "11:00", -6.9175, 107.6191, Category.TRANSPORT, PlaceKind.CITY)
    private val station = item("Stasiun Bandung", "09:00", "09:30", -6.9147, 107.6021, Category.TRANSPORT, PlaceKind.AREA)

    @Test
    fun nothingEverFollowsAPerjalananActivity() {
        assertEquals(LegDisplay.None, legBetween(otwBandung, farm, MainTravelMode.MOTORCYCLE))
        assertEquals(LegDisplay.None, legBetween(station, farm, MainTravelMode.MOTORCYCLE))
        assertEquals(LegDisplay.None, legBetween(station, item("no place"), MainTravelMode.MOTORCYCLE), "and no invitation either")
    }

    @Test
    fun beforeAPerjalananActivityOnlyAPointGetsACapsule() {
        assertEquals(LegDisplay.None, legBetween(hotel, otwBandung, MainTravelMode.MOTORCYCLE), "a city: no capsule")
        assertEquals(LegDisplay.None, legBetween(hotel, item("OTW", category = Category.TRANSPORT), MainTravelMode.MOTORCYCLE), "no place: no capsule and no invitation")
        assertIs<LegDisplay.Leg>(legBetween(hotel, station, MainTravelMode.MOTORCYCLE), "a station: a capsule")
    }

    @Test
    fun beforeAStationFromAnActivityWithoutAPlaceInvitesToAddOne() {
        assertEquals(LegDisplay.AddLocationPrompt, legBetween(item("makan siang"), station, MainTravelMode.MOTORCYCLE))
    }

    // ── ordinary activities ─────────────────────────────────────────────────

    @Test
    fun anActivityWithoutAPlaceInvitesToAddOneOnEitherSide() {
        assertEquals(LegDisplay.AddLocationPrompt, legBetween(hotel, item("makan siang"), MainTravelMode.CAR))
        assertEquals(LegDisplay.AddLocationPrompt, legBetween(item("makan siang"), hotel, MainTravelMode.CAR))
    }

    @Test
    fun aCityCentreIsNotMeasured() {
        assertEquals(LegDisplay.None, legBetween(hotel, item("jalan-jalan Bandung", lat = -6.9, lng = 107.6, kind = PlaceKind.CITY), MainTravelMode.CAR))
    }

    @Test
    fun theLegCarriesTheModeAndTheDistance() {
        val shown = legBetween(hotel, farm, MainTravelMode.CAR) as LegDisplay.Leg
        assertEquals(LegKind.CAR, shown.leg.kind)
        assertEquals(50, shown.leg.minutes)
        assertEquals(14.455, shown.leg.roadKm, 0.01)
    }

    @Test
    fun aDayHasOneCapsuleBetweenEachPairAndOnlyPointsGoToMaps() {
        val noPlace = item("makan siang", "11:00", "12:00")
        val city = item("Bandung", "12:00", "13:00", -6.9, 107.6, kind = PlaceKind.CITY)
        val day = listOf(hotel, farm, noPlace, city, station)
        val legs = dayLegs(day, MainTravelMode.MOTORCYCLE)
        assertEquals(4, legs.size)
        assertIs<LegDisplay.Leg>(legs[0])
        assertEquals(LegDisplay.AddLocationPrompt, legs[1])
        assertEquals(LegDisplay.AddLocationPrompt, legs[2])
        assertEquals(LegDisplay.None, legs[3], "after the city centre, before a station: the city is an area")
        assertEquals(emptyList(), dayLegs(listOf(hotel), MainTravelMode.CAR))
        assertEquals(listOf(MapsPoint(-6.9, 107.6), MapsPoint(-7.0, 107.6), MapsPoint(-6.9147, 107.6021)), dayRoutePoints(day))
    }
}
