package app.ransel.shared.domain

import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.Category
import app.ransel.shared.model.MainTravelMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun aWarningOverlapsWhenTheGapIsZeroOrLess() {
        assertTrue(TravelWarning(gapMinutes = 0, needMinutes = 15).overlaps)
        assertTrue(TravelWarning(gapMinutes = -10, needMinutes = 15).overlaps)
        assertTrue(!TravelWarning(gapMinutes = 5, needMinutes = 15).overlaps)
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
    fun aDayHasOneCapsuleBetweenEachPairAndStopsAreTheActivitiesWithAPlace() {
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
        // an activity with no place is skipped; a city centre is still a stop (only a Perjalanan one in a city is not)
        assertEquals(
            listOf(MapsPoint(-6.9, 107.6), MapsPoint(-7.0, 107.6), MapsPoint(-6.9, 107.6), MapsPoint(-6.9147, 107.6021)),
            dayRoutePoints(day),
        )
    }

    @Test
    fun aPerjalananActivityInACityIsNotAStopButOneAtAStationIs() {
        val otwBandung = item("OTW Bandung", "09:00", "12:00", -6.9, 107.6, category = Category.TRANSPORT, kind = PlaceKind.CITY)
        val atStation = item("Kereta", "09:00", "12:00", -6.9147, 107.6021, category = Category.TRANSPORT, kind = PlaceKind.AREA)
        assertEquals(false, otwBandung.isMapStop())
        assertEquals(true, atStation.isMapStop())
        assertEquals(listOf(hotel, atStation), dayStops(listOf(hotel, otwBandung, atStation)))
    }

    @Test
    fun theSheetCardsHaveATimePerModeButNoneForTransitOrFar() {
        assertEquals(15, travelMinutesFor(1.1, LegKind.WALK))
        assertEquals(5, travelMinutesFor(1.1, LegKind.MOTORCYCLE))
        assertEquals(10, travelMinutesFor(3.0, LegKind.CAR))
        assertNull(travelMinutesFor(3.0, LegKind.PUBLIC_TRANSPORT))
        assertNull(travelMinutesFor(80.0, LegKind.FAR))
    }
}

class PlaceCityOfTest {
    @Test
    fun aCityOrRegencyIsItsOwnCity() {
        assertEquals("Bandung", placeCityOf(PlaceKind.CITY, " Bandung ", "Jawa Barat"))
        assertEquals("Badung", placeCityOf(PlaceKind.REGENCY, "Badung", "Bali"))
    }

    @Test
    fun aProvinceOrACountryHasNoCity() {
        assertEquals(null, placeCityOf(PlaceKind.PROVINCE, "Bali", "Indonesia"))
        assertEquals(null, placeCityOf(PlaceKind.COUNTRY, "Jepang", null))
    }

    @Test
    fun anyOtherPlaceTakesTheFirstPartOfTheSecondLine() {
        assertEquals("Labuan Bajo", placeCityOf(PlaceKind.AREA, "Hotel Bajo Beach", "Labuan Bajo, Manggarai Barat"))
        assertEquals("Ubud", placeCityOf(PlaceKind.ATTRACTION, "Monkey Forest", "Ubud"))
        assertEquals("Ubud", placeCityOf(null, "Warung", " Ubud , Bali"))
        assertEquals(null, placeCityOf(PlaceKind.AREA, "Warung", null))
        assertEquals(null, placeCityOf(PlaceKind.AREA, "Warung", " , Bali"))
    }
}

class EffectiveMainTravelModeTest {
    private val trip = TripDto(
        id = "t", ownerId = "u", title = "x", destination = "Bali", baseCurrency = "IDR",
        createdAt = kotlin.time.Instant.fromEpochSeconds(0), updatedAt = kotlin.time.Instant.fromEpochSeconds(0),
    )

    @Test
    fun theStoredModeWins() {
        assertEquals(MainTravelMode.CAR, trip.copy(mainTravelMode = MainTravelMode.CAR, departureMode = DepartureMode.OWN_MOTORCYCLE).effectiveMainTravelMode())
    }

    @Test
    fun withoutOneTheAnswersDecideThenTheDefault() {
        assertEquals(MainTravelMode.MOTORCYCLE, trip.effectiveMainTravelMode())
        assertEquals(MainTravelMode.CAR, trip.copy(countryCode = "JP").effectiveMainTravelMode())
        assertEquals(MainTravelMode.CAR, trip.copy(departureMode = DepartureMode.OWN_CAR).effectiveMainTravelMode())
        assertEquals(MainTravelMode.PUBLIC_TRANSPORT, trip.copy(localTransport = LocalTransport.PUBLIC_TRANSPORT).effectiveMainTravelMode())
    }
}

class DayMapTest {
    private fun stop(id: String, lat: Double, lng: Double, city: String?, category: Category = Category.ATTRACTION, kind: PlaceKind? = PlaceKind.AREA) =
        item(id, lat = lat, lng = lng, category = category, kind = kind).copy(placeCity = city)

    private val sunda = stop("sunda", -6.20, 106.80, "Jakarta")
    private val gambir = stop("gambir", -6.17, 106.83, "Jakarta", Category.TRANSPORT)
    private val dago = stop("dago", -6.88, 107.61, "Bandung")
    private val lembang = stop("lembang", -6.81, 107.62, "Bandung")

    @Test
    fun linesJoinConsecutiveStopsThatHaveACapsuleBetweenThem() {
        // after the train station (a Perjalanan activity) there is no capsule, so no line
        val segments = daySegments(listOf(sunda, gambir, dago, lembang), MainTravelMode.MOTORCYCLE)
        assertEquals(listOf("sunda" to "gambir", "dago" to "lembang"), segments.map { it.from.id to it.to.id })
    }

    @Test
    fun citiesAreCountedInTheOrderTheyFirstAppear() {
        assertEquals(listOf(DayCity("Jakarta", 2), DayCity("Bandung", 2)), dayCities(listOf(sunda, gambir, dago, lembang)))
        assertEquals(emptyList(), dayCities(listOf(item("x", lat = 1.0, lng = 1.0))))
        assertEquals(listOf(DayCity("Bandung", 2)), dayCities(listOf(dago, lembang.copy(placeCity = " bandung "))))
    }

    @Test
    fun theDefaultCityIsTheDestinationsElseTheFirst() {
        val cities = listOf(DayCity("Jakarta", 2), DayCity("Bandung", 2))
        assertEquals("Bandung", defaultDayCity(cities, "Bandung"))
        assertEquals("Jakarta", defaultDayCity(cities, "Bali"))
        assertNull(defaultDayCity(emptyList(), "Bandung"))
    }

    @Test
    fun aStopWithoutACityStaysInEveryCity() {
        val nowhere = item("nowhere", lat = 1.0, lng = 1.0)
        assertEquals(listOf("dago", "nowhere"), stopsInCity(listOf(sunda, dago, nowhere), "Bandung").map { it.id })
    }

    @Test
    fun aPerjalananActivityBetweenTwoCitiesMakesTheTransferRow() {
        val otw = item("OTW Bandung", "09:00", "12:00", category = Category.TRANSPORT).copy(placeCity = null)
        val day = listOf(sunda, otw, dago)
        val transfer = intercityTransfers(day).single()
        assertEquals("OTW Bandung", transfer.item.id)
        assertEquals("Jakarta" to "Bandung", transfer.fromCity to transfer.toCity)
        // the same city twice, or no Perjalanan activity between: no row
        assertEquals(emptyList(), intercityTransfers(listOf(dago, lembang)))
        assertEquals(emptyList(), intercityTransfers(listOf(sunda, dago)))
    }

    @Test
    fun theTrainStationItselfCanBeTheTransfer() {
        assertEquals("gambir", intercityTransfers(listOf(sunda, gambir, dago)).single().item.id)
    }
}
