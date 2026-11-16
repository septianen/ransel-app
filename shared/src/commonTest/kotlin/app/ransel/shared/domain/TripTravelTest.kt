package app.ransel.shared.domain

import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.dto.TripPlaceDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.MainTravelMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val subang = TripOriginDto("Subang", -6.5694, 107.7606, countryCode = "ID")
private val jakarta = TripOriginDto("Jakarta", -6.2088, 106.8456, countryCode = "ID")
private val medan = TripOriginDto("Medan", 3.5952, 98.6722, countryCode = "ID")
private val tokyoOrigin = TripOriginDto("Tokyo", 35.6762, 139.6503, countryCode = "JP")

private fun trip(
    destination: String = "Bandung",
    place: TripPlaceDto? = TripPlaceDto("p-bdg", -6.9175, 107.6191),
    country: String? = "ID",
    origin: TripOriginDto? = subang,
    departure: DepartureMode? = DepartureMode.OWN_MOTORCYCLE,
    local: LocalTransport? = null,
    main: MainTravelMode? = MainTravelMode.MOTORCYCLE,
) = TripDto(
    id = "t", ownerId = "u", title = "Trip ke $destination", destination = destination, baseCurrency = "IDR",
    destinationPlace = place, countryCode = country, origin = origin, departureMode = departure, localTransport = local,
    mainTravelMode = main,
    createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
)

class TripTravelTest {
    @Test
    fun aNewStartingCityThatMakesTheMotorbikeImpossibleResetsTheAnswer() {
        // Jakarta to Bandung is about 150 km by road: the motorbike is no longer among the choices
        val edit = trip().changeOrigin(jakarta)
        assertNull(edit.answers.departure)
        assertEquals(DepartureMode.OWN_MOTORCYCLE, edit.departureReset, "the dialog names what was reset")
        // the vehicle is gone: getting around goes back to the default of the plan
        assertEquals(MainTravelMode.MOTORCYCLE, edit.mainTravelMode)
        assertEquals("Jakarta", edit.answers.origin?.name)
    }

    @Test
    fun aStartingCityThatKeepsTheAnswerPossibleKeepsIt() {
        val edit = trip(departure = DepartureMode.BUS, main = null).changeOrigin(jakarta)
        assertEquals(DepartureMode.BUS, edit.answers.departure)
        assertNull(edit.departureReset)
    }

    @Test
    fun aStartingCityOnAnotherIslandOffersThePlaneFirstAndResetsTheMotorbike() {
        val edit = trip().changeOrigin(medan)
        assertNull(edit.answers.departure)
        assertEquals(DepartureMode.OWN_MOTORCYCLE, edit.departureReset)
    }

    @Test
    fun aStartingCityAbroadDropsTheAnswerWithoutADialogAndKeepsGettingAround() {
        // a taxi is a choice abroad as well; a ride-hailing app is not, so only the taxi would survive
        val t = trip(departure = DepartureMode.BUS, local = LocalTransport.RENT_CAR, main = MainTravelMode.CAR)
        val edit = t.changeOrigin(tokyoOrigin)
        assertNull(edit.answers.departure, "W2 is not asked abroad")
        assertNull(edit.departureReset, "no dialog")
        assertEquals(LocalTransport.RENT_CAR, edit.answers.localTransport)
    }

    @Test
    fun anOwnVehicleBringsItsOwnWayOfGettingAround() {
        val t = trip(departure = DepartureMode.BUS, local = LocalTransport.PUBLIC_TRANSPORT, main = MainTravelMode.PUBLIC_TRANSPORT)
        val edit = t.changeDeparture(DepartureMode.OWN_CAR)
        assertNull(edit.answers.localTransport, "the old answer is dropped")
        assertEquals(MainTravelMode.CAR, edit.mainTravelMode)
        assertTrue(edit.ownVehicleNow)
        assertFalse(edit.askLocalTransportNext)
    }

    @Test
    fun leavingTheOwnVehicleAsksForGettingAroundAndFallsBackToTheDefault() {
        val edit = trip(departure = DepartureMode.OWN_CAR, main = MainTravelMode.CAR).changeDeparture(DepartureMode.BUS)
        assertTrue(edit.askLocalTransportNext)
        assertNull(edit.answers.localTransport)
        assertEquals(MainTravelMode.MOTORCYCLE, edit.mainTravelMode, "Motor for a destination in Indonesia")
        assertFalse(edit.ownVehicleNow)
        // abroad the default is the car
        val abroad = trip("Tokyo", TripPlaceDto("p-tyo", 35.6762, 139.6503), "JP", jakarta, null, LocalTransport.TAXI, MainTravelMode.CAR)
        assertEquals(MainTravelMode.CAR, abroad.changeLocalTransport(null).mainTravelMode)
    }

    @Test
    fun betweenTwoWaysWithoutAVehicleGettingAroundDoesNotChange() {
        // the owner (or an editor) had chosen the car in the sheet; Bus to Kereta leaves it
        val t = trip(departure = DepartureMode.BUS, local = LocalTransport.RENT_MOTORCYCLE, main = MainTravelMode.CAR)
        val edit = t.changeDeparture(DepartureMode.TRAIN)
        assertEquals(MainTravelMode.CAR, edit.mainTravelMode)
        assertEquals(LocalTransport.RENT_MOTORCYCLE, edit.answers.localTransport)
        assertFalse(edit.askLocalTransportNext)
    }

    @Test
    fun gettingAroundSetsTheMainModeFromTheAnswer() {
        val t = trip(departure = DepartureMode.BUS, main = null)
        assertEquals(MainTravelMode.MOTORCYCLE, t.changeLocalTransport(LocalTransport.RENT_MOTORCYCLE).mainTravelMode)
        assertEquals(MainTravelMode.CAR, t.changeLocalTransport(LocalTransport.RENT_CAR).mainTravelMode)
        assertEquals(MainTravelMode.MOTORCYCLE, t.changeLocalTransport(LocalTransport.RIDE_HAILING).mainTravelMode, "ojol is a motorbike in Indonesia")
        assertEquals(MainTravelMode.MOTORCYCLE, t.changeLocalTransport(LocalTransport.UNKNOWN).mainTravelMode, "Belum tahu: the default")
        // Jakarta has public transport on Google Maps
        val jakartaTrip = trip("Jakarta", TripPlaceDto("p-jkt", -6.2088, 106.8456), "ID", medan, DepartureMode.PLANE, null, null)
        assertEquals(MainTravelMode.PUBLIC_TRANSPORT, jakartaTrip.changeLocalTransport(LocalTransport.PUBLIC_TRANSPORT).mainTravelMode)
    }

    @Test
    fun aNewDestinationAbroadDropsW2AndAGettingAroundItDoesNotOfferIsReset() {
        val t = trip(departure = DepartureMode.OWN_MOTORCYCLE, local = null, main = MainTravelMode.MOTORCYCLE)
        val edit = t.changeDestination(InterviewDestination("Tokyo", "p-tyo", 35.6762, 139.6503, "JP"))
        assertNull(edit.answers.departure)
        assertNull(edit.departureReset, "W2 does not exist abroad: no dialog")
        // the own vehicle is gone, so abroad the default is the car
        assertEquals(MainTravelMode.CAR, edit.mainTravelMode)
    }

    @Test
    fun theRequestHoldsTheWholeTripAndTheAnswersAsTheTruth() {
        val t = trip()
        val request = t.updateRequestFor(t.changeOrigin(jakarta))
        assertTrue(request.replaceTravelAnswers)
        assertNull(request.departureMode, "a reset is sent as a null that clears")
        assertEquals("Bandung", request.destination)
        assertEquals(t.destinationPlace, request.destinationPlace)
        val back = t.restoreRequest()
        assertEquals(DepartureMode.OWN_MOTORCYCLE, back.departureMode)
        assertEquals("Subang", back.origin?.name)
        assertTrue(back.replaceTravelAnswers)
    }

    @Test
    fun theRowsOfATripAbroadLeaveOutHowYouGetThere() {
        assertEquals(listOf(TravelRow.ORIGIN, TravelRow.DEPARTURE, TravelRow.LOCAL_TRANSPORT, TravelRow.TASTE), trip().travelRows())
        val tokyo = trip("Tokyo", TripPlaceDto("p-tyo", 35.6762, 139.6503), "JP", jakarta, null, LocalTransport.PUBLIC_TRANSPORT, null)
        assertEquals(listOf(TravelRow.ORIGIN, TravelRow.LOCAL_TRANSPORT, TravelRow.TASTE), tokyo.travelRows())
    }

    @Test
    fun gettingAroundFollowsAnOwnVehicleOnlyInIndonesia() {
        assertTrue(trip().localTransportFollowsVehicle())
        assertFalse(trip(departure = DepartureMode.BUS).localTransportFollowsVehicle())
        assertFalse(trip(departure = null).localTransportFollowsVehicle())
    }
}
