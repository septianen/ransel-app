package app.ransel.feature

import app.ransel.data.auth.AuthRepository
import app.ransel.data.auth.EmailVerification
import app.ransel.data.auth.NoSessionHooks
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.credit.CreditsStore
import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.explore.SwipeUiState
import app.ransel.feature.explore.SwipeViewModel
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.model.Interest
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
import kotlin.time.Instant

/** "Pencarian menunggu verifikasi email" and "Lanjut mencari" (spec 1572903, "State lain"), and "Batalkan pencarian". */
@OptIn(ExperimentalCoroutinesApi::class)
class SwipeWaitsForEmailTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private var verifiedOnServer = false
    private var searches = 0

    private val api = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/recommendations") -> {
                        searches++
                        if (!verifiedOnServer) {
                            respond("""{"error":{"code":"EMAIL_NOT_VERIFIED","message":"x"}}""", HttpStatusCode.Forbidden, json)
                        } else {
                            respond(
                                """{"data":{"groups":[{"category":"FOOD","items":[{"placeId":"a","name":"A","reason":"r","suggestedDurationMin":60,"lat":-8.5,"lng":115.2}]}],"credits":{"balance":27,"dailyFreeLeft":0,"dailyFreeLimit":3,"resetsAt":"2026-10-16T17:00:00Z","costs":{"search":3,"nextBatch":1,"placeDetail":0}}}}""",
                                HttpStatusCode.OK, json,
                            )
                        }
                    }
                    path.endsWith("/me") ->
                        respond("""{"data":{"id":"u1","email":"ayu@contoh.id","displayName":"Ayu","emailVerified":$verifiedOnServer}}""", HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun session(): SessionManager {
        val store = object : TokenStore {
            var saved: StoredSession? = null
            override suspend fun read() = saved
            override suspend fun write(session: StoredSession) { saved = session }
            override suspend fun clear() { saved = null }
        }
        return SessionManager(store) { error("not used") }.also {
            it.start(AuthSessionDto("a", Instant.parse("2099-01-01T00:00:00Z"), "r", MeDto("u1", "ayu@contoh.id", "Ayu", emailVerified = false)))
        }
    }

    private val form = ExploreForm(
        tripId = "t1", tripTitle = "Trip Bandung 3 hari", destination = "Bandung",
        startDate = LocalDate(2026, 12, 20), endDate = LocalDate(2026, 12, 22), interests = listOf(Interest.FOOD),
    )

    private suspend fun vm(): Pair<SwipeViewModel, ExploreSessionStore> {
        val sessions = ExploreSessionStore(InMemoryPreferencesRepository()).also { it.save(ExploreSession(form)) }
        val manager = session()
        val vm = SwipeViewModel(
            "t1", sessions, api, InMemoryItineraryRepository(InMemoryStore()), CreditsStore(),
            auth = AuthRepository(api, manager, NoSessionHooks, EmailVerification()), sessionManager = manager,
        )
        return vm to sessions
    }

    @Test
    fun aSearchThatWaitsForTheEmailShowsTheGateAndContinuesOnceVerified() = runTest {
        val (vm, _) = vm()
        eventually { vm.state.value is SwipeUiState.VerifyEmail }
        val waiting = assertIs<SwipeUiState.VerifyEmail>(vm.state.value)
        assertEquals("ayu@contoh.id", waiting.email)
        assertFalse(waiting.verified)

        // "Sudah verifikasi" before opening the link: still waiting, with the message
        vm.checkVerified()
        eventually { !(vm.state.value as SwipeUiState.VerifyEmail).gate.checking }
        assertFalse((vm.state.value as SwipeUiState.VerifyEmail).verified)
        assertEquals(1, searches)

        // the link is opened: the button becomes "Lanjut mencari"
        verifiedOnServer = true
        vm.checkVerified()
        eventually { (vm.state.value as? SwipeUiState.VerifyEmail)?.verified == true }
        assertEquals(1, searches, "nothing runs until the user taps Lanjut mencari")

        vm.retry()
        eventually { vm.state.value is SwipeUiState.Cards }
        assertEquals(2, searches)
    }

    @Test
    fun cancellingTheSearchForgetsTheSessionAndKeepsTheTrip() = runTest {
        val (vm, sessions) = vm()
        eventually { vm.state.value is SwipeUiState.VerifyEmail }
        assertTrue(sessions.session("t1") != null)
        vm.cancelSearch()
        eventually { sessions.session("t1") == null }
        assertNull(vm.searchForm)
    }
}
