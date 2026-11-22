package app.ransel.feature

import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.explore.TripTasteRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.explore.TripExploreBanner
import app.ransel.feature.explore.TripExploreViewModel
import app.ransel.feature.explore.tripExploreBanner
import app.ransel.feature.interview.KhasState
import app.ransel.feature.trips.TripPhase
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.Role
import app.ransel.shared.model.TravelStyle
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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

/** "Cari tempat" from a trip (plan explore-trip-one-tap v4): the sheet prefilled from the trip's server Seleramu. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripExploreViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** The "server": its Seleramu (a JSON object or null), whether a save works, and what was saved. */
    private var serverTaste: String? = null
    private var putWorks = true
    private val puts = mutableListOf<String>()
    private var members = 1

    private val api = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/taste") && request.method == HttpMethod.Put -> {
                        val body = request.body.toByteArray().decodeToString()
                        puts += body
                        if (putWorks) {
                            serverTaste = body
                            respond("""{"data":{"taste":$body,"updatedAt":"2026-11-22T00:00:00Z"}}""", HttpStatusCode.OK, json)
                        } else {
                            respond("""{"error":{"code":"FORBIDDEN","message":"x"}}""", HttpStatusCode.Forbidden, json)
                        }
                    }
                    path.endsWith("/taste") -> respond("""{"data":{"taste":${serverTaste ?: "null"}}}""", HttpStatusCode.OK, json)
                    path.endsWith("/explore/suggestions") ->
                        respond("""{"data":{"interests":["Pantai","Pura"],"suggestedPriceLevel":"LOW"}}""", HttpStatusCode.OK, json)
                    path.endsWith("/members") -> {
                        val list = (1..members).joinToString(",") {
                            """{"tripId":"t1","memberId":"m$it","userId":"u$it","displayName":"M$it","role":"EDITOR","joinedAt":"2026-11-01T00:00:00Z"}"""
                        }
                        respond("""{"data":[$list]}""", HttpStatusCode.OK, json)
                    }
                    else -> respond("""{"error":{"code":"NOT_FOUND","message":"x"}}""", HttpStatusCode.NotFound, json)
                }
            },
        ),
        origin = "http://api.test",
    )

    private val store = InMemoryStore()
    private val trips = InMemoryTripRepository(store)
    private val sessions = ExploreSessionStore(InMemoryPreferencesRepository())

    private suspend fun vm(): TripExploreViewModel {
        trips.create(
            CreateTripRequest(
                id = "t1", title = "Bali 4 hari", destination = "Bali", startDate = LocalDate(2026, 11, 20),
                endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
            ),
        )
        return TripExploreViewModel("t1", trips, api, TripTasteRepository(api, sessions), sessions, InMemoryPreferencesRepository())
            .also { it.open() }
    }

    private suspend fun TripExploreViewModel.ready() = eventually { !state.value.loading && state.value.khas is KhasState.Loaded }

    @Test
    fun theSheetStartsFromTheServerSeleramuAndASearchWithoutChangesSavesNothing() = runTest {
        serverTaste = """{"interests":["FOOD"],"extraInterests":["Sunset"],"style":"PADAT","priceLevel":"HIGH","notes":"tanpa babi"}"""
        members = 3
        val vm = vm()
        vm.ready()
        val s = vm.state.value
        assertEquals(setOf(Interest.FOOD), s.draft.interests)
        assertEquals(TravelStyle.PADAT, s.draft.style)
        assertEquals("tanpa babi", s.draft.notes)
        // a saved "Khas" chip the server no longer suggests stays listed, so it can be removed
        assertEquals(listOf("Pantai", "Pura", "Sunset"), assertIs<KhasState.Loaded>(s.khas).interests)
        eventually { vm.state.value.group }
        assertTrue(s.canSearch)

        var opened: String? = null
        vm.search { opened = it }
        eventually { opened != null }
        assertEquals("t1", opened)
        assertTrue(puts.isEmpty(), "nothing changed: no save")
        val form = sessions.sessions.value.getValue("t1").form
        assertEquals(PriceLevel.HIGH, form.priceLevel)
        assertEquals(listOf("Sunset"), form.extraInterests)
    }

    @Test
    fun withoutASeleramuTheSearchWaitsForAnInterestThenSavesIt() = runTest {
        val vm = vm()
        vm.ready()
        assertTrue(vm.state.value.draft.isEmpty)
        assertFalse(vm.state.value.canSearch)
        assertEquals(PriceLevel.LOW, vm.state.value.budgetPriceLevel)

        vm.toggleInterest(Interest.NATURE)
        assertTrue(vm.state.value.canSearch)
        var opened = false
        vm.search { opened = true }
        eventually { opened }
        assertTrue(puts.single().contains("NATURE"))
        // the budget's range is what the search uses while none was picked
        assertEquals(PriceLevel.LOW, sessions.sessions.value.getValue("t1").form.priceLevel)
    }

    @Test
    fun closingDropsTheChangesAndAFailedSaveStillSearches() = runTest {
        serverTaste = """{"interests":["FOOD"]}"""
        val vm = vm()
        vm.ready()
        vm.toggleInterest(Interest.CULTURE)
        vm.open() // closed and opened again
        eventually { !vm.state.value.loading }
        assertEquals(setOf(Interest.FOOD), vm.state.value.draft.interests)

        putWorks = false
        vm.toggleInterest(Interest.CULTURE)
        var opened = false
        vm.search { opened = true }
        eventually { opened }
        assertEquals(1, puts.size)
        assertEquals(listOf(Interest.FOOD, Interest.CULTURE), sessions.sessions.value.getValue("t1").form.interests)
        // this device keeps it for the next try
        assertEquals(setOf(Interest.FOOD, Interest.CULTURE), sessions.taste("t1")?.interests?.toSet())
    }

    @Test
    fun aSeleramuOnlyOnThisDeviceIsUploadedOnce() = runTest {
        sessions.saveTaste(ExploreForm(tripId = "t1", tripTitle = "Bali 4 hari", destination = "Bali", interests = listOf(Interest.SHOPPING), style = TravelStyle.SANTAI))
        val vm = vm()
        vm.ready()
        assertEquals(setOf(Interest.SHOPPING), vm.state.value.draft.interests)
        assertTrue(puts.single().contains("SHOPPING"))
        vm.open()
        eventually { !vm.state.value.loading }
        assertEquals(1, puts.size, "the server has it now")
    }

    @Test
    fun theBannerIsForEditorsOfTripsThatHaveNotEnded() {
        assertEquals(TripExploreBanner.SEARCH, tripExploreBanner(true, Role.EDITOR, TripPhase.UPCOMING, "Bali", pending = false))
        assertEquals(TripExploreBanner.RESUME, tripExploreBanner(true, Role.OWNER, TripPhase.ONGOING, "Bali", pending = true))
        assertEquals(TripExploreBanner.NO_DESTINATION, tripExploreBanner(true, Role.OWNER, TripPhase.UPCOMING, " ", pending = false))
        assertNull(tripExploreBanner(true, Role.EDITOR, TripPhase.UPCOMING, "", pending = false), "Edit trip is the owner's")
        assertNull(tripExploreBanner(true, Role.VIEWER, TripPhase.UPCOMING, "Bali", pending = false))
        assertNull(tripExploreBanner(true, Role.OWNER, TripPhase.FINISHED, "Bali", pending = false))
        assertNull(tripExploreBanner(false, Role.OWNER, TripPhase.UPCOMING, "Bali", pending = false))
    }
}
