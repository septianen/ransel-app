package app.ransel.feature

import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.travel.TravelAnswerChanges
import app.ransel.feature.tripform.travel.TripAnswerEvent
import app.ransel.feature.tripform.travel.TripAnswerViewModel
import app.ransel.feature.tripform.travel.TripTravelViewModel
import app.ransel.shared.domain.TravelRow
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.dto.TripPlaceDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.Interest
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.MainTravelMode
import app.ransel.shared.model.PriceLevel
import app.ransel.stopTestMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val subang = TripOriginDto("Subang", -6.5694, 107.7606, countryCode = "ID")
private val jakarta = TripOriginDto("Jakarta", -6.2088, 106.8456, countryCode = "ID")

private suspend fun <T : Any> until(block: () -> T?): T = withTimeout(5_000) {
    var value = block()
    while (value == null) { delay(10); value = block() }
    value
}

/** "Edit trip, Perjalanan" (spec 1638556): changing an answer of a trip that exists, with the reset, the undo and the Seleramu. */
class TripTravelEditTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val store = InMemoryStore()
    private val trips = InMemoryTripRepository(store)
    private val explore = ExploreSessionStore(InMemoryPreferencesRepository())
    private val changes = TravelAnswerChanges()

    private suspend fun createSubangToBandung() = trips.create(
        CreateTripRequest(
            id = "t1", title = "Trip ke Bandung", destination = "Bandung", startDate = LocalDate(2026, 12, 20), endDate = LocalDate(2026, 12, 22),
            baseCurrency = "IDR", destinationPlace = TripPlaceDto("p-bdg", -6.9175, 107.6191), countryCode = "ID",
            origin = subang, departureMode = DepartureMode.OWN_MOTORCYCLE, mainTravelMode = MainTravelMode.MOTORCYCLE,
        ),
    )

    private fun answer(row: TravelRow) = TripAnswerViewModel("t1", row, trips, api = null, session = null, changes = changes)
    private fun section() = TripTravelViewModel("t1", trips, explore, api = null, session = null, preferences = InMemoryPreferencesRepository(), answerChanges = changes)

    private fun trip() = store.trips.value.single()

    @Test
    fun aNewStartingCityThatMakesTheAnswerImpossibleResetsItAndSaysSo() = runTest {
        createSubangToBandung()
        val vm = answer(TravelRow.ORIGIN)
        until { vm.state.value.trip }
        vm.pickOrigin(jakarta)
        vm.save()
        assertEquals(TripAnswerEvent.Done, vm.eventFlow.first())
        val saved = trip()
        assertEquals("Jakarta", saved.origin?.name)
        assertNull(saved.departureMode, "reset: Jakarta to Bandung is too far for a motorbike")
        val change = changes.changes.first()
        assertEquals(DepartureMode.OWN_MOTORCYCLE, change.resetDeparture, "the dialog names it")
        // undo puts every answer back, the reset one too
        section().undo(change)
        until { trip().departureMode }
        assertEquals("Subang", trip().origin?.name)
        assertEquals(DepartureMode.OWN_MOTORCYCLE, trip().departureMode)
    }

    @Test
    fun leavingTheOwnVehicleOpensGettingAroundNextAndTheDefaultMainModeApplies() = runTest {
        createSubangToBandung()
        val vm = answer(TravelRow.DEPARTURE)
        until { vm.state.value.trip }
        vm.chooseDeparture(DepartureMode.BUS)
        vm.save()
        assertEquals(TripAnswerEvent.OpenLocalTransport, vm.eventFlow.first())
        assertEquals(DepartureMode.BUS, trip().departureMode)
        assertNull(trip().localTransport)
        assertEquals(MainTravelMode.MOTORCYCLE, trip().mainTravelMode)
    }

    @Test
    fun anOwnVehicleDropsGettingAroundAndTheMainModeFollowsIt() = runTest {
        createSubangToBandung()
        val bus = answer(TravelRow.DEPARTURE)
        until { bus.state.value.trip }
        bus.chooseDeparture(DepartureMode.BUS); bus.save(); bus.eventFlow.first()
        val local = answer(TravelRow.LOCAL_TRANSPORT)
        until { local.state.value.trip?.departureMode == DepartureMode.BUS }
        assertTrue(!local.state.value.hasChoice, "Simpan stays off until a way is picked")
        local.chooseLocalTransport(LocalTransport.RENT_MOTORCYCLE)
        assertTrue(local.state.value.hasChoice)
        local.save(); local.eventFlow.first()
        assertEquals(LocalTransport.RENT_MOTORCYCLE, trip().localTransport)

        val car = answer(TravelRow.DEPARTURE)
        until { car.state.value.trip?.localTransport == LocalTransport.RENT_MOTORCYCLE }
        car.chooseDeparture(DepartureMode.OWN_CAR); car.save(); car.eventFlow.first()
        assertNull(trip().localTransport, "dropped: the car is the way of getting around")
        assertEquals(MainTravelMode.CAR, trip().mainTravelMode)
    }

    @Test
    fun savingWithoutAChangeOnlyCloses() = runTest {
        createSubangToBandung()
        val vm = answer(TravelRow.DEPARTURE)
        until { vm.state.value.trip }
        val updatedBefore = trip().updatedAt
        vm.save()
        assertEquals(TripAnswerEvent.Done, vm.eventFlow.first())
        assertEquals(updatedBefore, trip().updatedAt, "nothing was written")
    }

    @Test
    fun theSeleramuIsStoredForTheTripAndUndoPutsItBack() = runTest {
        createSubangToBandung()
        val vm = section()
        until { vm.state.value.trip }
        assertNull(vm.state.value.taste)
        vm.openTasteSheet()
        vm.draftToggleInterest(Interest.FOOD)
        vm.draftPriceLevel(PriceLevel.HIGH)
        vm.saveTasteSheet()
        val form = until { explore.taste("t1") }
        assertEquals(listOf(Interest.FOOD), form.interests)
        assertEquals(PriceLevel.HIGH, form.priceLevel)
        val change = changes.changes.first()
        assertTrue(change.tasteChanged)
        assertNull(change.undoTaste, "there was none before")
        vm.undo(change)
        until { if (explore.taste("t1") == null) true else null }
        assertNull(vm.state.value.taste)
    }

    @Test
    fun editTripRefreshesTasteSavedInTheSeparateTasteScreen() = runTest {
        createSubangToBandung()
        val editTrip = section()
        until { editTrip.state.value.trip }
        assertNull(editTrip.state.value.taste)

        val tasteScreen = section()
        tasteScreen.openTasteSheet()
        tasteScreen.draftToggleInterest(Interest.FOOD)
        tasteScreen.saveTasteSheet()
        until { explore.taste("t1") }

        assertNull(editTrip.state.value.taste, "the edit screen keeps its own ViewModel while the taste screen is open")
        editTrip.refreshTaste()
        assertEquals(setOf(Interest.FOOD), editTrip.state.value.taste?.interests)
    }

    @Test
    fun theSheetNeedsAnInterestToBeSaved() = runTest {
        createSubangToBandung()
        val vm = section()
        until { vm.state.value.trip }
        vm.openTasteSheet()
        vm.draftNotes("hindari trekking")
        vm.saveTasteSheet()
        assertTrue(vm.state.value.draft != null, "still open: Jelajah cannot search without an interest")
        assertNull(explore.taste("t1"))
    }
}
