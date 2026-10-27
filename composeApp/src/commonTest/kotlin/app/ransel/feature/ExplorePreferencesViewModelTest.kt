package app.ransel.feature

import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.ApiJson
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.data.sync.NoSyncStatus
import app.ransel.feature.explore.ExplorePreferencesViewModel
import app.ransel.feature.explore.SuggestionsState
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import app.ransel.stopTestMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorePreferencesViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val suggestionCalls = mutableListOf<String>()

    /** [answer] maps the request URL (with its query) to the suggestions body, or null for a 503. */
    private fun api(answer: (String) -> String?) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val url = request.url.toString()
                if (request.url.encodedPath.endsWith("/explore/suggestions")) {
                    suggestionCalls += url
                    answer(url)?.let { respond(it, HttpStatusCode.OK, json) }
                        ?: respond("""{"error":{"code":"AI_UNAVAILABLE","message":"x"}}""", HttpStatusCode.ServiceUnavailable, json)
                } else {
                    // GET /trips (used while waiting for a new trip to reach the server): the "server" has what was created.
                    val body = ApiJson.encodeToString(ListSerializer(TripDto.serializer()), store.trips.value)
                    respond("""{"data":$body}""", HttpStatusCode.OK, json)
                }
            },
        ),
        origin = "http://api.test",
    )

    private fun suggestions(vararg names: String, level: String? = null, perDay: Long? = null) =
        """{"data":{"interests":[${names.joinToString(",") { "\"$it\"" }}]""" +
            (level?.let { ""","suggestedPriceLevel":"$it"""" } ?: "") +
            (perDay?.let { ""","budgetPerPersonPerDayMinor":$it,"currency":"IDR"""" } ?: "") + "}}"

    private val store = InMemoryStore()
    private val trips = InMemoryTripRepository(store)
    private val sessions = ExploreSessionStore(InMemoryPreferencesRepository())

    private fun vm(tripId: String?, api: RanselApi) =
        ExplorePreferencesViewModel(tripId, trips, api, sessions, InMemoryPreferencesRepository(), NoSyncStatus)

    private suspend fun createBali(budgetMinor: Long? = null) = trips.create(
        CreateTripRequest(
            id = "t1", title = "Bali 4 hari", destination = "Bali", startDate = LocalDate(2026, 11, 20),
            endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR", budgetMinor = budgetMinor,
        ),
    )

    @Test
    fun existingTripLoadsSuggestionsAndPreselectsThePriceLevel() = runTest {
        createBali(budgetMinor = 5_000_000)
        val vm = vm("t1", api { suggestions("Pantai", "Pura", level = "LOW", perDay = 300_000) })
        eventually { vm.state.value.suggestions is SuggestionsState.Loaded }
        val s = vm.state.value
        assertEquals(listOf("Pantai", "Pura"), assertIs<SuggestionsState.Loaded>(s.suggestions).interests)
        assertEquals(PriceLevel.LOW, s.priceLevel)
        assertEquals(300_000, s.budgetNote?.amountMinor)
        assertTrue(suggestionCalls.single().contains("tripId=t1"))
    }

    @Test
    fun aPriceLevelTheUserChoseIsNotOverwrittenBySuggestions() = runTest {
        createBali(budgetMinor = 5_000_000)
        val vm = vm("t1", api { suggestions("Pantai", level = "HIGH", perDay = 2_000_000) })
        vm.setPriceLevel(PriceLevel.LOW)
        eventually { vm.state.value.suggestions is SuggestionsState.Loaded }
        assertEquals(PriceLevel.LOW, vm.state.value.priceLevel)
    }

    @Test
    fun aFailedSuggestionsRequestHidesTheBoxAndSearchStillWorks() = runTest {
        createBali()
        val vm = vm("t1", api { null })
        eventually { suggestionCalls.isNotEmpty() && vm.state.value.suggestions == SuggestionsState.Hidden && !vm.state.value.loading }
        assertFalse(vm.state.value.canSearch) // no interest yet
        vm.toggleInterest(Interest.FOOD)
        assertTrue(vm.state.value.canSearch)
        assertTrue(vm.state.value.noBudget)
    }

    @Test
    fun movingTheStartMonthReloadsAndDropsChipsThatNoLongerExist() = runTest {
        val vm = vm(null, api { url -> if ("month=2027-03" in url) suggestions("Sakura", "Onsen") else suggestions("Onsen", "Anime") })
        vm.setDestination("Tokyo")
        vm.setDates(LocalDate(2026, 11, 20), LocalDate(2026, 11, 25))
        vm.onDestinationBlur()
        eventually { vm.state.value.suggestions is SuggestionsState.Loaded }
        vm.toggleExtraInterest("Anime")
        vm.toggleExtraInterest("Onsen")
        assertEquals(listOf("Anime", "Onsen"), vm.state.value.extraInterests)

        vm.setDates(LocalDate(2027, 3, 27), LocalDate(2027, 4, 2))
        eventually { (vm.state.value.suggestions as? SuggestionsState.Loaded)?.interests?.contains("Sakura") == true }
        assertEquals(listOf("Onsen"), vm.state.value.extraInterests)
    }

    @Test
    fun theSearchNeedsAtLeastOneCoreOrDestinationInterest() = runTest {
        createBali()
        val vm = vm("t1", api { suggestions("Pantai") })
        eventually { vm.state.value.suggestions is SuggestionsState.Loaded }
        assertFalse(vm.state.value.canSearch)
        vm.toggleExtraInterest("Pantai")
        assertTrue(vm.state.value.canSearch)
    }

    @Test
    fun anExistingTripPrefillsItsDestinationAndAnotherOneDrivesTheChipsAndTheSearch() = runTest {
        trips.create(
            CreateTripRequest(
                id = "t1", title = "Trip A", destination = "Yogyakarta", startDate = LocalDate(2026, 11, 20),
                endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
            ),
        )
        val vm = vm("t1", api { url -> if ("destination=Solo" in url) suggestions("Batik Laweyan", "Keraton") else suggestions("Malioboro", "Keraton") })
        eventually { vm.state.value.suggestions is SuggestionsState.Loaded }
        assertEquals("Yogyakarta", vm.state.value.destination)
        assertEquals("Yogyakarta", vm.state.value.tripDestination)
        vm.toggleExtraInterest("Malioboro")
        vm.toggleExtraInterest("Keraton")

        vm.setDestination("Solo")
        vm.onDestinationBlur()
        eventually { (vm.state.value.suggestions as? SuggestionsState.Loaded)?.interests?.contains("Batik Laweyan") == true }
        val call = suggestionCalls.last()
        assertTrue("tripId=t1" in call && "destination=Solo" in call, call)
        assertEquals(listOf("Keraton"), vm.state.value.extraInterests)

        vm.setDestination(" ")
        assertFalse(vm.state.value.canSearch)
        vm.setDestination("Solo")
        var ready = false
        vm.start("unused") { ready = true }
        eventually { ready }
        assertEquals("Solo", sessions.session("t1")!!.form.destination)
        assertEquals("Yogyakarta", store.trips.value.single().destination, "the trip itself is not changed")
    }

    @Test
    fun startingANewTripCreatesItAndStoresTheSession() = runTest {
        val vm = vm(null, api { suggestions("Snorkeling") })
        vm.setDestination("Lombok")
        vm.onDestinationBlur()
        eventually { vm.state.value.suggestions is SuggestionsState.Loaded }
        vm.toggleExtraInterest("Snorkeling")
        var ready = false
        vm.start("Trip ke Lombok") { ready = true }
        eventually { ready }

        val created = store.trips.value.single()
        assertEquals("Trip ke Lombok", created.title)
        assertEquals("Lombok", created.destination)
        val form = sessions.session(created.id)!!.form
        assertEquals(created.id, form.tripId)
        assertEquals(listOf("Snorkeling"), form.extraInterests)
    }
}
