package app.ransel.feature

import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.itinerary.ActivityDetailViewModel
import app.ransel.feature.itinerary.PlaceText
import app.ransel.feature.itinerary.activityMapsUrl
import app.ransel.feature.itinerary.repeatsTitle
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.Role
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** One Activity details screen for every card (plan activity-detail-unify, owner 23 Nov 2026). */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityDetailUnifyTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val at = Instant.parse("2026-11-23T00:00:00Z")
    private val store = InMemoryStore().apply {
        trips.value = listOf(
            TripDto(
                id = "t1", ownerId = "u1", title = "Malang", destination = "Malang", startDate = LocalDate(2026, 12, 1),
                endDate = LocalDate(2026, 12, 3), baseCurrency = "IDR", myRole = Role.EDITOR, createdAt = at, updatedAt = at,
            ),
        )
    }
    private val itinerary = InMemoryItineraryRepository(store)
    private val asked = mutableListOf<String>()

    private val api = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                asked += request.url.toString()
                respond(
                    """{"data":{"placeId":"p1","name":"Museum Angkut","about":"Museum transportasi.","bestTime":"Pagi","estimatedCostMinor":100000,"costCurrency":"IDR"}}""",
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ),
        origin = "http://api.test",
    )

    private fun item(placeId: String? = "p1", source: ItemSource = ItemSource.MANUAL, day: LocalDate? = null) = ItineraryItemDto(
        id = "i1", tripId = "t1", dayDate = day, title = "Museum Angkut", category = Category.ATTRACTION,
        placeName = "Museum Angkut, Batu", placeId = placeId, source = source, sortOrder = 1.0, createdAt = at, updatedAt = at,
    )

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private fun vm(signedIn: Boolean = true) =
        ActivityDetailViewModel("t1", "i1", InMemoryTripRepository(store), itinerary, api, signedIn = { signedIn })

    @Test
    fun aHandPickedPlaceAsksOnlyTheSavedTextAndAnAiPlaceAsksAsBefore() = runTest {
        store.items.value = listOf(item(source = ItemSource.MANUAL))
        val manual = vm()
        eventually { manual.state.value.place is PlaceText.Loaded }
        assertTrue(asked.single().contains("cacheOnly=true"), asked.single())
        assertEquals("Museum transportasi.", assertIs<PlaceText.Loaded>(manual.state.value.place).info.about)
        assertEquals(Category.ATTRACTION, assertIs<PlaceText.Loaded>(manual.state.value.place).info.category)

        asked.clear()
        store.items.value = listOf(item(source = ItemSource.AI))
        val ai = vm()
        eventually { ai.state.value.place is PlaceText.Loaded }
        assertFalse(asked.single().contains("cacheOnly"), asked.single())
    }

    @Test
    fun noPlaceIdAsksNothing() = runTest {
        store.items.value = listOf(item(placeId = null))
        val plain = vm()
        eventually { !plain.state.value.loading }
        assertEquals(PlaceText.None, plain.state.value.place)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun aGuestAsksNothing() = runTest {
        store.items.value = listOf(item())
        val guest = vm(signedIn = false)
        eventually { !guest.state.value.loading }
        assertEquals(PlaceText.None, guest.state.value.place)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun anEditOfTheTitleDoesNotAskAgainAndDeleteClosesTheScreen() = runTest {
        store.items.value = listOf(item())
        val detail = vm()
        eventually { detail.state.value.place is PlaceText.Loaded }
        store.items.value = listOf(item().copy(title = "Museum Angkut (pagi)"))
        eventually { detail.state.value.item?.title == "Museum Angkut (pagi)" }
        assertEquals(1, asked.size)
        assertIs<PlaceText.Loaded>(detail.state.value.place)

        assertTrue(detail.state.value.canSchedule, "unscheduled, an editor, a trip with days")
        detail.delete()
        eventually { detail.state.value.gone }
    }

    @Test
    fun aScheduledItemOrAnUndatedTripHasNoJadwalkan() = runTest {
        store.items.value = listOf(item(day = LocalDate(2026, 12, 2)))
        val scheduled = vm()
        eventually { !scheduled.state.value.loading }
        assertFalse(scheduled.state.value.canSchedule)

        store.trips.value = listOf(store.trips.value.single().copy(startDate = null, endDate = null))
        store.items.value = listOf(item())
        val undated = vm()
        eventually { !undated.state.value.loading && undated.state.value.trip?.startDate == null }
        assertFalse(undated.state.value.canSchedule)
    }

    @Test
    fun aPlaceLineThatOnlyRepeatsTheTitleIsLeftOut() {
        assertTrue(repeatsTitle("Museum D'Topeng Kingdom", "Museum D'Topeng Kingdom"))
        assertTrue(repeatsTitle("museum angkut, Batu", "Museum Angkut"))
        assertFalse(repeatsTitle("Jimbaran Bay Seafood", "Makan malam di Jimbaran"))
        assertFalse(repeatsTitle("Museum Angkut Baru", "Museum Angkut"))
    }

    @Test
    fun mapsOpensByPlaceIdElseByPointElseNothing() {
        assertTrue(activityMapsUrl(item())!!.contains("query_place_id=p1"))
        assertEquals("https://www.google.com/maps/search/?api=1&query=-7.9,112.5", activityMapsUrl(item(placeId = null).copy(lat = -7.9, lng = 112.5)))
        assertNull(activityMapsUrl(item(placeId = null)))
    }
}
