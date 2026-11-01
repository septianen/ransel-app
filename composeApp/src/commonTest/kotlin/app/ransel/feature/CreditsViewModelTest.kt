package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.credit.CreditsStore
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.credits.CreditsViewModel
import app.ransel.feature.credits.creditOrderMailto
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import app.ransel.stopTestMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CreditsViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun credits(balance: Int) =
        """{"balance":$balance,"dailyFreeLeft":3,"dailyFreeLimit":3,"resetsAt":"2026-10-16T17:00:00Z","costs":{"search":3,"nextBatch":1,"placeDetail":0}}"""

    private suspend fun session(): SessionManager {
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

    /** [redeem] answers `POST /credits/redeem`; everything else is a normal read. */
    private fun api(redeem: Pair<HttpStatusCode, String>) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/credits/redeem") -> respond(redeem.second, redeem.first, json)
                    path.endsWith("/credits/packages") ->
                        respond("""{"data":{"packages":[{"id":"small","credits":15,"priceMinor":10000,"currency":"IDR"}],"purchasable":false}}""", HttpStatusCode.OK, json)
                    path.endsWith("/credits/ledger") -> respond("""{"data":[]}""", HttpStatusCode.OK, json)
                    else -> respond("""{"data":${credits(30)}}""", HttpStatusCode.OK, json)
                }
            },
        ),
        origin = "http://api.test",
    )

    @Test
    fun loadsBalancePacksAndHistory() = runTest {
        val vm = CreditsViewModel(api(HttpStatusCode.OK to "{}"), CreditsStore(), session())
        eventually { vm.state.value.packages != null && vm.state.value.credits != null }
        val state = vm.state.value
        assertEquals(30, state.credits?.balance)
        assertEquals(33, state.credits?.available)
        assertEquals(false, state.packages?.purchasable)
        assertEquals(15, state.packages?.packages?.single()?.credits)
        assertEquals("ayu@contoh.id", state.accountEmail)
    }

    @Test
    fun redeemingAddsTheCreditsAndClearsTheField() = runTest {
        val store = CreditsStore()
        val vm = CreditsViewModel(
            api(HttpStatusCode.OK to """{"data":{"granted":15,"credits":${credits(45)}}}"""),
            store,
            session(),
        )
        eventually { vm.state.value.credits != null }
        vm.onCodeChange("ABCD-EFGH-JKLM")
        vm.redeem()
        eventually { vm.state.value.redeemed != null }
        assertEquals(15, vm.state.value.redeemed)
        assertEquals("", vm.state.value.code)
        assertEquals(45, store.state.value?.balance)
    }

    @Test
    fun aRejectedCodeShowsItsErrorCodeAndKeepsTheField() = runTest {
        val vm = CreditsViewModel(
            api(HttpStatusCode.NotFound to """{"error":{"code":"VOUCHER_INVALID","message":"Voucher not found"}}"""),
            CreditsStore(),
            session(),
        )
        eventually { vm.state.value.credits != null }
        vm.onCodeChange("NOPE")
        vm.redeem()
        eventually { vm.state.value.redeemError != null }
        assertEquals("VOUCHER_INVALID", vm.state.value.redeemError)
        assertEquals("NOPE", vm.state.value.code)
        assertNull(vm.state.value.redeemed)
    }

    @Test
    fun theOrderLinkEncodesSubjectAndBodyForAMailApp() {
        assertEquals(
            "mailto:developer.septianen@gmail.com?subject=Pesan%2015%20kredit&body=Halo%0D%0AEmail%3A%20a%2Bb%40contoh.id",
            creditOrderMailto(subject = "Pesan 15 kredit", body = "Halo\nEmail: a+b@contoh.id"),
        )
    }
}
