package app.ransel.feature

import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.home.HomeUiState
import app.ransel.feature.home.HomeViewModel
import app.ransel.shared.dto.CreateTripRequest
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
import app.ransel.stopTestMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import app.ransel.util.today
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

/** Home uses the empty FullScreenState whenever there is no ongoing or upcoming trip to feature. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeEmptyStateTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private fun session() = SessionManager(
        object : TokenStore {
            var saved: StoredSession? = null
            override suspend fun read() = saved
            override suspend fun write(session: StoredSession) { saved = session }
            override suspend fun clear() { saved = null }
        },
    ) { error("not used") }

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine {
                respond("""{"data":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
        origin = "http://api.test",
    )

    private fun home(trips: InMemoryTripRepository) = HomeViewModel(trips, api(), session())

    private suspend fun HomeViewModel.awaitState(check: (HomeUiState) -> Boolean): HomeUiState {
        val job = viewModelScope.launch { state.collect {} }
        try {
            return withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    while (true) {
                        val s = state.value
                        if (check(s)) return@withTimeout s
                        delay(10)
                    }
                    @Suppress("UNREACHABLE_CODE") error("")
                }
            }
        } finally {
            job.cancel()
        }
    }

    private suspend fun InMemoryTripRepository.addUpcoming(id: String) {
        val start = today().plus(DatePeriod(days = 10))
        create(CreateTripRequest(id, "Soon", "Bali", start, start.plus(DatePeriod(days = 3)), "IDR"))
    }

    private suspend fun InMemoryTripRepository.addFinished(id: String) {
        val end = today().minus(DatePeriod(days = 7))
        create(CreateTripRequest(id, "Done", "Lombok", end.minus(DatePeriod(days = 3)), end, "IDR"))
    }

    @Test
    fun noTripsAtAllShowsEmptyState() = runTest {
        val trips = InMemoryTripRepository(InMemoryStore())
        val home = home(trips)
        assertIs<HomeUiState.Empty>(home.awaitState { it is HomeUiState.Empty })
        home.viewModelScope.cancel()
    }

    @Test
    fun onlyFinishedTripsShowEmptyState() = runTest {
        val trips = InMemoryTripRepository(InMemoryStore())
        trips.addFinished("past")
        val home = home(trips)
        assertIs<HomeUiState.Empty>(home.awaitState { it is HomeUiState.Empty })
        home.viewModelScope.cancel()
    }

    @Test
    fun deletingTheOnlyUpcomingTripShowsEmptyState() = runTest {
        val trips = InMemoryTripRepository(InMemoryStore())
        trips.addUpcoming("soon")
        val home = home(trips)
        assertIs<HomeUiState.Content>(home.awaitState { it is HomeUiState.Content })
        trips.delete("soon")
        assertIs<HomeUiState.Empty>(home.awaitState { it is HomeUiState.Empty })
        home.viewModelScope.cancel()
    }

    @Test
    fun deletingTheOnlyUpcomingTripLeavesFinishedTripsOnEmptyState() = runTest {
        val trips = InMemoryTripRepository(InMemoryStore())
        trips.addFinished("past")
        trips.addUpcoming("soon")
        val home = home(trips)
        assertIs<HomeUiState.Content>(home.awaitState { it is HomeUiState.Content })
        trips.delete("soon")
        assertIs<HomeUiState.Empty>(home.awaitState { it is HomeUiState.Empty })
        home.viewModelScope.cancel()
    }
}
