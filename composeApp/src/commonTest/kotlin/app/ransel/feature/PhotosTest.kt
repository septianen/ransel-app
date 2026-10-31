package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.MemberPhotos
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.tripform.TripFormViewModel
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.dto.UpdateTripRequest
import app.ransel.shared.model.Role
import app.ransel.stopTestMain
import app.ransel.ui.components.PhotoProblem
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val json = headersOf(HttpHeaders.ContentType, "application/json")
private val stamp = Instant.parse("2026-10-31T10:00:00Z")
private const val COVER = "/files/images/6f1d1d3e-0000-4000-8000-000000000001"

private fun trip(role: Role = Role.OWNER, cover: String? = null) = TripDto(
    id = "t1", ownerId = "u1", title = "Bali", destination = "Bali", baseCurrency = "IDR",
    coverUrl = cover, myRole = role, createdAt = stamp, updatedAt = stamp,
)

private fun member(memberId: String, userId: String?, avatar: String?) = TripMemberDto(
    tripId = "t1", memberId = memberId, userId = userId, displayName = memberId, role = Role.EDITOR, joinedAt = stamp, avatarUrl = avatar,
)

class MemberPhotosTest {
    @Test
    fun aMembersListFillsPhotosByMemberAndUserAndMyOwnChangeReachesMyMemberships() {
        MemberPhotos.record(listOf(member("m1", "u1", "/files/images/a"), member("m2", null, null)))
        assertEquals("/files/images/a", MemberPhotos.urlFor("m1"))
        assertEquals("/files/images/a", MemberPhotos.urlFor("u1"))
        assertNull(MemberPhotos.urlFor("m2"))

        MemberPhotos.recordUser("u1", "/files/images/b")
        assertEquals("/files/images/b", MemberPhotos.urlFor("m1"))
        MemberPhotos.recordUser("u1", null)
        assertNull(MemberPhotos.urlFor("m1"), "a removed photo goes back to initials")
    }
}

class TripCoverFormTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val requests = MutableStateFlow<List<String>>(emptyList())
    private var answer: Pair<HttpStatusCode, String> = HttpStatusCode.OK to ""

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                requests.update { it + "${request.method.value} ${request.url.encodedPath} ${request.body.contentType}" }
                respond(answer.second, answer.first, json)
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
            it.start(AuthSessionDto("access", Instant.parse("2099-01-01T00:00:00Z"), "refresh", MeDto("u1", "ayu@contoh.id", "Ayu")))
        }
    }

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    @Test
    fun anEditorChangesTheCoverAndTheLocalTripKeepsIt() = runTest {
        val store = InMemoryStore().also { it.trips.value = listOf(trip()) }
        val trips = InMemoryTripRepository(store)
        val vm = TripFormViewModel("t1", trips, InMemoryPreferencesRepository(), api = api(), session = signedIn())
        backgroundScope.launch { vm.state.collect {} }
        eventually { !vm.state.value.loading }
        assertTrue(vm.state.value.canChangeCover)

        answer = HttpStatusCode.OK to """{"data":{"id":"t1","ownerId":"u1","title":"Bali","destination":"Bali","baseCurrency":"IDR",
            "coverUrl":"$COVER","myRole":"OWNER","createdAt":"$stamp","updatedAt":"$stamp"}}"""
        vm.changeCover(byteArrayOf(1, 2, 3))
        eventually { vm.state.value.coverUrl == COVER }
        assertFalse(vm.state.value.coverBusy)
        assertTrue(requests.value.single().startsWith("PUT /api/v1/trips/t1/cover image/jpeg"))
        assertEquals(COVER, store.trips.value.single().coverUrl)

        // saving the form afterwards keeps the cover (the server owns it)
        trips.update("t1", UpdateTripRequest(title = "Bali lagi", destination = "Bali", baseCurrency = "IDR"))
        assertEquals(COVER, store.trips.value.single().coverUrl)
    }

    @Test
    fun aFailedUploadSaysWhyAndViewersAndGuestsGetNoButton() = runTest {
        val store = InMemoryStore().also { it.trips.value = listOf(trip()) }
        val vm = TripFormViewModel("t1", InMemoryTripRepository(store), InMemoryPreferencesRepository(), api = api(), session = signedIn())
        backgroundScope.launch { vm.state.collect {} }
        eventually { !vm.state.value.loading }
        answer = HttpStatusCode.PayloadTooLarge to """{"error":{"code":"FILE_TOO_LARGE","message":"x"}}"""
        vm.changeCover(byteArrayOf(1))
        eventually { vm.state.value.coverProblem != null }
        assertEquals(PhotoProblem.TOO_LARGE, vm.state.value.coverProblem)
        assertNull(store.trips.value.single().coverUrl)

        val viewerStore = InMemoryStore().also { it.trips.value = listOf(trip(Role.VIEWER)) }
        val viewer = TripFormViewModel("t1", InMemoryTripRepository(viewerStore), InMemoryPreferencesRepository(), api = api(), session = signedIn())
        backgroundScope.launch { viewer.state.collect {} }
        eventually { !viewer.state.value.loading }
        assertFalse(viewer.state.value.canChangeCover)

        val guest = TripFormViewModel("t1", InMemoryTripRepository(store), InMemoryPreferencesRepository())
        backgroundScope.launch { guest.state.collect {} }
        eventually { !guest.state.value.loading }
        assertFalse(guest.state.value.canChangeCover)
    }
}
