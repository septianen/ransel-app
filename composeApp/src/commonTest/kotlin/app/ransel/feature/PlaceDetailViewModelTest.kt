package app.ransel.feature

import app.ransel.data.explore.ExploreCard
import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.explore.PlaceDetailViewModel
import app.ransel.navigation.PlaceDetailRoute
import app.ransel.shared.dto.OpeningHoursLine
import app.ransel.shared.dto.RecommendationItem
import app.ransel.shared.model.Category
import app.ransel.shared.model.Interest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceDetailViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private suspend fun eventually(check: suspend () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<String>()
    private var failNext = false

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                requests += request.url.encodedPath + "?" + request.url.encodedQuery
                if (failNext) {
                    failNext = false
                    respond("""{"error":{"code":"AI_UNAVAILABLE","message":"x"}}""", HttpStatusCode.ServiceUnavailable, json)
                } else {
                    respond(
                        """{"data":{"placeId":"p1","name":"Pantai Melasti","about":"Dibangun ulang","highlights":["Tip"],
                            "openingHours":[{"label":"Setiap hari","hours":"08.00 – 18.00"}],"estimatedCostMinor":25000,"costCurrency":"IDR"}}""",
                        HttpStatusCode.OK, json,
                    )
                }
            },
        ),
        origin = "http://api.test",
    )

    private val form = ExploreForm(
        tripId = "t1", tripTitle = "Bali", destination = "Bali",
        startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23),
        interests = listOf(Interest.FOOD),
    )

    private val card = ExploreCard(
        Category.ATTRACTION,
        RecommendationItem(
            placeId = "p1", name = "Pantai Melasti", reason = "Cocok", suggestedDurationMin = 90, lat = -8.5, lng = 115.2,
            about = "Pantai berkarang.", highlights = listOf("Datang pagi"),
            openingHours = listOf(OpeningHoursLine("Setiap hari", "08.00 – 18.00")), bestTime = "Pagi",
            estimatedCostMinor = 25_000, costCurrency = "IDR",
        ),
    )

    private suspend fun store() = ExploreSessionStore(InMemoryPreferencesRepository()).also { it.save(ExploreSession(form, cards = listOf(card))) }

    @Test
    fun fromASwipeCardEverythingIsThereWithoutARequest() = runTest {
        val vm = PlaceDetailViewModel(PlaceDetailRoute("p1"), store(), api())
        val info = assertNotNull(vm.state.value.info)
        assertEquals("Pantai berkarang.", info.about)
        assertEquals("Cocok", info.reason)
        assertEquals(Category.ATTRACTION, info.category)
        assertEquals(listOf("Datang pagi"), info.highlights)
        assertEquals(25_000L, info.estimatedCostMinor)
        assertTrue(requests.isEmpty(), "a card already carries the text")
    }

    @Test
    fun fromTheItineraryItIsLoadedByPlaceIdWithTheItemNameAndTripCurrency() = runTest {
        val vm = PlaceDetailViewModel(PlaceDetailRoute("p1", fromItinerary = true, name = "Pantai Melasti", currency = "IDR"), store(), api())
        eventually { vm.state.value.info != null }
        val info = assertNotNull(vm.state.value.info)
        assertEquals("Dibangun ulang", info.about)
        assertNull(info.reason, "the itinerary entry has no swipe reason")
        assertNull(info.category)
        val request = requests.single()
        assertTrue(request.startsWith("/api/v1/places/p1/detail?"), request)
        assertTrue("name=Pantai" in request && "currency=IDR" in request, request)
    }

    @Test
    fun aFailureCanBeRetried() = runTest {
        failNext = true
        val vm = PlaceDetailViewModel(PlaceDetailRoute("p1", fromItinerary = true, name = "Pantai Melasti"), store(), api())
        eventually { vm.state.value.failure != null }
        assertNull(vm.state.value.info)
        vm.retry()
        eventually { vm.state.value.info != null }
        assertEquals("Dibangun ulang", vm.state.value.info?.about)
    }

    @Test
    fun aSwipeCardThatIsNoLongerInTheSessionFallsBackToTheServer() = runTest {
        val vm = PlaceDetailViewModel(PlaceDetailRoute("gone"), store(), api())
        eventually { vm.state.value.info != null }
        assertTrue(requests.single().startsWith("/api/v1/places/gone/detail"))
    }
}
