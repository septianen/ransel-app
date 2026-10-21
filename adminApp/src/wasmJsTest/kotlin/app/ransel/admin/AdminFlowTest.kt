package app.ransel.admin

import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.AdminSession
import app.ransel.admin.data.AdminState
import app.ransel.admin.data.RefreshTokenStore
import app.ransel.admin.data.createHttpClient
import app.ransel.admin.feature.users.UserDetailPresenter
import app.ransel.shared.model.CreditDirection
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminFlowTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private class MemoryStore(var token: String? = null) : RefreshTokenStore {
        override fun read() = token
        override fun write(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        AdminApi(createHttpClient(MockEngine { handler(it) }), origin = "http://api.test")

    private val session = """{"data":{"accessToken":"a1","accessTokenExpiresAt":"2026-10-21T10:00:00Z","refreshToken":"r2",""" +
        """"user":{"id":"u-admin","email":"owner@ransel.app","displayName":"Septian"}}}"""
    private val me = """{"data":{"id":"u-admin","email":"owner@ransel.app","displayName":"Septian","environment":"staging","passwordResetEmailEnabled":false}}"""

    private fun MockRequestHandleScope.error(status: HttpStatusCode, code: String, details: String = "null") =
        respond("""{"error":{"code":"$code","message":"x","details":$details}}""", status, json)

    @Test
    fun adminSignsInAndSeesTheEnvironment() = runTest {
        val store = MemoryStore()
        val session = AdminSession(
            api { request ->
                when {
                    request.url.encodedPath.endsWith("/auth/login") -> respond(session, HttpStatusCode.OK, json)
                    request.url.encodedPath.endsWith("/admin/me") -> {
                        assertEquals("Bearer a1", request.headers[HttpHeaders.Authorization])
                        respond(me, HttpStatusCode.OK, json)
                    }
                    else -> error(HttpStatusCode.NotFound, "NOT_FOUND")
                }
            },
            store,
        )
        assertNull(session.signIn("owner@ransel.app", "secret"))
        val state = assertIs<AdminState.Ready>(session.state.value)
        assertEquals("staging", state.me.environment)
        assertEquals("r2", store.token)
    }

    @Test
    fun aTravellerAccountGetsNotAdmin() = runTest {
        val session = AdminSession(
            api { request ->
                if (request.url.encodedPath.endsWith("/auth/login")) respond(session, HttpStatusCode.OK, json)
                else error(HttpStatusCode.Forbidden, "FORBIDDEN")
            },
            MemoryStore(),
        )
        session.signIn("traveller@gmail.com", "secret")
        assertEquals(AdminState.NotAdmin("owner@ransel.app"), session.state.value)
    }

    @Test
    fun aStoredRefreshTokenIsRenewedOnTheFirstCall() = runTest {
        val store = MemoryStore("r1")
        var refreshes = 0
        val session = AdminSession(
            api { request ->
                when {
                    request.url.encodedPath.endsWith("/auth/refresh") -> { refreshes++; respond(session, HttpStatusCode.OK, json) }
                    request.headers[HttpHeaders.Authorization] == "Bearer a1" -> respond(me, HttpStatusCode.OK, json)
                    else -> error(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
                }
            },
            store,
        )
        session.restore()
        assertIs<AdminState.Ready>(session.state.value)
        assertEquals(1, refreshes)
        assertEquals("r2", store.token)
    }

    @Test
    fun aDeadRefreshTokenSignsOut() = runTest {
        val store = MemoryStore("r1")
        val session = AdminSession(api { error(HttpStatusCode.Unauthorized, "TOKEN_EXPIRED") }, store)
        session.restore()
        assertEquals(AdminState.SignedOut, session.state.value)
        assertNull(store.token)
    }

    @Test
    fun adjustRetriesReuseTheRequestIdAndANewOpeningMakesANewOne() = runTest {
        val sentIds = mutableListOf<String>()
        var failNext = true
        var ids = 0
        val api = api { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/credits") && request.method.value == "POST" -> {
                    sentIds += Regex("\"requestId\":\"([^\"]+)\"").find(request.body.text())!!.groupValues[1]
                    if (failNext) {
                        failNext = false
                        error(HttpStatusCode.InternalServerError, "INTERNAL_ERROR")
                    } else {
                        respond("""{"data":${credits(14)}}""", HttpStatusCode.OK, json)
                    }
                }
                path.endsWith("/credits/ledger") || path.endsWith("/admin/audit") ->
                    respond("""{"data":[],"meta":{"page":1,"pageSize":25,"total":0}}""", HttpStatusCode.OK, json)
                else -> respond(detail(balance = 4), HttpStatusCode.OK, json)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val presenter = UserDetailPresenter(api, scope, "u-1", { "req-${++ids}" }, {})
            eventually { presenter.state.value.detail != null }

            presenter.openAdjust()
            presenter.editAdjust { it.copy(amountText = "10", reason = "Kompensasi") }
            presenter.submitAdjust()
            eventually { presenter.state.value.adjust?.error != null }
            presenter.submitAdjust() // retry inside the same opening
            eventually { presenter.state.value.adjust == null }
            assertEquals(listOf("req-1", "req-1"), sentIds)
            assertEquals(14, presenter.state.value.detail?.credits?.balance)

            presenter.openAdjust()
            assertEquals("req-2", presenter.state.value.adjust?.form?.requestId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun exceedsBalanceFromTheServerKeepsTheDialogOpenWithTheNewBalance() = runTest {
        var balance = 4
        val api = api { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/credits") && request.method.value == "POST" -> {
                    balance = 1 // spent meanwhile
                    error(HttpStatusCode.UnprocessableEntity, "VALIDATION_FAILED", """{"amount":"EXCEEDS_BALANCE"}""")
                }
                path.endsWith("/credits/ledger") || path.endsWith("/admin/audit") ->
                    respond("""{"data":[],"meta":{"page":1,"pageSize":25,"total":0}}""", HttpStatusCode.OK, json)
                else -> respond(detail(balance), HttpStatusCode.OK, json)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val presenter = UserDetailPresenter(api, scope, "u-1", { "req" }, {})
            eventually { presenter.state.value.detail != null }
            presenter.openAdjust()
            presenter.editAdjust { it.copy(direction = CreditDirection.SUBTRACT, amountText = "3", reason = "Koreksi") }
            presenter.submitAdjust()
            eventually { presenter.state.value.adjust?.form?.balance == 1 }
            val adjust = assertNotNull(presenter.state.value.adjust)
            assertTrue(adjust.showsExceeds)
            assertNull(adjust.error)
        } finally {
            scope.cancel()
        }
    }

    private fun credits(balance: Int) =
        """{"balance":$balance,"dailyFreeLeft":0,"dailyFreeLimit":3,"resetsAt":"2026-10-21T17:00:00Z","costs":{"search":3,"nextBatch":1,"placeDetail":0}}"""

    private fun detail(balance: Int) = """{"data":{"user":{"id":"u-1","email":"dewi@gmail.com","displayName":"Dewi","status":"ACTIVE",""" +
        """"joinedAt":"2026-09-14T03:00:00Z","tripCount":3,"balance":$balance,"dailyFreeLeft":0},""" +
        """"credits":${credits(balance)},"expenseCount":142,"trips":[],"devices":[]}}"""

    private fun OutgoingContent.text(): String = (this as TextContent).text
}
