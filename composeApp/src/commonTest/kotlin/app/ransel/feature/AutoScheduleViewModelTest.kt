package app.ransel.feature

import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.feature.itinerary.ItineraryEvent
import app.ransel.feature.itinerary.ItineraryViewModel
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Category
import app.ransel.stopTestMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AutoScheduleViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val trip = TripDto(
        id = "t1", ownerId = "u1", title = "Bali 3 hari", destination = "Bali",
        startDate = LocalDate(2099, 11, 20), endDate = LocalDate(2099, 11, 22), baseCurrency = "IDR",
        createdAt = Instant.parse("2026-10-01T00:00:00Z"), updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun place(id: String, lat: Double?, lng: Double?, category: Category = Category.ATTRACTION) =
        ItineraryItemInput(id = id, title = id, category = category, lat = lat, lng = lng, durationMin = 90)

    private suspend fun vmWith(vararg inputs: ItineraryItemInput): Pair<ItineraryViewModel, InMemoryItineraryRepository> {
        val itinerary = InMemoryItineraryRepository(InMemoryStore())
        inputs.forEach { itinerary.save("t1", it) }
        val vm = ItineraryViewModel(trip, itinerary, InMemoryPreferencesRepository())
        vm.state.first { it.unscheduled.size == inputs.size }
        return vm to itinerary
    }

    @Test
    fun theDialogPreviewShowsWhereThePlacesWouldGoAndSavesNothing() = runTest {
        val (vm, itinerary) = vmWith(place("a", -8.50, 115.26), place("b", -8.51, 115.26), place("m", null, null))
        val plan = assertNotNull(vm.previewAutoSchedule())
        assertEquals(3, plan.count)
        assertEquals(0, plan.leftOver)
        assertTrue(itinerary.observe("t1").first().all { it.dayDate == null }, "preview does not save")
    }

    @Test
    fun runningItSchedulesEveryPlaceAndUndoPutsThemBack() = runTest {
        val (vm, itinerary) = vmWith(place("a", -8.50, 115.26), place("b", -8.51, 115.26))
        vm.runAutoSchedule()
        val event = vm.eventFlow.first()
        val done = event as ItineraryEvent.AutoScheduled
        assertEquals(2, done.count)
        val saved = itinerary.observe("t1").first()
        assertTrue(saved.all { it.dayDate != null && it.startTime != null && it.endTime != null })
        assertEquals(false, vm.state.value.autoScheduling)

        vm.undoAutoSchedule(done.restore)
        val back = itinerary.observe("t1").first()
        assertTrue(back.all { it.dayDate == null && it.startTime == null })
    }

    @Test
    fun nothingToScheduleGivesNoPlan() = runTest {
        val itinerary = InMemoryItineraryRepository(InMemoryStore())
        val vm = ItineraryViewModel(trip, itinerary, InMemoryPreferencesRepository())
        vm.state.first { !it.loading }
        assertNull(vm.previewAutoSchedule())
    }
}
