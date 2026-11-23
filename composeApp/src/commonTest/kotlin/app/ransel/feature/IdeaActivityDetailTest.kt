package app.ransel.feature

import app.ransel.data.explore.ItineraryIdeas
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.ApiJson
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.interview.InterviewEvent
import app.ransel.feature.interview.InterviewPrefill
import app.ransel.feature.interview.InterviewViewModel
import app.ransel.feature.itinerary.ActivityDetailViewModel
import app.ransel.feature.itinerary.PlaceText
import app.ransel.feature.itinerary.toInput
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.InspirationCostRangeDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.Role
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The item made from an idea: title, point and Activity details (plan idea-activity-detail, owner 23 Nov 2026). */
@OptIn(ExperimentalCoroutinesApi::class)
class IdeaActivityDetailTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private suspend fun eventually(check: suspend () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private val at = Instant.parse("2026-11-23T00:00:00Z")
    private val card = InspirationCardDto(
        id = "c1", activity = "Marine wildlife photography · Taman Nasional Komodo, NTT", region = "Taman Nasional Komodo, NTT",
        why = "Pari manta dan penyu.", access = "Kapal dari Labuan Bajo", bestSeason = "Apr–Des", level = "Menengah",
        requirements = listOf("Lisensi selam"), costRange = InspirationCostRangeDto(1_500_000, 3_000_000, "IDR"),
    )
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<String>()
    /** The server: false = the trip is not there yet (404), true = it saves and answers with the point. */
    private var serverHasTrip = true

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                requests += "${request.method.value} ${request.url.encodedPath}"
                val body = """{"data":{"card":${ApiJson.encodeToString(InspirationCardDto.serializer(), card.copy(activity = "Marine wildlife photography"))},"lat":-8.55,"lng":119.49,"placeId":"pid-komodo"}}"""
                when {
                    !serverHasTrip -> respond("""{"error":{"code":"TRIP_NOT_FOUND","message":"x"}}""", HttpStatusCode.NotFound, json)
                    request.method == HttpMethod.Put -> respond(body, HttpStatusCode.OK, json)
                    else -> respond(body, HttpStatusCode.OK, json)
                }
            },
        ),
        origin = "http://api.test",
    )

    @Test
    fun aTripFromAnIdeaGetsTheActivityAsTitleAndKeepsTheCardForUpload() = runTest {
        val store = InMemoryStore()
        val itinerary = InMemoryItineraryRepository(store)
        val ideas = ItineraryIdeas(null, itinerary, InMemoryPreferencesRepository())
        val vm = InterviewViewModel(
            InterviewDoor.MY_TRIPS, InMemoryTripRepository(store), InMemoryPreferencesRepository(), null, null,
            itinerary = itinerary, ideas = ideas,
        )
        val cardJson = ApiJson.encodeToString(InspirationCardDto.serializer(), card)
        vm.applyPrefill(InterviewPrefill.fromRoute(card.region, "id", "NATURE", card.activity, cardJson)!!); runCurrent()
        vm.onDatesUndecided(true)
        vm.next(); vm.skipInterview()
        vm.create()
        val tripId = assertIs<InterviewEvent.Created>(vm.eventFlow.first()).tripId
        val item = store.items.value.single { it.tripId == tripId }
        assertEquals("Marine wildlife photography", item.title)
        assertEquals("Taman Nasional Komodo, NTT", item.placeName)
        assertEquals(PlaceKind.AREA, item.placeKind)
        assertNull(item.notes)
        assertEquals(card, ideas.local(item.id))
        assertTrue(ideas.hasPending(tripId))
    }

    @Test
    fun theUploadWaitsForTheTripThenPutsTheRegionsPointOnTheItem() = runTest {
        val store = InMemoryStore().apply { trips.value = listOf(trip()) }
        val itinerary = InMemoryItineraryRepository(store)
        itinerary.save("t1", ideaItem().toInput())
        val ideas = ItineraryIdeas(api(), itinerary, InMemoryPreferencesRepository())
        ideas.add("t1", "i1", card)

        serverHasTrip = false
        assertEquals(false, ideas.push("t1"), "not on the server yet: kept")
        serverHasTrip = true
        assertTrue(ideas.push("t1"))
        val item = itinerary.observe("t1").first().single()
        assertEquals(-8.55, item.lat)
        assertEquals(119.49, item.lng)
        assertNull(ideas.local("i1"))
    }

    @Test
    fun theDetailShowsTheIdeaFromTheServerOrThisDevicesCopyWithoutAnyAiCall() = runTest {
        val store = InMemoryStore().apply { trips.value = listOf(trip()); items.value = listOf(ideaItem()) }
        val itinerary = InMemoryItineraryRepository(store)
        val detail = ActivityDetailViewModel("t1", "i1", InMemoryTripRepository(store), itinerary, api(), signedIn = { true }, ideas = ItineraryIdeas(null, itinerary, InMemoryPreferencesRepository()))
        eventually { detail.state.value.place is PlaceText.Idea }
        val idea = assertIs<PlaceText.Idea>(detail.state.value.place)
        assertEquals("Pari manta dan penyu.", idea.card.why)
        assertEquals("pid-komodo", idea.placeId)
        // only the idea was asked: no place detail (which may rebuild text with the AI)
        assertTrue(requests.isNotEmpty() && requests.all { it.startsWith("GET ") && it.endsWith("/trips/t1/itinerary/i1/idea") }, requests.toString())

        // offline on the device that made it: its own copy
        serverHasTrip = false
        val local = ItineraryIdeas(null, itinerary, InMemoryPreferencesRepository()).also { it.add("t1", "i1", card) }
        val offline = ActivityDetailViewModel("t1", "i1", InMemoryTripRepository(store), itinerary, api(), signedIn = { true }, ideas = local)
        eventually { offline.state.value.place is PlaceText.Idea }
        assertEquals("Pari manta dan penyu.", assertIs<PlaceText.Idea>(offline.state.value.place).card.why)
    }

    @Test
    fun aHandMadeItemWithoutPlaceAsksNothing() = runTest {
        val store = InMemoryStore().apply { trips.value = listOf(trip()); items.value = listOf(ideaItem().copy(source = ItemSource.MANUAL)) }
        val detail = ActivityDetailViewModel("t1", "i1", InMemoryTripRepository(store), InMemoryItineraryRepository(store), api(), signedIn = { true })
        eventually { !detail.state.value.loading }
        assertEquals(PlaceText.None, detail.state.value.place)
        assertTrue(requests.isEmpty())
    }

    private fun trip() = TripDto(
        id = "t1", ownerId = "u1", title = "Komodo", destination = "Komodo", baseCurrency = "IDR", myRole = Role.OWNER, createdAt = at, updatedAt = at,
    )

    private fun ideaItem() = ItineraryItemDto(
        id = "i1", tripId = "t1", dayDate = null, title = "Marine wildlife photography", category = Category.ATTRACTION,
        placeName = "Taman Nasional Komodo, NTT", placeKind = PlaceKind.AREA, source = ItemSource.AI, sortOrder = 1.0,
        createdAt = at, updatedAt = at,
    )
}
