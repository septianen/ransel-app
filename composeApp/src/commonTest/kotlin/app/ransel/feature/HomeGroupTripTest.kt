package app.ransel.feature

import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.home.HomeUiState
import app.ransel.feature.home.HomeViewModel
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.MeDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import app.ransel.util.today
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.time.Instant

private fun member(id: String) =
    """{"tripId":"t1","memberId":"$id","displayName":"$id","role":"EDITOR","joinedAt":"2026-10-01T00:00:00Z"}"""

/** Home's nearest-trip card opens the Group tab for a shared trip and the itinerary for a solo one. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeGroupTripTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private suspend fun signedIn(): SessionManager {
        val store = object : TokenStore {
            var saved: StoredSession? = null
            override suspend fun read() = saved
            override suspend fun write(session: StoredSession) { saved = session }
            override suspend fun clear() { saved = null }
        }
        return SessionManager(store) { error("not used") }.also {
            it.start(AuthSessionDto("a", Instant.parse("2099-01-01T00:00:00Z"), "r", MeDto("u1", "ayu@contoh.id", "Ayu")))
        }
    }

    private fun api(members: List<String>) = RanselApi(
        createApiHttpClient(
            MockEngine {
                respond("""{"data":[${members.joinToString(",") { member(it) }}]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun homeFor(members: List<String>): HomeViewModel {
        val store = InMemoryStore()
        val trips = InMemoryTripRepository(store)
        val start = today().plus(DatePeriod(days = 10))
        trips.create(CreateTripRequest("t1", "Bali", "Bali", start, start.plus(DatePeriod(days = 3)), "IDR"))
        return HomeViewModel(trips, InMemoryItineraryRepository(store), api(members), signedIn())
    }

    private suspend fun HomeViewModel.content(check: (HomeUiState.Content) -> Boolean): HomeUiState.Content {
        val job = viewModelScope.launch { state.collect {} }
        try {
            return withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    while (true) {
                        val s = state.value
                        if (s is HomeUiState.Content && check(s)) return@withTimeout s
                        delay(10)
                    }
                    @Suppress("UNREACHABLE_CODE") error("")
                }
            }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun aTripWithOtherMembersOpensOnTheGroupTab() = runTest {
        val home = homeFor(listOf("m1", "m2"))
        home.content { it.isGroup }
        home.viewModelScope.cancel()
    }

    @Test
    fun aSoloTripOpensOnTheItinerary() = runTest {
        val home = homeFor(listOf("m1"))
        home.content { true }
        // give the members request time to answer: one member stays solo
        withContext(Dispatchers.Default) { delay(200) }
        assertFalse(home.content { true }.isGroup)
        home.viewModelScope.cancel()
    }
}
