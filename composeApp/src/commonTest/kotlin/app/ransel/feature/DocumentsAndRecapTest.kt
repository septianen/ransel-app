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
import app.ransel.feature.recap.RecapViewModel
import app.ransel.feature.tripform.TripTemplatesViewModel
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
    override suspend fun save(name: String, bytes: ByteArray) { saved[name] = bytes }
    override suspend fun open(name: String) { opened += name }
    override suspend fun delete(name: String) { saved.remove(name) }
    override suspend fun share(fileName: String, bytes: ByteArray) = Unit
}

private const val DOC = """{"id":"d1","tripId":"t1","title":"Tiket pesawat","category":"TRAVEL","kind":"FILE","fileName":"tiket.pdf","mime":"application/pdf","sizeBytes":3,"uploadedByMemberId":"m","uploadedByName":"Ayu","createdAt":"2026-10-26T00:00:00Z"}"""

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsAndRecapTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val requests = MutableStateFlow<List<String>>(emptyList())
    private var offline = false

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                requests.update { it + "${request.method.value} $path" }
                when {
                    offline -> respondError(HttpStatusCode.ServiceUnavailable)
                    path.endsWith("/download") -> respond("""{"data":{"url":"/files/documents/d1?exp=1&sig=x","expiresAt":"2099-01-01T00:00:00Z"}}""", HttpStatusCode.OK, json)
                    path.startsWith("/api/v1/files/") -> respond(byteArrayOf(37, 80, 68), HttpStatusCode.OK)
                    path.endsWith("/documents") && request.method == HttpMethod.Post ->
                        respond("""{"data":${DOC.replace("d1", "d2")}}""", HttpStatusCode.Created, json)
                    path.endsWith("/documents") -> respond("""{"data":{"items":[$DOC],"usedBytes":3}}""", HttpStatusCode.OK, json)
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
    fun aTemplateFillsTheNewTripAndTheRecapCountsIt() = runTest {
        val store = InMemoryStore()
        val trips = InMemoryTripRepository(store)
        val itinerary = InMemoryItineraryRepository(store)
        val packing = InMemoryPackingRepository(store)
        trips.create(CreateTripRequest("t1", "Bali", "Bali", LocalDate(2026, 11, 20), LocalDate(2026, 11, 22), "IDR"))
        val templates = TripTemplatesViewModel(api(), signedIn(), itinerary, packing)
        eventually { templates.state.value.templates.isNotEmpty() }
        templates.choose(templates.state.value.templates.single())
        var done = false
        templates.applyTo("t1", LocalDate(2026, 11, 20), LocalDate(2026, 11, 22)) { done = true }
        eventually { done }
        val items = itinerary.observe("t1").first()
        assertEquals(setOf(LocalDate(2026, 11, 21), null), items.map { it.dayDate }.toSet(), "day 6 does not fit a 3-day trip")
        assertEquals(2, packing.observe("t1").first().single().quantity)

        val recap = RecapViewModel("t1", trips, itinerary, InMemoryExpenseRepository(store), api(), signedIn())
        eventually { recap.state.value.recap != null }
        assertEquals(3, recap.state.value.recap?.days)
        templates.viewModelScope.cancel()
        recap.viewModelScope.cancel()
    }
}
