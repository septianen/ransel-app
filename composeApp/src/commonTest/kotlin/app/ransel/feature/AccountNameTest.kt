package app.ransel.feature

import app.ransel.data.auth.AuthRepository
import app.ransel.data.auth.EmailVerification
import app.ransel.data.auth.NoSessionHooks
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.credit.CreditsStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.notification.PushRegistrar
import app.ransel.data.notification.PushRegistration
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.data.sync.NoSyncStatus
import app.ransel.data.voting.VotingStore
import app.ransel.feature.account.AccountViewModel
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountNameTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val patches = mutableListOf<String>()
    private var offline = false

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request: HttpRequestData ->
                when {
                    offline -> respondError(HttpStatusCode.ServiceUnavailable)
                    request.method == HttpMethod.Patch && request.url.encodedPath.endsWith("/me") -> {
                        val body = request.body.toByteArray().decodeToString()
                        patches += body
                        val name = Regex("\"displayName\":\"([^\"]*)\"").find(body)!!.groupValues[1]
                        respond("""{"data":{"id":"u1","email":"ayu@contoh.id","displayName":"$name"}}""", HttpStatusCode.OK, json)
                    }
                    else -> respond("", HttpStatusCode.NoContent)
                }
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
            it.start(AuthSessionDto("a", Instant.parse("2099-01-01T00:00:00Z"), "r", MeDto("u1", "ayu@contoh.id", "Ayu")))
        }
    }

    private object NoRegistrar : PushRegistrar {
        override val available = false
        override val platform = null
        override val appVersion = "0"
        override fun shouldExplainPermission() = false
        override fun explanationDismissed() = Unit
        override fun openSystemSettings() = Unit
        override suspend fun token() = null
        override fun hasPermission() = false
        override fun requestPermission() = Unit
        override fun deviceLanguage() = "id"
    }

    private fun vm(session: SessionManager): AccountViewModel {
        val api = api()
        val prefs = InMemoryPreferencesRepository()
        return AccountViewModel(
            prefs, session, AuthRepository(api, session, NoSessionHooks, EmailVerification()), NoSyncStatus,
            CreditsStore(), PushRegistration(NoRegistrar, api, prefs), NotificationsStore(), VotingStore(), api,
        )
    }

    @Test
    fun aNewNameIsSavedTrimmedAndShowsAtOnce() = runTest {
        val session = signedIn()
        val vm = vm(session)
        vm.saveName("  Ayu Lestari ")
        eventually { vm.name.value.saved }
        assertTrue("\"displayName\":\"Ayu Lestari\"" in patches.single(), patches.toString())
        assertEquals("Ayu Lestari", session.user?.displayName)
    }

    @Test
    fun anEmptyOrUnchangedNameSendsNothing() = runTest {
        val vm = vm(signedIn())
        vm.saveName("   ")
        assertEquals(ErrorCode.VALIDATION_FAILED, vm.name.value.problem)
        vm.saveName("Ayu")
        assertTrue(vm.name.value.saved)
        assertTrue(patches.isEmpty())
    }

    @Test
    fun aFailedSaveKeepsTheOldNameAndSaysSo() = runTest {
        offline = true
        val session = signedIn()
        val vm = vm(session)
        vm.saveName("Ayu L")
        eventually { !vm.name.value.busy && vm.name.value.problem != null }
        assertEquals("Ayu", session.user?.displayName)
    }
}
