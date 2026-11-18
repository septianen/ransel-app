package app.ransel.feature

import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTravelModeRepository
import app.ransel.feature.itinerary.ItineraryViewModel
import app.ransel.shared.domain.LegDisplay
import app.ransel.shared.domain.LegKind
import app.ransel.shared.domain.dayLegs
import app.ransel.shared.domain.effectiveMainTravelMode
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
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TravelModeViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val trip = TripDto(
        id = "t1", ownerId = "u1", title = "Bali", destination = "Bali",
        startDate = LocalDate(2099, 11, 20), endDate = LocalDate(2099, 11, 20), baseCurrency = "IDR",
        createdAt = Instant.parse("2026-10-01T00:00:00Z"), updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun stop(id: String, sort: Double, lat: Double) = ItineraryItemInput(
        id = id, title = id, category = Category.ATTRACTION, dayDate = LocalDate(2099, 11, 20),
        startTime = LocalTime(9, 0), endTime = LocalTime(10, 0), sortOrder = sort,
        lat = lat, lng = 115.0,
    )

    @Test
    fun aSavedModeIsKeptUntilThePairIsBroken() = runTest {
        val store = InMemoryStore()
        val itinerary = InMemoryItineraryRepository(store)
        val modes = InMemoryTravelModeRepository(store)
        itinerary.save("t1", stop("a", 1.0, -8.50))
        itinerary.save("t1", stop("b", 2.0, -8.51))
        itinerary.save("t1", stop("c", 3.0, -8.52))
        val vm = ItineraryViewModel(trip, itinerary, InMemoryPreferencesRepository(), travelModes = modes)
        vm.state.first { it.selectedItems.size == 3 }
        assertTrue(vm.saveLegMode("a", "b", LegKind.WALK))
        assertEquals(LegKind.WALK, vm.state.first { it.travelModeOverrides["a" to "b"] == LegKind.WALK }.travelModeOverrides["a" to "b"])
        val main = trip.effectiveMainTravelMode()
        val withSave = dayLegs(vm.state.value.selectedItems, main, vm.state.value.travelModeOverrides)
        assertEquals(LegKind.WALK, (withSave[0] as LegDisplay.Leg).leg.kind)

        itinerary.applyDayOrder(
            "t1",
            LocalDate(2099, 11, 20),
            listOf(
                DayOrderItem("a", 1.0, LocalTime(9, 0), LocalTime(10, 0)),
                DayOrderItem("c", 2.0, LocalTime(11, 0), LocalTime(12, 0)),
                DayOrderItem("b", 3.0, LocalTime(13, 0), LocalTime(14, 0)),
            ),
        )
        val items = itinerary.observe("t1").first()
        val legs = dayLegs(items, main, vm.state.value.travelModeOverrides)
        val auto = (dayLegs(items, main)[0] as LegDisplay.Leg).leg.kind
        assertEquals(LegKind.WALK, vm.state.value.travelModeOverrides["a" to "b"], "the row is still stored")
        assertEquals(auto, (legs[0] as LegDisplay.Leg).leg.kind, "a-c falls back to Auto after the reorder")
    }
}
