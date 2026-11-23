package app.ransel.feature

import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.feature.destination.DestinationChoice
import app.ransel.feature.interview.InterviewEvent
import app.ransel.feature.interview.InterviewViewModel
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.dto.TripPlaceDto
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
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

private val bandungChoice = DestinationChoice("Bandung", TripPlaceDto("p-bdg", -6.9175, 107.6191), countryCode = "ID", timeZone = "Asia/Jakarta")

private suspend fun <T : Any> waitFor(block: () -> T?): T = withTimeout(5_000) {
    var value = block()
    while (value == null) { delay(10); value = block() }
    value
}

/** The Jelajah door of "Buat trip" and the Seleramu of a trip (step 5): the answers of W4 and the "Ubah seleramu" sheet. */
class InterviewJelajahTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val tripsStore = InMemoryStore()
    private val explore = ExploreSessionStore(InMemoryPreferencesRepository())

    private fun vm(door: InterviewDoor) =
        InterviewViewModel(door, InMemoryTripRepository(tripsStore), InMemoryPreferencesRepository(), api = null, session = null, explore = explore)
            .also { it.setNameTemplate("Trip ke %s") }

    private fun InterviewViewModel.toW4() {
        onDestinationPicked(bandungChoice)
        onDates(LocalDate(2026, 12, 20), LocalDate(2026, 12, 22))
        next()
        skipInterview()
    }

    @Test
    fun w4AnswersBecomeTheSeleramuOfTheTripOnBothDoors() = runTest {
        val mine = vm(InterviewDoor.MY_TRIPS)
        mine.onDestinationPicked(bandungChoice); mine.onDates(LocalDate(2026, 12, 20), LocalDate(2026, 12, 22))
        mine.next(); mine.skipInterview()
        mine.create()
        val trip = waitFor { tripsStore.trips.value.singleOrNull() }
        // My trips: W4 was skipped, so there is nothing to keep
        assertNull(explore.taste(trip.id))

        val vm = vm(InterviewDoor.EXPLORE)
        vm.toW4()
        vm.toggleInterest(Interest.FOOD)
        vm.chooseStyle(TravelStyle.PADAT)
        vm.next()
        vm.create()
        val created = waitFor { tripsStore.trips.value.firstOrNull { it.id != trip.id } }
        val form = waitFor { explore.taste(created.id) }
        assertEquals(listOf(Interest.FOOD), form.interests)
        assertEquals(TravelStyle.PADAT, form.style)
        assertEquals(PriceLevel.MEDIUM, form.priceLevel, "Sedang until the sheet says otherwise")
        assertEquals("Bandung", form.destination)
        assertEquals(LocalDate(2026, 12, 20), form.startDate)
    }

    @Test
    fun aGuestOrOfflineFromJelajahOnlyMakesTheTripAndHasNoAjakTeman() = runTest {
        val vm = vm(InterviewDoor.EXPLORE)
        vm.toW4()
        vm.toggleInterest(Interest.NATURE)
        vm.next()
        vm.create()
        val event = vm.eventFlow.first()
        assertTrue(event is InterviewEvent.Created && !event.signedIn, "no search without an account or a connection")
    }

    @Test
    fun theSheetChangesTheDraftAndSimpanAppliesItOnlyWithAnInterest() = runTest {
        val vm = vm(InterviewDoor.EXPLORE)
        vm.toW4()
        vm.toggleInterest(Interest.FOOD)
        vm.next()

        vm.openTasteSheet()
        vm.draftPriceLevel(PriceLevel.HIGH)
        vm.draftNotes("hindari trekking")
        // closing drops the draft
        vm.closeTasteSheet()
        assertEquals(PriceLevel.MEDIUM, vm.state.value.answers.taste.effectivePriceLevel)

        vm.openTasteSheet()
        vm.draftPriceLevel(PriceLevel.HIGH)
        vm.draftNotes("hindari trekking")
        vm.draftToggleInterest(Interest.FOOD) // the last interest off: Simpan does nothing
        vm.saveTasteSheet()
        assertTrue(vm.state.value.tasteDraft != null, "still open: it needs an interest")
        vm.draftToggleInterest(Interest.NATURE)
        vm.saveTasteSheet()
        assertNull(vm.state.value.tasteDraft)
        assertEquals(PriceLevel.HIGH, vm.state.value.answers.taste.effectivePriceLevel)
        assertEquals("hindari trekking", vm.state.value.answers.taste.notes)
        assertEquals(setOf(Interest.NATURE), vm.state.value.answers.taste.interests)
    }

    @Test
    fun theNoteStopsAtFiveHundredCharacters() = runTest {
        val vm = vm(InterviewDoor.EXPLORE)
        vm.toW4(); vm.toggleInterest(Interest.FOOD); vm.next()
        vm.openTasteSheet()
        vm.draftNotes("x".repeat(520))
        assertEquals(500, vm.state.value.tasteDraft?.notes?.length)
    }

    @Test
    fun theFormOfAnExistingTripStartsWithItsSeleramu() = runTest {
        val vm = vm(InterviewDoor.EXPLORE)
        vm.toW4(); vm.toggleInterest(Interest.FOOD); vm.next()
        vm.create()
        val trip = waitFor { tripsStore.trips.value.singleOrNull() }
        waitFor { explore.taste(trip.id) }
        assertEquals(listOf(Interest.FOOD), explore.takeLastForm(trip.id)?.interests)
    }
}
