package app.ransel.feature

import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.destination.LocationState
import app.ransel.feature.itinerary.ActivityFormViewModel
import app.ransel.feature.itinerary.PlaceChoice
import app.ransel.feature.itinerary.PlacePickerViewModel
import app.ransel.feature.itinerary.PlaceSearchMode
import app.ransel.platform.LocationFix
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripPlaceDto
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val json = headersOf(HttpHeaders.ContentType, "application/json")
private val stamp = Instant.parse("2026-11-12T10:00:00Z")
private const val TRIP = "t1"

private val suggestionsBody = """{"data":{"suggestions":[
    {"placeId":"ChIJ-warung","name":"Warung Bajo","secondary":"Labuan Bajo","kind":"AREA","matchRanges":[{"start":0,"end":3}],"category":"FOOD"},
    {"placeId":"ChIJ-pantai","name":"Pantai Pede","secondary":"Labuan Bajo","kind":"ATTRACTION","category":"ATTRACTION"}],"fallback":false}}"""

private fun tripWithDestination() = TripDto(
    id = TRIP, ownerId = "u1", title = "Flores", destination = "Labuan Bajo",
    startDate = LocalDate(2026, 12, 1), endDate = LocalDate(2026, 12, 4), baseCurrency = "IDR",
    destinationPlace = TripPlaceDto("ChIJ-bajo", -8.49, 119.88), createdAt = stamp, updatedAt = stamp,
)

private fun item(
    id: String, place: String?, placeId: String? = null, lat: Double? = null, lng: Double? = null,
    day: LocalDate? = null, updated: Instant = stamp,
) = ItineraryItemDto(
    id = id, tripId = TRIP, dayDate = day, title = "Aktivitas $id", placeName = place, placeId = placeId, lat = lat, lng = lng,
    sortOrder = 1.0, createdAt = stamp, updatedAt = updated,
)

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(10) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlacePickerViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val requests = MutableStateFlow<List<String>>(emptyList())
    private val bearers = MutableStateFlow<List<String?>>(emptyList())
    private val store = InMemoryStore().apply { trips.value = listOf(tripWithDestination()) }

    private enum class Server { OK, OFFLINE, DOWN, GOOGLE_DOWN, NO_DETAILS }

    private fun api(server: Server = Server.OK) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                requests.update { it + (path + "?" + request.url.encodedQuery) }
                bearers.update { it + request.headers[HttpHeaders.Authorization] }
                when {
                    server == Server.OFFLINE -> error("no route to host")
                    server == Server.DOWN -> respondError(HttpStatusCode.ServiceUnavailable)
                    path.endsWith("/places/autocomplete") ->
                        if (server == Server.GOOGLE_DOWN) respond("""{"data":{"suggestions":[],"fallback":true}}""", HttpStatusCode.OK, json)
                        else respond(suggestionsBody, HttpStatusCode.OK, json)
                    path.endsWith("/places/reverse") -> respond(
                        """{"data":{"areas":[{"placeId":"a","name":"Kemang","secondary":"Jakarta Selatan","kind":"AREA"},{"placeId":"c","name":"Jakarta Selatan","kind":"CITY"}]}}""",
                        HttpStatusCode.OK, json,
                    )
                    path.contains("/places/") ->
                        if (server == Server.NO_DETAILS) respondError(HttpStatusCode.ServiceUnavailable)
                        else respond("""{"data":{"placeId":"ChIJ-warung","lat":-8.5,"lng":119.9}}""", HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    /** The client has no sign-in behind it, like a guest: it sends no bearer token. */
    private fun picker(server: Server = Server.OK) =
        PlacePickerViewModel(TRIP, api(server), InMemoryTripRepository(store), InMemoryItineraryRepository(store))

    private fun autocompletes() = requests.value.filter { it.startsWith("/api/v1/places/autocomplete") }

    @Test
    fun searchAsksInTheActivityScopeBiasedToTheDestinationAndOnlyForTheLastQuery() = runTest {
        val vm = picker()
        vm.onQuery("W")
        vm.onQuery("Wa")
        vm.onQuery("War")
        eventually { vm.state.value.suggestions.isNotEmpty() }
        val sent = autocompletes()
        assertEquals(1, sent.size, sent.toString())
        val query = sent.single()
        assertTrue("q=War" in query && "scope=ACTIVITY" in query, query)
        assertTrue("near=-8.49" in query && "119.88" in query, "biased to the trip's destination point: $query")
        assertEquals(listOf("Warung Bajo", "Pantai Pede"), vm.state.value.suggestions.map { it.name })
        assertEquals(PlaceSearchMode.READY, vm.state.value.mode)
        assertTrue(vm.state.value.searched && !vm.state.value.searching)
    }

    @Test
    fun belowTwoLettersTheServerIsNotAsked() = runTest {
        val vm = picker()
        vm.onQuery("W")
        delay(400)
        assertTrue(autocompletes().isEmpty())
        assertFalse(vm.state.value.typing)
    }

    @Test
    fun placesOfThisTripComeFirstNewestFirstOneRowPerPlaceAndDropOutOfTheGoogleList() = runTest {
        store.items.value = listOf(
            item("a", "Pantai Pede", "ChIJ-pantai", -8.5, 119.8, LocalDate(2026, 12, 2), updated = Instant.parse("2026-11-12T11:00:00Z")),
            item("b", "Rumah Budi", updated = Instant.parse("2026-11-12T12:00:00Z")),
            item("c", "Pantai Pede", "ChIJ-pantai", -8.5, 119.8, LocalDate(2026, 12, 3)),
            item("d", null),
        )
        val vm = picker()
        eventually { vm.state.value.usedAll.size == 2 }
        // newest first; the second Pantai Pede and the item without a place are left out
        assertEquals(listOf("Rumah Budi", "Pantai Pede"), vm.state.value.usedAll.map { it.choice.name })
        assertEquals(listOf(null, 2), vm.state.value.usedAll.map { it.day })
        assertEquals(2, vm.state.value.used.size, "before typing: up to five")

        vm.onQuery("pantai")
        assertEquals(listOf("Pantai Pede"), vm.state.value.used.map { it.choice.name }, "filtered while typing")
        eventually { vm.state.value.suggestions.isNotEmpty() }
        assertEquals(listOf("Warung Bajo"), vm.state.value.googleRows.map { it.name }, "the same placeId is not shown twice")
        assertEquals(listOf("Pantai Pede", "Warung Bajo"), vm.state.value.rows.map { row ->
            when (row) {
                is app.ransel.feature.itinerary.PlacePickerRow.Used -> row.place.choice.name
                is app.ransel.feature.itinerary.PlacePickerRow.Suggestion -> row.suggestion.name
            }
        })
    }

    @Test
    fun aPlaceOfThisTripIsPickedWithoutAskingTheServer() = runTest {
        store.items.value = listOf(item("a", "Pantai Pede", "ChIJ-pantai", -8.5, 119.8, LocalDate(2026, 12, 2)))
        val vm = picker()
        eventually { vm.state.value.usedAll.isNotEmpty() }
        vm.pickUsed(vm.state.value.usedAll.single())
        assertEquals(PlaceChoice("Pantai Pede", null, "ChIJ-pantai", -8.5, 119.8), vm.picked.first())
        assertTrue(requests.value.isEmpty())
    }

    @Test
    fun pickingASuggestionAsksForThePointOnceInTheSameSessionAndWithoutATimeZone() = runTest {
        val vm = picker()
        vm.onQuery("War")
        eventually { vm.state.value.suggestions.isNotEmpty() }
        vm.pickSuggestion(vm.state.value.suggestions.first())
        val choice = vm.picked.first()
        assertEquals(PlaceChoice("Warung Bajo", "Labuan Bajo", "ChIJ-warung", -8.5, 119.9, fromSuggestion = true), choice)
        assertTrue(choice.hasPoint)

        val details = requests.value.single { it.startsWith("/api/v1/places/ChIJ-warung") }
        assertTrue("scope=ACTIVITY" in details, details)
        val auto = autocompletes().single().substringAfter("session=").substringBefore('&')
        assertEquals(auto, details.substringAfter("session=").substringBefore('&'), "the pick ends the search session")

        // a new search opens a new session
        vm.onQuery("Pan")
        eventually { autocompletes().size == 2 }
        assertNotEquals(auto, autocompletes().last().substringAfter("session=").substringBefore('&'))
    }

    @Test
    fun ifThePointCannotBeAskedTheNameStillWorksAsText() = runTest {
        val vm = picker(server = Server.NO_DETAILS)
        val suggestion = app.ransel.shared.dto.PlaceSuggestionDto("ChIJ-warung", "Warung Bajo", "Labuan Bajo", app.ransel.shared.dto.PlaceKind.AREA)
        vm.pickSuggestion(suggestion)
        val choice = vm.picked.first()
        assertEquals("Warung Bajo", choice.name)
        assertNull(choice.lat)
        assertNull(choice.placeId)
        assertFalse(choice.hasPoint)
        assertTrue(choice.fromSuggestion)
    }

    @Test
    fun typedTextIsAlwaysAWayOutAndEnterUsesItUnlessARowIsHighlighted() = runTest {
        val vm = picker()
        vm.onQuery("  Rumah Budi  ")
        vm.useTyped()
        assertEquals(PlaceChoice("Rumah Budi"), vm.picked.first())

        vm.onQuery("War")
        eventually { vm.state.value.suggestions.isNotEmpty() }
        vm.enter()
        assertEquals(PlaceChoice("War"), vm.picked.first(), "no row highlighted: Enter means the text")

        vm.moveHighlight(-1)
        assertEquals(-1, vm.state.value.highlight, "up from nothing stays on nothing")
        vm.moveHighlight(1)
        vm.moveHighlight(1)
        vm.moveHighlight(1)
        vm.moveHighlight(1)
        assertEquals(1, vm.state.value.highlight, "stops at the last row")
        vm.enter()
        assertEquals("Pantai Pede", vm.picked.first().name)

        vm.onQuery("Wa")
        assertEquals(-1, vm.state.value.highlight, "typing clears the highlight")
    }

    @Test
    fun guestsSearchLikeEveryoneWithoutAnyBannerOrBearerToken() = runTest {
        store.items.value = listOf(item("a", "Rumah Budi"))
        val vm = picker()
        assertEquals(PlaceSearchMode.READY, vm.state.value.mode)
        vm.onQuery("Rum")
        eventually { vm.state.value.searched }
        assertEquals(1, autocompletes().size)
        assertEquals(PlaceSearchMode.READY, vm.state.value.mode)
        assertEquals(listOf("Warung Bajo", "Pantai Pede"), vm.state.value.suggestions.map { it.name })
        assertEquals(listOf("Rumah Budi"), vm.state.value.used.map { it.choice.name })

        vm.pickSuggestion(vm.state.value.suggestions.first())
        assertEquals("ChIJ-warung", vm.picked.first().placeId)
        vm.useLocation("Sekitar %s") { LocationFix.Found(-6.26, 106.81) }
        assertEquals("Sekitar Kemang", vm.picked.first().name)
        assertTrue(bearers.value.isNotEmpty() && bearers.value.all { it == null }, "no bearer token went to the server: $bearers")
    }

    @Test
    fun noRouteToTheServerIsOfflineAndAServerErrorIsUnavailable() = runTest {
        val offline = picker(server = Server.OFFLINE)
        offline.onQuery("pantai")
        eventually { offline.state.value.searched }
        assertEquals(PlaceSearchMode.OFFLINE, offline.state.value.mode)
        assertTrue(offline.state.value.suggestions.isEmpty())

        val down = picker(server = Server.DOWN)
        down.onQuery("pantai")
        eventually { down.state.value.searched }
        assertEquals(PlaceSearchMode.UNAVAILABLE, down.state.value.mode)

        val google = picker(server = Server.GOOGLE_DOWN)
        google.onQuery("pantai")
        eventually { google.state.value.searched }
        assertEquals(PlaceSearchMode.UNAVAILABLE, google.state.value.mode, "fallback = Google is down")
        assertTrue(google.state.value.suggestions.isEmpty())
        google.useTyped()
        assertEquals("pantai", google.picked.first().name)
    }

    @Test
    fun theCurrentLocationBecomesSekitarTheAreaWithTheDevicePointAndNoPlaceId() = runTest {
        val vm = picker()
        vm.useLocation("Sekitar %s") { LocationFix.Found(-6.26, 106.81) }
        val choice = vm.picked.first()
        assertEquals(PlaceChoice("Sekitar Kemang", "Jakarta Selatan", null, -6.26, 106.81), choice)
        assertTrue(choice.hasPoint)
        assertNull(choice.placeId)
        assertEquals(LocationState.Idle, vm.state.value.location)
        assertTrue(requests.value.none { it.contains("autocomplete") }, "no place search for the location")
    }

    @Test
    fun aRefusedOrFailedLocationLeavesTheWayOutOpen() = runTest {
        val vm = picker()
        vm.useLocation("Sekitar %s") { LocationFix.Failed(ErrorCode.LOCATION_DENIED) }
        assertEquals(LocationState.Denied, vm.state.value.location)

        vm.useLocation("Sekitar %s") { LocationFix.Failed(ErrorCode.LOCATION_TIMEOUT) }
        assertTrue(vm.state.value.location is LocationState.Failed)

        val offline = picker(server = Server.OFFLINE)
        offline.useLocation("Sekitar %s") { LocationFix.Found(-6.26, 106.81) }
        eventually { offline.state.value.location is LocationState.Failed }
        offline.onQuery("Rumah")
        offline.useTyped()
        assertEquals("Rumah", offline.picked.first().name)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityFormPlaceTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val store = InMemoryStore().apply { trips.value = listOf(tripWithDestination()) }
    private val itinerary = InMemoryItineraryRepository(store)

    private fun form(itemId: String? = null) =
        ActivityFormViewModel(TRIP, itemId, "2026-12-01", InMemoryTripRepository(store), itinerary)

    private suspend fun ActivityFormViewModel.ready() = eventually { !state.value.loading }

    private val suggestion = PlaceChoice("Warung Bajo", "Labuan Bajo", "ChIJ-warung", -8.5, 119.9, fromSuggestion = true)

    @Test
    fun aSuggestionFillsTheFieldWithItsPointAndAnEmptyTitleTakesTheName() = runTest {
        val vm = form()
        vm.ready()
        vm.onPlacePicked(suggestion)
        val s = vm.state.value
        assertEquals("Warung Bajo", s.placeName)
        assertEquals("Labuan Bajo", s.placeSecondary)
        assertTrue(s.placeHasPoint)
        assertEquals("Warung Bajo", s.title)

        // a title the user already typed stays
        val typed = form()
        typed.ready()
        typed.onTitle("Makan malam")
        typed.onPlacePicked(suggestion)
        assertEquals("Makan malam", typed.state.value.title)
    }

    @Test
    fun savingKeepsTheNameTheIdAndThePointAndSyncsThemWithTheItem() = runTest {
        val vm = form()
        vm.ready()
        vm.onPlacePicked(suggestion)
        vm.save()
        eventually { store.items.value.isNotEmpty() }
        val saved = store.items.value.single()
        assertEquals("Warung Bajo", saved.placeName)
        assertEquals("ChIJ-warung", saved.placeId)
        assertEquals(-8.5, saved.lat)
        assertEquals(119.9, saved.lng)
    }

    @Test
    fun freeTextIsSavedWithoutAPointAndTypingItOverAPlaceDropsTheOldPoint() = runTest {
        store.items.value = listOf(item("a", "Warung Bajo", "ChIJ-warung", -8.5, 119.9, LocalDate(2026, 12, 1)))
        val vm = form("a")
        vm.ready()
        assertTrue(vm.state.value.placeHasPoint, "an item with a point opens with it (green pin)")

        vm.onPlacePicked(PlaceChoice("Rumah Budi"))
        assertFalse(vm.state.value.placeHasPoint)
        assertNull(vm.state.value.placeId)
        vm.save()
        eventually { store.items.value.single().placeName == "Rumah Budi" }
        val saved = store.items.value.single()
        assertNull(saved.placeId)
        assertNull(saved.lat)
        assertNull(saved.lng)
    }

    @Test
    fun anOldFreeTextActivityGetsAPointWhenASuggestionIsPicked() = runTest {
        store.items.value = listOf(item("a", "Warung", day = LocalDate(2026, 12, 1)))
        val vm = form("a")
        vm.ready()
        assertFalse(vm.state.value.placeHasPoint)
        vm.onPlacePicked(suggestion)
        assertEquals("Aktivitas a", vm.state.value.title, "the title of an existing activity is not replaced")
        vm.save()
        eventually { store.items.value.single().lat != null }
        assertEquals("ChIJ-warung", store.items.value.single().placeId)
    }

    @Test
    fun anExploreActivityKeepsItsPlaceIdAndPointWhenTheFieldIsNotTouched() = runTest {
        store.items.value = listOf(item("a", "Pantai Pede", "ChIJ-pantai", -8.5, 119.8))
        val vm = form("a")
        vm.ready()
        vm.onTitle("Pantai sore")
        vm.save()
        eventually { store.items.value.single().title == "Pantai sore" }
        val saved = store.items.value.single()
        assertEquals("ChIJ-pantai", saved.placeId)
        assertEquals(-8.5, saved.lat)
        assertEquals(119.8, saved.lng)
    }

    @Test
    fun theXClearsTheNameTheIdAndThePointTogether() = runTest {
        val vm = form()
        vm.ready()
        vm.onTitle("Makan")
        vm.onPlacePicked(suggestion)
        vm.onPlaceCleared()
        val s = vm.state.value
        assertEquals("", s.placeName)
        assertNull(s.placeSecondary)
        assertNull(s.placeId)
        assertFalse(s.placeHasPoint)
        vm.save()
        eventually { store.items.value.isNotEmpty() }
        val saved = store.items.value.single()
        assertNull(saved.placeName)
        assertNull(saved.placeId)
        assertNull(saved.lat)
    }

    @Test
    fun theCurrentLocationHasAPointButNoPlaceId() = runTest {
        val vm = form()
        vm.ready()
        vm.onPlacePicked(PlaceChoice("Sekitar Kemang", "Jakarta Selatan", null, -6.26, 106.81))
        assertTrue(vm.state.value.placeHasPoint)
        assertEquals("", vm.state.value.title, "only a Google suggestion names the activity")
        vm.onTitle("Jalan pagi")
        vm.save()
        eventually { store.items.value.isNotEmpty() }
        val saved = store.items.value.single()
        assertNull(saved.placeId)
        assertEquals(-6.26, saved.lat)
        assertEquals(106.81, saved.lng)
    }
}
