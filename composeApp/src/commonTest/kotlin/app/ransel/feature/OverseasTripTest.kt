package app.ransel.feature

import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.feature.destination.DestinationChoice
import app.ransel.feature.trip.gmtLabel
import app.ransel.feature.trip.zone
import app.ransel.feature.trip.zoneCity
import app.ransel.feature.tripform.TripFormViewModel
import app.ransel.shared.dto.TripPlaceDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OverseasTripTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    @Test
    fun pickingTokyoSetsTheLocalCurrencyAndZoneButKeepsTheMainCurrency() = runTest {
        val store = InMemoryStore()
        val vm = TripFormViewModel(null, InMemoryTripRepository(store), InMemoryPreferencesRepository())
        vm.onTitleChange("Jepang musim dingin")
        vm.onDestinationPicked(DestinationChoice("Tokyo", TripPlaceDto("ChIJ-tokyo", 35.68, 139.76), "JP", "Asia/Tokyo"))
        val s = vm.state.value
        assertEquals("JPY", s.localCurrency)
        assertEquals("Asia/Tokyo", s.timeZone)
        assertEquals("IDR", s.currency, "members settle up in rupiah")

        vm.save()
        eventually { store.trips.value.isNotEmpty() }
        val trip = store.trips.value.single()
        assertEquals("JP", trip.countryCode)
        assertEquals("JPY", trip.localCurrency)
        assertEquals("Asia/Tokyo", trip.timeZone)
        assertEquals(TimeZone.of("Asia/Tokyo"), trip.zone())
    }

    @Test
    fun typingANewDestinationDropsTheLocale() = runTest {
        val vm = TripFormViewModel(null, InMemoryTripRepository(InMemoryStore()), InMemoryPreferencesRepository())
        vm.onDestinationPicked(DestinationChoice("Tokyo", null, "JP", "Asia/Tokyo"))
        vm.onDestinationChange("Bandung")
        assertNull(vm.state.value.localCurrency)
        assertNull(vm.state.value.timeZone)
    }

    @Test
    fun zoneLabelsReadWell() {
        assertEquals("Tokyo", zoneCity("Asia/Tokyo"))
        assertEquals("Ho Chi Minh", zoneCity("Asia/Ho_Chi_Minh"))
        assertEquals("GMT+9", gmtLabel("Asia/Tokyo"))
        assertEquals("GMT+5:30", gmtLabel("Asia/Kolkata"))
        assertNull(gmtLabel("Mars/Olympus"))
    }
}
