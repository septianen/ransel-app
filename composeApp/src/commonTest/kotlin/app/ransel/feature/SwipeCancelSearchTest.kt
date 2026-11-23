package app.ransel.feature

import app.ransel.data.credit.CreditsStore
import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.explore.SwipeUiState
import app.ransel.feature.explore.SwipeViewModel
import app.ransel.shared.model.Interest
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Batalkan pencarian" tells the server to stop the search (it sends a `searchId`), so nothing is charged and no round is made. */
@OptIn(ExperimentalCoroutinesApi::class)
class SwipeCancelSearchTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private suspend fun eventually(check: suspend () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val searchBodies = mutableListOf<String>()
    private val cancelPaths = mutableListOf<String>()
    /** The server holds the search until the test lets it answer. */
    private val release = CompletableDeferred<HttpStatusCode>()

    private val api = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/recommendations") && request.method == HttpMethod.Post -> {
                        searchBodies += request.body.toByteArray().decodeToString()
                        release.await()
                        respond("""{"error":{"code":"SEARCH_CANCELLED","message":"x"}}""", HttpStatusCode.Conflict, json)
                    }
                    request.method == HttpMethod.Delete && request.url.encodedPath.contains("/recommendations/searches/") -> {
                        cancelPaths += request.url.encodedPath
                        respond("""{"data":{"cancelled":true}}""", HttpStatusCode.OK, json)
                    }
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private val form = ExploreForm(
        tripId = "t1", tripTitle = "Trip Bandung", destination = "Bandung",
        startDate = LocalDate(2026, 12, 20), endDate = LocalDate(2026, 12, 22), interests = listOf(Interest.FOOD),
    )

    private suspend fun start(): Pair<SwipeViewModel, ExploreSessionStore> {
        val sessions = ExploreSessionStore(InMemoryPreferencesRepository()).also { it.save(ExploreSession(form)) }
        val vm = SwipeViewModel("t1", sessions, api, InMemoryItineraryRepository(InMemoryStore()), CreditsStore())
        eventually { searchBodies.isNotEmpty() }
        return vm to sessions
    }

    private fun idOf(body: String) = Regex("\"searchId\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)

    @Test
    fun theFirstSearchCarriesAnIdAndCancelTellsTheServerToStopThatSearch() = runTest {
        val (vm, sessions) = start()
        val id = assertNotNull(idOf(searchBodies.single()), "the search is sent with a searchId")
        assertTrue(vm.state.value is SwipeUiState.Loading)

        vm.cancelSearch()
        eventually { cancelPaths.isNotEmpty() }
        assertTrue(cancelPaths.single().endsWith("/recommendations/searches/$id"), cancelPaths.toString())
        eventually { sessions.session("t1") == null }

        // the server's "cancelled" answer reaches a screen that is gone: it must not turn into an error state
        release.complete(HttpStatusCode.Conflict)
        delay(100)
        assertTrue(vm.state.value !is SwipeUiState.Error)
    }

    @Test
    fun leavingTheScreenWhileItLoadsCancelsToo() = runTest {
        val (vm, _) = start()
        val id = assertNotNull(idOf(searchBodies.single()))
        vm.stopSearchOnServer() // what onCleared does while the search still loads
        eventually { cancelPaths.isNotEmpty() }
        assertTrue(cancelPaths.single().endsWith(id))
        vm.stopSearchOnServer()
        delay(50)
        assertEquals(1, cancelPaths.size, "one cancel per search")
        assertNull(null)
    }
}
