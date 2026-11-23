package app.ransel.feature

import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.inspiration.AddIdeaToTripRequests
import app.ransel.feature.inspiration.AddIdeaToTripEvent
import app.ransel.feature.inspiration.AddIdeaToTripViewModel
import app.ransel.feature.inspiration.unscheduledItemFromIdea
import app.ransel.feature.trips.canAddIdeaToExistingTrip
import app.ransel.feature.trips.tripsThatCanReceiveIdea
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.Role
import app.ransel.stopTestMain
import app.ransel.util.plusDays
import app.ransel.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AddIdeaToTripVisibilityTest {
    private val today = LocalDate(2026, 11, 23)

    @Test
    fun hiddenWhenTheUserHasNoTrip() {
        assertFalse(canAddIdeaToExistingTrip(emptyList(), today))
    }

    @Test
    fun hiddenWhenEveryTripHasFinished() {
        assertFalse(canAddIdeaToExistingTrip(listOf(trip("done", today.plusDays(-10), today.plusDays(-1))), today))
    }

    @Test
    fun hiddenWhenTheUserCanOnlyView() {
        assertFalse(canAddIdeaToExistingTrip(listOf(trip("v", today, today.plusDays(3), Role.VIEWER)), today))
    }

    @Test
    fun shownForOneUpcomingOrOngoingOrUndatedTrip() {
        assertTrue(canAddIdeaToExistingTrip(listOf(trip("soon", today.plusDays(5), today.plusDays(8))), today))
        assertTrue(canAddIdeaToExistingTrip(listOf(trip("now", today, today.plusDays(2))), today))
        assertTrue(canAddIdeaToExistingTrip(listOf(trip("open", null, null)), today))
        assertTrue(canAddIdeaToExistingTrip(listOf(trip("soon", today.plusDays(5), today.plusDays(8), Role.EDITOR)), today))
    }

    @Test
    fun finishedAndViewerTripsAreLeftOutOfThePicker() {
        val trips = listOf(
            trip("done", today.plusDays(-10), today.plusDays(-1)),
            trip("view", today.plusDays(2), today.plusDays(5), Role.VIEWER),
            trip("go", today.plusDays(1), today.plusDays(4)),
        )
        assertEquals(listOf("go"), tripsThatCanReceiveIdea(trips, today).map { it.id })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddIdeaToTripViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    @Test
    fun addingPutsAnUnscheduledItemOnThePickedTrip() = runTest {
        val store = InMemoryStore()
        val tripId = "trip-1"
        store.trips.value = listOf(trip(tripId, LocalDate(2026, 12, 1), LocalDate(2026, 12, 4)))
        store.items.value = listOf(
            ItineraryItemDto(
                id = "scheduled", tripId = tripId, dayDate = LocalDate(2026, 12, 1),
                title = "Already on day 1", sortOrder = 1.0,
                createdAt = stamp, updatedAt = stamp,
            ),
        )
        val requests = AddIdeaToTripRequests().also { it.set(card()) }
        val itinerary = InMemoryItineraryRepository(store)
        val vm = AddIdeaToTripViewModel(InMemoryTripRepository(store), itinerary, requests)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertTrue(vm.state.value.canAdd)
        vm.addTo(tripId)
        runCurrent()
        val added = assertIs<AddIdeaToTripEvent.Added>(vm.eventFlow.first())
        assertEquals(tripId, added.tripId)
        val ideas = store.items.value.filter { it.tripId == tripId && it.dayDate == null }
        assertEquals(1, ideas.size)
        val item = ideas.single()
        assertEquals("Park walk", item.title)
        assertEquals("Khao Yai, Thailand", item.placeName)
        assertEquals(PlaceKind.AREA, item.placeKind)
        assertEquals(ItemSource.AI, item.source)
        assertNull(item.startTime)
        assertNull(item.endTime)
        assertTrue(item.sortOrder > 0.0)
        assertEquals("scheduled", store.items.value.single { it.dayDate != null }.id)
    }

    @Test
    fun aFinishedTripIsRefused() = runTest {
        val store = InMemoryStore()
        val today = today()
        store.trips.value = listOf(trip("done", today.plusDays(-10), today.plusDays(-1)))
        val requests = AddIdeaToTripRequests().also { it.set(card()) }
        val vm = AddIdeaToTripViewModel(InMemoryTripRepository(store), InMemoryItineraryRepository(store), requests)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertFalse(vm.state.value.canAdd)
        vm.addTo("done")
        runCurrent()
        assertEquals(AddIdeaToTripEvent.Failed, vm.eventFlow.first())
        assertTrue(store.items.value.isEmpty())
    }
}

class UnscheduledItemFromIdeaTest {
    @Test
    fun theActivityIsTheTitleAndThereIsNoDay() {
        val input = unscheduledItemFromIdea(
            InspirationCardDto(
                id = "c1",
                activity = "Marine wildlife photography · Taman Nasional Komodo, Nusa Tenggara Timur",
                region = "Taman Nasional Komodo, Nusa Tenggara Timur",
                why = "Fits",
            ),
            itemId = "item-1",
            sortOrder = 2.0,
        )
        assertNull(input.dayDate)
        assertNull(input.startTime)
        assertEquals("Marine wildlife photography", input.title)
        assertEquals("Taman Nasional Komodo, Nusa Tenggara Timur", input.placeName)
        assertEquals(PlaceKind.AREA, input.placeKind)
        assertEquals(ItemSource.AI, input.source)
        assertEquals(2.0, input.sortOrder)
    }
}

private val stamp: Instant = Instant.parse("2026-11-01T00:00:00Z")

private fun trip(id: String, start: LocalDate?, end: LocalDate?, role: Role = Role.OWNER) = TripDto(
    id = id, ownerId = "u1", title = "Trip $id", destination = "Bali", startDate = start, endDate = end,
    baseCurrency = "IDR", myRole = role, createdAt = stamp, updatedAt = stamp,
)

private fun card() = InspirationCardDto(id = "idea-1", activity = "Park walk", region = "Khao Yai, Thailand", why = "Fits")
