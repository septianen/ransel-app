package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.albums.AlbumGalleryViewModel
import app.ransel.feature.albums.AlbumSource
import app.ransel.feature.albums.AlbumsEvent
import app.ransel.feature.albums.AlbumsViewModel
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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

private const val ALBUM = """{"id":"a1","tripId":"t1","url":"https://drive.google.com/drive/folders/1AbCdEfGhIjKlMn","provider":"DRIVE_FOLDER","title":"Foto Bali","addedByMemberId":"m","addedByName":"Ayu","createdAt":"2026-10-31T00:00:00Z"}"""

private fun photo(id: String) = """{"id":"$id","name":"$id.jpg","thumbnailUrl":"https://lh3.googleusercontent.com/d/$id=w400","imageUrl":"https://lh3.googleusercontent.com/d/$id=w2048"}"""

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val requests = MutableStateFlow<List<String>>(emptyList())
    private var offline = false
    private var folderPublic = true

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                val token = request.url.parameters["pageToken"]
                requests.update { it + "${request.method.value} $path${token?.let { t -> "?pageToken=$t" }.orEmpty()}" }
                when {
                    offline -> respondError(HttpStatusCode.ServiceUnavailable)
                    path.endsWith("/albums") && request.method == HttpMethod.Post ->
                        if (folderPublic) respond("""{"data":${ALBUM.replace("a1", "a2")}}""", HttpStatusCode.Created, json)
                        else respond("""{"error":{"code":"ALBUM_NOT_PUBLIC","message":"x"}}""", HttpStatusCode.UnprocessableEntity, json)
                    path.endsWith("/albums") -> respond("""{"data":{"items":[$ALBUM]}}""", HttpStatusCode.OK, json)
                    path.endsWith("/items") && token == null -> respond("""{"data":{"items":[${photo("p1")},${photo("p2")}],"nextPageToken":"next"}}""", HttpStatusCode.OK, json)
                    path.endsWith("/items") -> respond("""{"data":{"items":[${photo("p2")},${photo("p3")}]}}""", HttpStatusCode.OK, json)
                    else -> respond("""{"error":{"code":"NOT_FOUND","message":"x"}}""", HttpStatusCode.NotFound, json)
                }
            },
        ),
        origin = "http://api.test",
    )

    @Test
    fun albumsAreListedAndKeptForOffline() = runTest {
        val prefs = InMemoryPreferencesRepository()
        val online = AlbumsViewModel("t1", api(), prefs, signedIn())
        eventually { !online.state.value.loading }
        assertEquals(listOf("a1"), online.state.value.albums.map { it.id })

        offline = true
        val later = AlbumsViewModel("t1", api(), prefs, signedIn())
        eventually { !later.state.value.loading }
        assertTrue(later.state.value.fromDevice)
        assertEquals(listOf("a1"), later.state.value.albums.map { it.id }, "the last list shows without signal")
    }

    @Test
    fun aPrivateFolderStaysInTheSheetWithItsCode() = runTest {
        folderPublic = false
        val vm = AlbumsViewModel("t1", api(), InMemoryPreferencesRepository(), signedIn())
        eventually { !vm.state.value.loading }

        vm.add("bukan link", "")
        assertEquals(AlbumsViewModel.URL, vm.state.value.addProblem, "checked on the phone, nothing sent")
        assertTrue(requests.value.none { it.startsWith("POST") })

        vm.add("https://drive.google.com/drive/folders/1AbCdEfGhIjKlMn", "Foto Bali")
        eventually { !vm.state.value.saving && vm.state.value.addProblem != null }
        assertEquals(ErrorCode.ALBUM_NOT_PUBLIC, vm.state.value.addProblem)

        folderPublic = true
        vm.clearProblem()
        vm.add("https://drive.google.com/drive/folders/1AbCdEfGhIjKlMn", "Foto Bali")
        assertEquals(AlbumsEvent.Added, vm.eventFlow.first())
        assertNull(vm.state.value.addProblem)
    }

    @Test
    fun theGalleryLoadsPageByPageWithoutDoubles() = runTest {
        val vm = AlbumGalleryViewModel(AlbumSource("a1", tripId = "t1"), api())
        eventually { !vm.state.value.loading }
        assertEquals(listOf("p1", "p2"), vm.state.value.items.map { it.id })
        assertEquals("next", vm.state.value.nextPageToken)

        vm.loadMore()
        eventually { !vm.state.value.loadingMore && vm.state.value.nextPageToken == null }
        assertEquals(listOf("p1", "p2", "p3"), vm.state.value.items.map { it.id })
        assertTrue("GET /api/v1/trips/t1/albums/a1/items?pageToken=next" in requests.value, requests.value.toString())

        vm.loadMore()
        assertEquals(2, requests.value.count { "/items" in it }, "no page is left to ask for")
    }

    @Test
    fun theGuestPageAsksWithItsCode() = runTest {
        val vm = AlbumGalleryViewModel(AlbumSource("a1", guestCode = "ABC123"), api())
        eventually { !vm.state.value.loading }
        assertEquals(2, vm.state.value.items.size)
        assertTrue("GET /api/v1/guest/ABC123/albums/a1/items" in requests.value, requests.value.toString())
    }

    @Test
    fun aFailedFirstPageKeepsItsCode() = runTest {
        offline = true
        val vm = AlbumGalleryViewModel(AlbumSource("a1", tripId = "t1"), api())
        eventually { !vm.state.value.loading }
        assertTrue(vm.state.value.failure != null)
        assertTrue(vm.state.value.items.isEmpty())
    }
}
