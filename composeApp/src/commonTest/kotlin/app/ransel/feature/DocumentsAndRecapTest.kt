package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryExpenseRepository
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPackingRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.documents.DocumentsEvent
import app.ransel.feature.documents.DocumentsViewModel
import app.ransel.feature.documents.PickedFile
import app.ransel.feature.documents.viewer.PhotoLoad
import app.ransel.feature.recap.RecapViewModel
import app.ransel.platform.DeviceFiles
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.MeDto
import app.ransel.shared.model.DocumentCategory
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
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import app.ransel.stopTestMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

/** A phone's file folder in memory. */
private class FakeFiles : DeviceFiles {
    val saved = mutableMapOf<String, ByteArray>()
    val opened = mutableListOf<String>()
    override val keepsFiles = true
    override suspend fun has(name: String) = name in saved
    override suspend fun read(name: String) = saved[name]
    override suspend fun save(name: String, bytes: ByteArray) { saved[name] = bytes }
    override suspend fun open(name: String) { opened += name }
    override suspend fun delete(name: String) { saved.remove(name) }
    override suspend fun share(fileName: String, bytes: ByteArray) = Unit
}

private fun photoDoc(id: String) = """{"id":"$id","tripId":"t1","title":"Foto $id","category":"TRAVEL","kind":"FILE","fileName":"$id.jpg","mime":"image/jpeg","sizeBytes":3,"uploadedByMemberId":"m","uploadedByName":"Ayu","createdAt":"2026-10-26T00:00:00Z"}"""

private const val DOC = """{"id":"d1","tripId":"t1","title":"Tiket pesawat","category":"TRAVEL","kind":"FILE","fileName":"tiket.pdf","mime":"application/pdf","sizeBytes":3,"uploadedByMemberId":"m","uploadedByName":"Ayu","createdAt":"2026-10-26T00:00:00Z"}"""

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsAndRecapTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val requests = MutableStateFlow<List<String>>(emptyList())
    private var offline = false
    private var unreachable = false
    private var withPhotos = false

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                requests.update { it + "${request.method.value} $path" }
                if (unreachable) error("no signal")
                when {
                    offline -> respondError(HttpStatusCode.ServiceUnavailable)
                    path.endsWith("/download") -> respond("""{"data":{"url":"/files/documents/d1?exp=1&sig=x","expiresAt":"2099-01-01T00:00:00Z"}}""", HttpStatusCode.OK, json)
                    path.startsWith("/api/v1/files/") -> respond(byteArrayOf(37, 80, 68), HttpStatusCode.OK)
                    path.endsWith("/documents") && request.method == HttpMethod.Post ->
                        respond("""{"data":${DOC.replace("d1", "d2")}}""", HttpStatusCode.Created, json)
                    path.endsWith("/documents") -> respond("""{"data":{"items":[$DOC${if (withPhotos) ",${photoDoc("d3")},${photoDoc("d4")}" else ""}],"usedBytes":3}}""", HttpStatusCode.OK, json)
                    path.endsWith("/trip-templates") -> respond(
                        """{"data":[{"id":"tt","name":"Bali","dayCount":2,"items":[{"dayIndex":1,"title":"Pantai"},{"dayIndex":5,"title":"Jauh"}],"packing":[{"category":"OTHER","name":"Kompor","quantity":2}],"createdAt":"2026-10-26T00:00:00Z"}]}""",
                        HttpStatusCode.OK, json,
                    )
                    else -> respond("""{"error":{"code":"NOT_FOUND","message":"x"}}""", HttpStatusCode.NotFound, json)
                }
            },
        ),
        origin = "http://api.test",
    )

    @Test
    fun thePhotoViewerListsThePhotosOfAGroupAndKeepsWhatItLoadsForUseWithoutSignal() = runTest {
        withPhotos = true
        val files = FakeFiles()
        val vm = DocumentsViewModel("t1", api(), InMemoryPreferencesRepository(), files, InMemoryItineraryRepository(InMemoryStore()), signedIn())
        eventually { !vm.state.value.loading }
        val docs = vm.state.value.documents
        assertEquals(false, vm.isPhoto(docs.first { it.id == "d1" }), "a PDF opens in another app")
        val request = vm.viewerFor(docs.first { it.id == "d4" })
        assertEquals(listOf("d3", "d4"), request.photos.map { it.id }, "only the photos of the group")
        assertEquals(1, request.startIndex)

        assertTrue(vm.photoLoader.load(request.photos[0]) is PhotoLoad.Ready)
        assertTrue("d3.jpg" in files.saved, "kept on the phone once opened")
        unreachable = true
        assertTrue(vm.photoLoader.load(request.photos[0]) is PhotoLoad.Ready, "opens without a signal")
        assertEquals(PhotoLoad.Offline, vm.photoLoader.load(request.photos[1]), "not kept and no signal")
        unreachable = false
        offline = true
        assertEquals(PhotoLoad.Failed, vm.photoLoader.load(request.photos[1]), "the server answered with an error")
    }

    @Test
    fun theGestureHintShowsOnTheFirstThreeOpenings() = runTest {
        val prefs = InMemoryPreferencesRepository()
        val vm = DocumentsViewModel("t1", api(), prefs, FakeFiles(), InMemoryItineraryRepository(InMemoryStore()), signedIn())
        repeat(3) {
            assertTrue(vm.viewerHintDue())
            vm.viewerOpened()
            assertEquals((it + 1).toString(), prefs.getString(DocumentsViewModel.HINT_KEY))
        }
        assertEquals(false, vm.viewerHintDue())
    }

    @Test
    fun documentsAreKeptOnThePhoneAndListedOffline() = runTest {
        val store = InMemoryStore()
        val prefs = InMemoryPreferencesRepository()
        val files = FakeFiles()
        val vm = DocumentsViewModel("t1", api(), prefs, files, InMemoryItineraryRepository(store), signedIn())
        eventually { !vm.state.value.loading }
        assertEquals(listOf("Tiket pesawat"), vm.state.value.documents.map { it.title })

        vm.open(vm.state.value.documents.single())
        eventually { files.opened.isNotEmpty() }
        assertEquals(listOf("d1.pdf"), files.opened, "saved with its extension so the viewer knows it")
        assertTrue("d1" in vm.state.value.kept)

        // no signal: the saved list, and the file opens without asking the server
        offline = true
        val before = requests.value.size
        val again = DocumentsViewModel("t1", api(), prefs, files, InMemoryItineraryRepository(store), signedIn())
        eventually { !again.state.value.loading }
        assertTrue(again.state.value.fromDevice)
        again.open(again.state.value.documents.single())
        eventually { files.opened.size == 2 }
        assertEquals(before + 1, requests.value.size, "only the failed list request went out")
        vm.viewModelScope.cancel()
        again.viewModelScope.cancel()
    }

    @Test
    fun anUploadKeepsTheFileAndCheckesTheInput() = runTest {
        val files = FakeFiles()
        val vm = DocumentsViewModel("t1", api(), InMemoryPreferencesRepository(), files, InMemoryItineraryRepository(InMemoryStore()), signedIn())
        eventually { !vm.state.value.loading }
        vm.add("", DocumentCategory.TRAVEL, null, null, "")
        assertEquals(DocumentsEvent.Problem(DocumentsViewModel.TITLE), vm.eventFlow.first())
        vm.add("Voucher", DocumentCategory.ACTIVITY, null, null, "ftp://contoh")
        assertEquals(DocumentsEvent.Problem(DocumentsViewModel.LINK), vm.eventFlow.first())

        vm.add("Tiket pulang", DocumentCategory.TRAVEL, null, PickedFile("pulang.pdf", byteArrayOf(1, 2, 3)), "")
        assertEquals(DocumentsEvent.Added, vm.eventFlow.first())
        assertTrue("d2.pdf" in files.saved, "the uploader has it offline at once")
        assertEquals(null, vm.state.value.uploadProgress, "the surfing progress closes once the upload is done")
        vm.viewModelScope.cancel()
    }

    @Test
    fun theRecapCountsTheTripsDays() = runTest {
        val store = InMemoryStore()
        val trips = InMemoryTripRepository(store)
        trips.create(CreateTripRequest("t1", "Bali", "Bali", LocalDate(2026, 11, 20), LocalDate(2026, 11, 22), "IDR"))
        val recap = RecapViewModel("t1", trips, InMemoryItineraryRepository(store), InMemoryExpenseRepository(store), api(), signedIn())
        eventually { recap.state.value.recap != null }
        assertEquals(3, recap.state.value.recap?.days)
        recap.viewModelScope.cancel()
    }
}
