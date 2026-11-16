package app.ransel.feature

import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.feature.destination.DestinationChoice
import app.ransel.feature.interview.InterviewEvent
import app.ransel.feature.interview.InterviewScreen
import app.ransel.feature.interview.InterviewViewModel
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.domain.InterviewStep
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.dto.TripPlaceDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.Interest
import app.ransel.shared.model.MainTravelMode
import app.ransel.stopTestMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val bandung = DestinationChoice("Bandung", TripPlaceDto("p-bdg", -6.9175, 107.6191), countryCode = "ID", timeZone = "Asia/Jakarta")
private val tokyo = DestinationChoice("Tokyo", TripPlaceDto("p-tyo", 35.6762, 139.6503), countryCode = "JP", timeZone = "Asia/Tokyo")
private val subang = TripOriginDto("Subang", -6.5694, 107.7606, countryCode = "ID")

private suspend fun <T> eventually(block: () -> T?): T = withContext(Dispatchers.Default) {
    withTimeout(5_000) {
        var value = block()
        while (value == null) { delay(10); value = block() }
        value
    }
}

class InterviewViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private fun vm(door: InterviewDoor = InterviewDoor.MY_TRIPS, store: InMemoryStore = InMemoryStore()) =
        InterviewViewModel(door, InMemoryTripRepository(store), InMemoryPreferencesRepository(), api = null, session = null).also {
            it.setNameTemplate("Trip ke %s")
        }

    private fun InterviewViewModel.layar1(choice: DestinationChoice = bandung) {
        onDestinationPicked(choice)
        onDates(LocalDate(2026, 12, 20), LocalDate(2026, 12, 22))
    }

    @Test
    fun subangToBandungByMotorcycleSkipsW3AndSavesTheAnswers() = runTest {
        val store = InMemoryStore()
        val vm = vm(store = store)
        vm.layar1()
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.ORIGIN), vm.state.value.screen)
        vm.pickOrigin(subang)
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.DEPARTURE), vm.state.value.screen)
        vm.chooseDeparture(DepartureMode.OWN_MOTORCYCLE)
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.TASTE), vm.state.value.screen, "W3 is skipped for an own vehicle")
        vm.toggleInterest(Interest.FOOD)
        vm.next()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)

        vm.create()
        val created = eventually { store.trips.value.singleOrNull() }
        assertEquals("Trip ke Bandung", created.title)
        assertEquals("Subang", created.origin?.name)
        assertEquals(DepartureMode.OWN_MOTORCYCLE, created.departureMode)
        assertNull(created.localTransport)
        assertEquals(MainTravelMode.MOTORCYCLE, created.mainTravelMode)
        assertEquals("p-bdg", created.destinationPlace?.placeId)
        assertEquals(LocalDate(2026, 12, 20), created.startDate)
    }

    @Test
    fun theCreatedEventSaysWhetherTheUserIsSignedIn() = runTest {
        val vm = vm()
        vm.layar1()
        vm.skipInterviewFromSummaryPath()
        vm.create()
        val event = vm.eventFlow.first()
        assertTrue(event is InterviewEvent.Created && !event.signedIn, "a guest: no Ajak teman")
    }

    /** From Layar 1 to W1, then "Lewati wawancara" to the summary. */
    private fun InterviewViewModel.skipInterviewFromSummaryPath() {
        next()
        skipInterview()
    }

    @Test
    fun skipTheInterviewGoesToTheSummaryFromMyTripsAndToW4FromJelajah() = runTest {
        val mine = vm()
        mine.layar1(); mine.next(); mine.skipInterview()
        assertEquals(InterviewScreen.Summary, mine.state.value.screen)

        val explore = vm(InterviewDoor.EXPLORE)
        explore.layar1(); explore.next(); explore.skipInterview()
        assertEquals(InterviewScreen.Question(InterviewStep.TASTE), explore.state.value.screen)
    }

    @Test
    fun lewatiStoresNothingAndMovesOn() = runTest {
        val vm = vm()
        vm.layar1(); vm.next()
        vm.skip()
        assertNull(vm.state.value.answers.origin)
        assertEquals(InterviewScreen.Question(InterviewStep.DEPARTURE), vm.state.value.screen)
        // W2 skipped: W3 is still asked (nothing says it is an own vehicle), then W4
        vm.skip()
        assertEquals(InterviewScreen.Question(InterviewStep.LOCAL_TRANSPORT), vm.state.value.screen)
        vm.skip(); vm.skip()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)
        assertTrue(vm.state.value.answers.taste.isEmpty)
    }

    @Test
    fun w4FromJelajahCannotBeSkippedAndNeedsAnInterest() = runTest {
        val vm = vm(InterviewDoor.EXPLORE)
        vm.layar1(); vm.next(); vm.skipInterview()
        vm.skip()
        assertEquals(InterviewScreen.Question(InterviewStep.TASTE), vm.state.value.screen)
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.TASTE), vm.state.value.screen, "no interest yet")
        vm.toggleInterest(Interest.NATURE)
        vm.next()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)
    }

    @Test
    fun anOverseasTripSkipsW2() = runTest {
        val vm = vm()
        vm.layar1(tokyo)
        vm.next()
        vm.pickOrigin(subang)
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.LOCAL_TRANSPORT), vm.state.value.screen)
    }

    @Test
    fun backOnLayar1AsksOnlyWhenSomethingWasFilledIn() = runTest {
        val empty = vm()
        empty.back()
        assertEquals(InterviewEvent.Leave, empty.eventFlow.first())

        val filled = vm()
        filled.onDestinationPicked(bandung)
        filled.back()
        assertTrue(filled.state.value.leaveDialog)
        filled.dismissLeave()
        assertFalse(filled.state.value.leaveDialog)
    }

    @Test
    fun backGoesToTheQuestionBeforeAndKeepsTheAnswer() = runTest {
        val vm = vm()
        vm.layar1(); vm.next(); vm.pickOrigin(subang); vm.next()
        vm.back()
        assertEquals(InterviewScreen.Question(InterviewStep.ORIGIN), vm.state.value.screen)
        assertEquals("Subang", vm.state.value.answers.origin?.name)
    }

    @Test
    fun modeUbahChangesARowAndBackPutsTheAnswerBack() = runTest {
        val vm = vm()
        vm.layar1(); vm.next(); vm.pickOrigin(subang); vm.next()
        vm.chooseDeparture(DepartureMode.BUS); vm.next()
        vm.skip(); vm.skip()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)

        vm.edit(InterviewStep.DEPARTURE)
        vm.chooseDeparture(DepartureMode.OWN_CAR)
        vm.back()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)
        assertEquals(DepartureMode.BUS, vm.state.value.answers.departure, "cancelled")

        vm.edit(InterviewStep.DEPARTURE)
        vm.chooseDeparture(DepartureMode.OWN_CAR)
        vm.next()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)
        assertEquals(DepartureMode.OWN_CAR, vm.state.value.answers.departure, "saved")
        assertNull(vm.state.value.editing)
    }

    @Test
    fun theNameDefaultsToTripToTheDestinationUntilTheUserTypesOne() = runTest {
        val vm = vm()
        vm.layar1()
        assertEquals("Trip ke Bandung", vm.state.value.tripName)
        vm.onNameChange("Liburan Dago")
        assertEquals("Liburan Dago", vm.state.value.tripName)
    }
}
