package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.data.travel.TravelAnswerChanges
import app.ransel.feature.tripform.travel.TripTravelViewModel
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.MeDto
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
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
import kotlin.time.Instant

/** The "Ubah seleramu" sheet of Edit trip starts from the trip's budget ("Dari budget trip"), else Sedang. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripTravelBudgetPriceTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val calls = mutableListOf<String>()
    private var level: String? = "HIGH"

    private val api = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                calls += request.url.toString()
                respond(
                    """{"data":{"interests":["Kuliner khas"]""" + (level?.let { ""","suggestedPriceLevel":"$it"""" } ?: "") + "}}",
                    HttpStatusCode.OK, json,
                )
            },
        ),
        origin = "http://api.test",
    )

    private val store = InMemoryStore()
    private val trips = InMemoryTripRepository(store)
    private val explore = ExploreSessionStore(InMemoryPreferencesRepository())

    private suspend fun signedIn(): SessionManager {
        val tokens = object : TokenStore {
            var saved: StoredSession? = null
            override suspend fun read() = saved
            override suspend fun write(session: StoredSession) { saved = session }
            override suspend fun clear() { saved = null }
        }
        return SessionManager(tokens) { error("not used") }.also {
            it.start(AuthSessionDto("a", Instant.parse("2099-01-01T00:00:00Z"), "r", MeDto("u1", "ayu@contoh.id", "Ayu")))
        }
    }

    private suspend fun section(): TripTravelViewModel {
        trips.create(
            CreateTripRequest(
                id = "t1", title = "Trip ke Bandung", destination = "Bandung", startDate = LocalDate(2026, 12, 20),
                endDate = LocalDate(2026, 12, 22), baseCurrency = "IDR", budgetMinor = 30_000_000,
            ),
        )
        return TripTravelViewModel(
            "t1", trips, explore, api, signedIn(), InMemoryPreferencesRepository(), TravelAnswerChanges(),
        )
    }

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    @Test
    fun theSheetOfATripWithABudgetStartsFromItsRangeAndSavingKeepsIt() = runTest {
        val vm = section()
        eventually { vm.state.value.trip != null }
        vm.openTasteSheet()
        eventually { vm.state.value.budgetPriceLevel != null }
        assertEquals(PriceLevel.HIGH, vm.state.value.budgetPriceLevel)
        assertEquals(true, calls.any { "tripId=t1" in it }, "the server knows the budget: it is asked with the trip")

        vm.draftToggleInterest(Interest.FOOD)
        vm.saveTasteSheet()
        eventually { explore.taste("t1") != null }
        assertEquals(PriceLevel.HIGH, explore.taste("t1")!!.priceLevel, "nothing was picked: the budget's range is what the search uses")
    }

    @Test
    fun withoutABudgetTheSheetStartsAtSedang() = runTest {
        level = null
        val vm = section()
        eventually { vm.state.value.trip != null }
        vm.openTasteSheet()
        eventually { calls.isNotEmpty() }
        vm.draftToggleInterest(Interest.FOOD)
        vm.saveTasteSheet()
        eventually { explore.taste("t1") != null }
        assertEquals(PriceLevel.MEDIUM, explore.taste("t1")!!.priceLevel)
    }

    @Test
    fun aRangePickedInTheSheetWinsOverTheBudget() = runTest {
        val vm = section()
        eventually { vm.state.value.trip != null }
        vm.openTasteSheet()
        eventually { vm.state.value.budgetPriceLevel != null }
        vm.draftToggleInterest(Interest.FOOD)
        vm.draftPriceLevel(PriceLevel.LOW)
        vm.saveTasteSheet()
        eventually { explore.taste("t1") != null }
        assertEquals(PriceLevel.LOW, explore.taste("t1")!!.priceLevel)
    }
}
