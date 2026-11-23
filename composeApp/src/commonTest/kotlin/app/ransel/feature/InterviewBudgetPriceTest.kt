package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.destination.DestinationChoice
import app.ransel.feature.interview.InterviewViewModel
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.TripPlaceDto
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
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The price range of "Ubah seleramu" starts from the trip budget typed on the summary ("Dari budget trip"), else Sedang. */
@OptIn(ExperimentalCoroutinesApi::class)
class InterviewBudgetPriceTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val calls = mutableListOf<String>()
    private var level: String? = "LOW"

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

    private suspend fun vm(): InterviewViewModel {
        val vm = InterviewViewModel(
            InterviewDoor.EXPLORE, InMemoryTripRepository(InMemoryStore()), InMemoryPreferencesRepository(),
            api = api, session = signedIn(),
        )
        vm.setNameTemplate("Trip ke %s")
        vm.onDestinationPicked(DestinationChoice("Bandung", TripPlaceDto("p-bdg", -6.9175, 107.6191), countryCode = "ID", timeZone = "Asia/Jakarta"))
        vm.onDates(LocalDate(2026, 12, 20), LocalDate(2026, 12, 22))
        vm.toggleInterest(Interest.FOOD)
        return vm
    }

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    @Test
    fun aBudgetOnTheSummaryStartsThePriceRangeAndSaysSo() = runTest {
        val vm = vm()
        assertEquals(PriceLevel.MEDIUM, vm.state.value.priceLevel, "no budget: Sedang")
        assertFalse(vm.state.value.priceFromBudget)

        vm.onBudgetChange("1500000")
        advanceUntilIdle() // the typing pause
        eventually { vm.state.value.budgetPriceLevel != null }
        assertEquals(PriceLevel.LOW, vm.state.value.priceLevel)
        assertTrue(vm.state.value.priceFromBudget)
        val call = calls.last { "budgetMinor" in it }
        assertTrue("budgetMinor=1500000" in call && "currency=IDR" in call && "days=3" in call && "destination=Bandung" in call, call)
    }

    @Test
    fun aRangePickedInTheSheetWinsOverTheBudgetAndClearingTheBudgetGoesBackToSedang() = runTest {
        val vm = vm()
        vm.onBudgetChange("1500000")
        advanceUntilIdle()
        eventually { vm.state.value.budgetPriceLevel != null }

        vm.openTasteSheet()
        vm.draftPriceLevel(PriceLevel.HIGH)
        vm.saveTasteSheet()
        assertEquals(PriceLevel.HIGH, vm.state.value.priceLevel)
        assertFalse(vm.state.value.priceFromBudget, "the user chose it")

        vm.onBudgetChange("")
        assertNull(vm.state.value.budgetPriceLevel)
    }

    @Test
    fun aServerThatCannotTellLeavesSedang() = runTest {
        level = null
        val vm = vm()
        vm.onBudgetChange("1500000")
        advanceUntilIdle()
        eventually { calls.any { "budgetMinor" in it } }
        assertEquals(PriceLevel.MEDIUM, vm.state.value.priceLevel)
        assertFalse(vm.state.value.priceFromBudget)
    }
}
