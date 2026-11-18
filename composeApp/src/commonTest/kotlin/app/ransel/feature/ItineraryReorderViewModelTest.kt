package app.ransel.feature

import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.repository.AppPreferences
import app.ransel.feature.itinerary.ItineraryEvent
import app.ransel.feature.itinerary.ItineraryViewModel
import app.ransel.shared.dto.DayOrderItem
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
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ItineraryReorderViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val day = LocalDate(2026, 11, 21)
    private val trip = TripDto(
        id = "t1", ownerId = "u1", title = "Makassar", destination = "Makassar",
        startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
        createdAt = Instant.parse("2026-10-01T00:00:00Z"), updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun activity(id: String, start: String, end: String, order: Double) = ItineraryItemInput(
        id = id, dayDate = day, startTime = LocalTime.parse(start), endTime = LocalTime.parse(end),
        title = id, category = Category.FOOD, sortOrder = order,
    )

    private suspend fun vmWith(
        vararg inputs: ItineraryItemInput,
        skipConfirm: Boolean = false,
    ): Pair<ItineraryViewModel, InMemoryItineraryRepository> {
        val itinerary = InMemoryItineraryRepository(InMemoryStore())
        inputs.forEach { itinerary.save(trip.id, it) }
        val prefs = InMemoryPreferencesRepository(AppPreferences(skipReorderConfirmation = skipConfirm))
        val vm = ItineraryViewModel(trip, itinerary, prefs)
        vm.selectDay(day)
        vm.state.first { it.selectedItems.size == inputs.size && it.selectedDay == day }
        return vm to itinerary
    }

    @Test
    fun droppingOnTheOriginalSlotDoesNotWrite() = runTest {
        val (vm, itinerary) = vmWith(
            activity("coto", "09:00", "09:45", 1.0),
            activity("benteng", "09:45", "11:15", 2.0),
        )
        val before = itinerary.observe(trip.id).first().map { it.id to it.sortOrder }
        vm.onReorder(day, from = 0, to = 0)
        assertNull(vm.state.value.pendingReorder)
        assertEquals(before, itinerary.observe(trip.id).first().map { it.id to it.sortOrder })
    }

    @Test
    fun aTimeChangingDropAsksBeforeWriting() = runTest {
        val (vm, itinerary) = vmWith(
            activity("coto", "09:00", "09:45", 1.0),
            activity("benteng", "09:45", "11:15", 2.0),
        )
        vm.onReorder(day, from = 1, to = 0)
        val pending = assertNotNull(vm.state.value.pendingReorder)
        assertEquals(listOf("benteng", "coto"), pending.changes.map { it.item.id })
        val still = itinerary.observe(trip.id).first().sortedBy { it.sortOrder }
        assertEquals(listOf("coto", "benteng"), still.map { it.id })
        vm.confirmReorder(dontAskAgain = false)
        assertNull(vm.state.value.pendingReorder)
        val after = itinerary.observe(trip.id).first().sortedBy { it.sortOrder }
        assertEquals(listOf("benteng", "coto"), after.map { it.id })
        assertEquals(LocalTime(9, 0), after[0].startTime)
        assertTrue(vm.state.value.flashingIds.contains("benteng"))
    }

    @Test
    fun fiveReordersInARowEachStickWhenConfirmIsSkipped() = runTest {
        val (vm, itinerary) = vmWith(
            activity("a", "09:00", "09:30", 1.0),
            activity("b", "09:30", "10:00", 2.0),
            activity("c", "10:00", "10:30", 3.0),
            skipConfirm = true,
        )
        repeat(5) { i ->
            val from = i % 3
            val to = (from + 1) % 3
            vm.onReorder(day, from, to)
            assertNull(vm.state.value.pendingReorder, "dialog must not block a skipped-confirm drop")
        }
        val ids = itinerary.observe(trip.id).first().sortedBy { it.sortOrder }.map { it.id }
        assertEquals(3, ids.size)
        assertEquals(ids.toSet(), setOf("a", "b", "c"))
    }

    @Test
    fun aPullDuringDragDoesNotMoveTheVisibleRows() = runTest {
        val store = InMemoryStore()
        val itinerary = InMemoryItineraryRepository(store)
        itinerary.save(trip.id, activity("coto", "09:00", "09:45", 1.0))
        itinerary.save(trip.id, activity("benteng", "09:45", "11:15", 2.0))
        val vm = ItineraryViewModel(trip, itinerary, InMemoryPreferencesRepository())
        vm.selectDay(day)
        vm.state.first { it.selectedItems.size == 2 }
        vm.beginDrag()
        assertEquals(listOf("coto", "benteng"), vm.state.value.selectedItems.map { it.id })

        // a GET /sync overwrite of the same rows (stale order, or another device)
        val now = Instant.parse("2026-11-18T10:00:00Z")
        store.items.value = store.items.value.map { row ->
            when (row.id) {
                "coto" -> row.copy(sortOrder = 2.0, startTime = LocalTime(11, 15), updatedAt = now)
                "benteng" -> row.copy(sortOrder = 1.0, startTime = LocalTime(9, 0), updatedAt = now)
                else -> row
            }
        }
        assertEquals(
            listOf("coto", "benteng"),
            vm.state.value.selectedItems.map { it.id },
            "the held list must ignore Room until the finger is up",
        )
        vm.endDrag()
        assertEquals(listOf("benteng", "coto"), vm.state.value.selectedItems.map { it.id })
    }

    @Test
    fun undoRestoresThePackedDay() = runTest {
        val (vm, itinerary) = vmWith(
            activity("coto", "09:00", "09:45", 1.0),
            activity("benteng", "09:45", "11:15", 2.0),
            skipConfirm = true,
        )
        vm.onReorder(day, from = 1, to = 0)
        val event = vm.eventFlow.first() as ItineraryEvent.Reordered
        val packed = itinerary.observe(trip.id).first().sortedBy { it.sortOrder }
        assertEquals("benteng", packed.first().id)
        vm.undoReorder(event.day, event.restore)
        val restored = itinerary.observe(trip.id).first().sortedBy { it.sortOrder }
        assertEquals(listOf("coto", "benteng"), restored.map { it.id })
        assertEquals(LocalTime(9, 0), restored[0].startTime)
        assertEquals(LocalTime(9, 45), restored[1].startTime)
        val restoreIds = event.restore.map(DayOrderItem::id)
        assertEquals(listOf("coto", "benteng"), restoreIds)
    }
}
