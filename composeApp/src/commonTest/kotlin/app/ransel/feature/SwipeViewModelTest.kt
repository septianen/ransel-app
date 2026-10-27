package app.ransel.feature

import app.ransel.data.credit.CreditsStore
import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.explore.SwipeUiState
import app.ransel.feature.explore.SwipeViewModel
import app.ransel.shared.model.Interest
import app.ransel.shared.model.ItemSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import app.ransel.stopTestMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    /** The mock engine answers on its own dispatcher: wait (real time) until [check] holds. */
    private suspend fun eventually(check: suspend () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private suspend fun SwipeViewModel.loaded() = eventually { state.value !is SwipeUiState.Loading }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<Pair<String, String>>()

    private fun place(id: String) =
        """{"placeId":"$id","name":"Place $id","reason":"because","suggestedDurationMin":90,"lat":-8.5,"lng":115.2}"""

    private val creditsJson =
        """"credits":{"balance":27,"dailyFreeLeft":0,"dailyFreeLimit":3,"resetsAt":"2026-10-16T17:00:00Z",
            "costs":{"search":3,"nextBatch":1,"placeDetail":0}}"""

    private fun response(vararg ids: String) =
        """{"data":{"groups":[{"category":"FOOD","items":[${ids.joinToString(",") { place(it) }}]}],$creditsJson}}"""

    private fun api(first: String, next: String = response()) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val body = request.body.toByteArray().decodeToString()
                requests += request.method.value + " " + request.url.encodedPath to body
                when {
                    request.url.encodedPath.endsWith("/recommendations") ->
                        respond(if ("\"newSearch\":false" in body) next else first, HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NoContent)
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

    private suspend fun store(session: ExploreSession) =
        ExploreSessionStore(InMemoryPreferencesRepository()).also { it.save(session) }

    @Test
    fun firstSearchLoadsCardsAndLikeSavesAnUnscheduledAiItem() = runTest {
        val itinerary = InMemoryItineraryRepository(InMemoryStore())
        val vm = SwipeViewModel("t1", store(ExploreSession(form)), api(response("a", "b", "c", "d", "e")), itinerary)
        vm.loaded()
        val cards = assertIs<SwipeUiState.Cards>(vm.state.value)
        assertEquals(5, cards.session.cards.size)
        assertTrue(requests.first().second.contains("\"newSearch\":true"))

        vm.like()
        eventually { itinerary.observe("t1").first().isNotEmpty() && requests.any { "feedback" in it.first } }
        val saved = itinerary.observe("t1").first().single()
        assertNull(saved.dayDate)
        assertEquals(ItemSource.AI, saved.source)
        assertEquals("a", saved.placeId)
        assertEquals(90, saved.durationMin)
        assertTrue(requests.any { it.first == "POST /api/v1/recommendations/feedback" && "\"LIKE\"" in it.second })
    }

    @Test
    fun undoRemovesTheSavedItemAndTheFeedback() = runTest {
        val itinerary = InMemoryItineraryRepository(InMemoryStore())
        val vm = SwipeViewModel("t1", store(ExploreSession(form)), api(response("a", "b", "c", "d", "e")), itinerary)
        vm.loaded()
        vm.like()
        eventually { itinerary.observe("t1").first().isNotEmpty() }
        vm.undo()
        eventually { requests.any { it.first.startsWith("DELETE") } }
        assertTrue(itinerary.observe("t1").first().isEmpty())
        assertTrue(requests.any { it.first == "DELETE /api/v1/recommendations/feedback/t1/a" })
        assertEquals(0, assertIs<SwipeUiState.Cards>(vm.state.value).session.position)
    }

    @Test
    fun skipOnlySendsFeedback() = runTest {
        val itinerary = InMemoryItineraryRepository(InMemoryStore())
        val vm = SwipeViewModel("t1", store(ExploreSession(form)), api(response("a", "b", "c", "d", "e")), itinerary)
        vm.loaded()
        vm.skip()
        eventually { requests.any { "feedback" in it.first } }
        assertTrue(itinerary.observe("t1").first().isEmpty())
        assertTrue(requests.any { it.first == "POST /api/v1/recommendations/feedback" && "\"SKIP\"" in it.second })
    }

    @Test
    fun backgroundBatchIsRequestedWhenThreeCardsAreLeft() = runTest {
        val vm = SwipeViewModel(
            "t1",
            store(ExploreSession(form)),
            api(first = response("a", "b", "c", "d"), next = response("e", "f")),
            InMemoryItineraryRepository(InMemoryStore()),
        )
        vm.loaded()
        vm.skip() // 3 left
        eventually { requests.count { it.first == "POST /api/v1/recommendations" } >= 2 }
        eventually { (vm.state.value as? SwipeUiState.Cards)?.session?.cards?.size == 6 }
        val batch = requests.last { it.first == "POST /api/v1/recommendations" }
        assertTrue("\"newSearch\":false" in batch.second)
        assertTrue("\"excludePlaceIds\"" in batch.second && "\"a\"" in batch.second)
        assertEquals(6, assertIs<SwipeUiState.Cards>(vm.state.value).session.cards.size)
    }

    @Test
    fun emptySearchShowsTheNoResultsState() = runTest {
        val vm = SwipeViewModel(
            "t1",
            store(ExploreSession(form)),
            api(first = """{"data":{"groups":[],$creditsJson}}"""),
            InMemoryItineraryRepository(InMemoryStore()),
        )
        vm.loaded()
        assertIs<SwipeUiState.NoResults>(vm.state.value)
    }

    private fun errorApi(status: HttpStatusCode, body: String) =
        RanselApi(createApiHttpClient(MockEngine { respond(body, status, json) }), origin = "http://api.test")

    @Test
    fun notEnoughCreditsShowsTheCostAndWhatTheUserHas() = runTest {
        val api = errorApi(
            HttpStatusCode.PaymentRequired,
            """{"error":{"code":"INSUFFICIENT_CREDITS","message":"Not enough credits",
                "details":{"cost":"3","available":"1","resetsAt":"2026-10-16T17:00:00Z"}}}""",
        )
        val vm = SwipeViewModel("t1", store(ExploreSession(form)), api, InMemoryItineraryRepository(InMemoryStore()))
        vm.loaded()
        val state = assertIs<SwipeUiState.OutOfCredits>(vm.state.value)
        assertEquals(3, state.cost)
        assertEquals(1, state.available)
    }

    @Test
    fun theSearchResponseUpdatesTheSharedBalance() = runTest {
        val credits = CreditsStore()
        val vm = SwipeViewModel("t1", store(ExploreSession(form)), api(response("a")), InMemoryItineraryRepository(InMemoryStore()), credits)
        vm.loaded()
        assertEquals(27, credits.state.value?.balance)
        assertEquals(27, credits.state.value?.available)
    }
}
