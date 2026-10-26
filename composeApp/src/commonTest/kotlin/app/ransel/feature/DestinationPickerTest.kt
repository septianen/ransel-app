package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.destination.DestinationChoice
import app.ransel.feature.destination.DestinationPickerViewModel
import app.ransel.feature.destination.LocationState
import app.ransel.platform.LocationFix
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(10) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationPickerTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val requests = MutableStateFlow<List<String>>(emptyList())

    private suspend fun session(signedIn: Boolean): SessionManager {
        val store = object : TokenStore {
            var saved: StoredSession? = null
            override suspend fun read() = saved
            override suspend fun write(session: StoredSession) { saved = session }
            override suspend fun clear() { saved = null }
        }
        return SessionManager(store) { error("not used") }.also {
            if (signedIn) it.start(AuthSessionDto("a", Instant.parse("2099-01-01T00:00:00Z"), "r", MeDto("u1", "ayu@contoh.id", "Ayu")))
        }
    }

    private fun api(offline: Boolean = false) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                requests.update { it + (path + "?" + request.url.encodedQuery) }
                when {
                    offline -> respondError(HttpStatusCode.ServiceUnavailable)
                    path.endsWith("/places/autocomplete") -> respond(
                        """{"data":{"suggestions":[{"placeId":"ChIJ-bajo","name":"Labuan Bajo","secondary":"Manggarai Barat, Nusa Tenggara Timur","kind":"CITY","matchRanges":[{"start":0,"end":3}]}],"fallback":false}}""",
                        HttpStatusCode.OK, json,
                    )
                    path.endsWith("/places/popular") -> respond("""{"data":[{"placeId":"ChIJ-yogya","name":"Yogyakarta"}]}""", HttpStatusCode.OK, json)
                    path.endsWith("/places/reverse") -> respond(
                        """{"data":{"areas":[{"placeId":"a","name":"Kemang","secondary":"Jakarta Selatan","kind":"AREA"},{"placeId":"c","name":"Jakarta Selatan","kind":"CITY"},{"placeId":"c","name":"Jakarta Selatan","kind":"AREA","around":true}]}}""",
                        HttpStatusCode.OK, json,
                    )
                    path.contains("/places/") -> respond(
                        """{"data":{"placeId":"ChIJ-bajo","lat":-8.49,"lng":119.88,"countryCode":"ID"}}""", HttpStatusCode.OK, json,
                    )
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    @Test
    fun suggestionsWaitForTwoCharactersAndOnlyTheLastQueryIsSent() = runTest {
        val vm = DestinationPickerViewModel(api(), session(true), InMemoryPreferencesRepository())
        vm.onQuery("L")
        vm.onQuery("La")
        vm.onQuery("Lab")
        eventually { vm.state.value.suggestions.isNotEmpty() }
        val sent = requests.value.filter { it.startsWith("/api/v1/places/autocomplete") }
        assertEquals(1, sent.size, sent.toString())
        assertTrue("q=Lab" in sent.single())
        assertEquals("Labuan Bajo", vm.state.value.suggestions.single().name)
        assertEquals(listOf("Yogyakarta"), vm.state.value.popular.map { it.name })
    }

    @Test
    fun pickingAsksForThePointRotatesTheSessionAndRemembersTheRecent() = runTest {
        val prefs = InMemoryPreferencesRepository()
        val vm = DestinationPickerViewModel(api(), session(true), prefs)
        vm.onQuery("Lab")
        eventually { vm.state.value.suggestions.isNotEmpty() }
        vm.pick(vm.state.value.suggestions.single())
        val choice = vm.picked.first()
        assertEquals(DestinationChoice("Labuan Bajo", app.ransel.shared.dto.TripPlaceDto("ChIJ-bajo", -8.49, 119.88), "ID"), choice)

        val auto = requests.value.first { it.startsWith("/api/v1/places/autocomplete") }.substringAfter("session=").substringBefore('&')
        val details = requests.value.first { it.startsWith("/api/v1/places/ChIJ-bajo") }.substringAfter("session=").substringBefore('&')
        assertEquals(auto, details, "the pick ends the same session")
        assertEquals("Labuan Bajo", vm.state.value.recents.first().name)
        assertTrue(prefs.getString(DestinationPickerViewModel.RECENTS_KEY)!!.contains("Labuan Bajo"))
    }

    @Test
    fun guestsAndOfflineUsersSearchTheBuiltInList() = runTest {
        val guest = DestinationPickerViewModel(api(), session(false), InMemoryPreferencesRepository())
        guest.onQuery("bandung")
        eventually { guest.state.value.suggestions.isNotEmpty() }
        assertTrue(guest.state.value.fallback)
        assertEquals("Bandung", guest.state.value.suggestions.first().name)
        assertTrue(requests.value.none { it.contains("autocomplete") })

        val offline = DestinationPickerViewModel(api(offline = true), session(true), InMemoryPreferencesRepository())
        offline.onQuery("malang")
        eventually { offline.state.value.suggestions.isNotEmpty() }
        assertTrue(offline.state.value.fallback)
        // a built-in suggestion has no point: the name is used as typed
        offline.pick(offline.state.value.suggestions.first())
        assertNull(offline.picked.first().place)
    }

    @Test
    fun theCurrentLocationBecomesAreasOrSaysThatItIsOff() = runTest {
        val vm = DestinationPickerViewModel(api(), session(true), InMemoryPreferencesRepository())
        vm.useLocation { LocationFix.Failed(ErrorCode.LOCATION_DENIED) }
        assertEquals(LocationState.Denied, vm.state.value.location)

        vm.useLocation { LocationFix.Found(-6.26, 106.81) }
        eventually { vm.state.value.location is LocationState.Areas }
        val areas = (vm.state.value.location as LocationState.Areas).areas
        assertEquals(listOf("Kemang", "Jakarta Selatan", "Jakarta Selatan"), areas.map { it.name })

        vm.pickArea(areas.last(), "Sekitar %s")
        assertEquals("Sekitar Jakarta Selatan", vm.picked.first().name)
    }

    @Test
    fun typedTextCanBeUsedAsIs() = runTest {
        val vm = DestinationPickerViewModel(api(), session(true), InMemoryPreferencesRepository())
        vm.onQuery("  Kampung Halaman  ")
        vm.useTyped()
        assertEquals(DestinationChoice("Kampung Halaman"), vm.picked.first())
    }
}

