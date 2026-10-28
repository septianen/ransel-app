package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.members.MembersDialog
import app.ransel.feature.members.MembersEvent
import app.ransel.feature.members.MembersViewModel
import app.ransel.feature.members.newOwnerCandidates
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val TRIP = "t1"
private const val ME = "u1"

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private fun member(id: String, userId: String?, name: String, role: Role, day: Int) =
    """{"tripId":"$TRIP","memberId":"$id",${userId?.let { "\"userId\":\"$it\"," } ?: ""}"displayName":"$name","role":"$role","joinedAt":"2026-10-0${day}T00:00:00Z"}"""

/** Me (Ayu) as the owner, a viewer and an editor with an account, and Rina added by name. */
private val withAccounts = listOf(
    member("m-me", ME, "Ayu", Role.OWNER, 1),
    member("m-maya", "u3", "Maya", Role.VIEWER, 2),
    member("m-dinda", "u2", "Dinda", Role.EDITOR, 3),
    member("m-rina", null, "Rina", Role.VIEWER, 4),
)

/** Me as the owner and only people added by name. */
private val onlyGuests = listOf(
    member("m-me", ME, "Ayu", Role.OWNER, 1),
    member("m-rina", null, "Rina", Role.VIEWER, 4),
)

private suspend fun signedIn(): SessionManager {
    val store = object : TokenStore {
        var saved: StoredSession? = null
        override suspend fun read() = saved
        override suspend fun write(session: StoredSession) { saved = session }
        override suspend fun clear() { saved = null }
    }
    val manager = SessionManager(store) { error("not used") }
    manager.start(AuthSessionDto("access", Instant.parse("2099-01-01T00:00:00Z"), "refresh", MeDto(ME, "ayu@contoh.id", "Ayu")))
    return manager
}

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(10) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerLeaveTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val log = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    private fun api(
        members: List<String> = withAccounts,
        transferStatus: HttpStatusCode = HttpStatusCode.NoContent,
        leaveStatus: HttpStatusCode = HttpStatusCode.NoContent,
        leaveCode: String = ErrorCode.NETWORK_UNAVAILABLE,
    ) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                log.update { it + (request.method.value + " " + path to request.body.toByteArray().decodeToString()) }
                fun fail(status: HttpStatusCode, code: String) = respond("""{"error":{"code":"$code","message":"x"}}""", status, json)
                when {
                    request.method == HttpMethod.Get && path.endsWith("/members") ->
                        respond("""{"data":[${members.joinToString(",")}]}""", HttpStatusCode.OK, json)
                    request.method == HttpMethod.Get && path.endsWith("/invites") -> respond("""{"data":[]}""", HttpStatusCode.OK, json)
                    path.endsWith("/transfer-ownership") ->
                        if (transferStatus == HttpStatusCode.NoContent) respond("", transferStatus) else fail(transferStatus, "FORBIDDEN")
                    path.endsWith("/leave") ->
                        if (leaveStatus == HttpStatusCode.NoContent) respond("", leaveStatus) else fail(leaveStatus, leaveCode)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun vm(api: RanselApi) = MembersViewModel(TRIP, api, signedIn(), InMemoryTripRepository(InMemoryStore())).also { vm ->
        eventually { vm.state.value.members.isNotEmpty() }
    }

    private val calls get() = log.value.map { it.first }.filter { it.startsWith("POST") }

    private suspend fun MembersViewModel.nextEvent() = withContext(Dispatchers.Default) { withTimeout(5_000) { eventFlow.first() } }

    @Test
    fun onlyOtherMembersWithAnAccountCanTakeOver() {
        val at = Instant.parse("2026-10-01T00:00:00Z")
        val members = listOf(
            TripMemberDto(TRIP, "m-me", ME, "Ayu", role = Role.OWNER, joinedAt = at),
            TripMemberDto(TRIP, "m-dinda", "u2", "Dinda", role = Role.EDITOR, joinedAt = at),
            TripMemberDto(TRIP, "m-rina", null, "Rina", role = Role.VIEWER, joinedAt = at),
        )
        assertEquals(listOf("Dinda"), newOwnerCandidates(members, ME).map { it.displayName })
    }

    @Test
    fun theOwnerPicksANewOwnerInsteadOfLeavingStraightAway() = runTest {
        val vm = vm(api())
        vm.askLeave()
        val dialog = assertIs<MembersDialog.PickNewOwner>(vm.state.value.dialog)
        // editors first, then viewers; never me, never a person added by name
        assertEquals(listOf("Dinda", "Maya"), dialog.candidates.map { it.displayName })
        assertEquals("u2", dialog.selected)
    }

    @Test
    fun confirmingTransfersThenLeaves() = runTest {
        val vm = vm(api())
        vm.askLeave()
        vm.pickNewOwner(vm.state.value.members.first { it.displayName == "Maya" })
        assertEquals("u3", (vm.state.value.dialog as MembersDialog.PickNewOwner).selected)
        vm.confirmDialog()
        assertEquals(MembersEvent.Left, vm.nextEvent())
        assertEquals(
            listOf("POST /api/v1/trips/$TRIP/transfer-ownership", "POST /api/v1/trips/$TRIP/leave"),
            calls,
        )
        val body = log.value.single { it.first.endsWith("/transfer-ownership") }.second
        assertTrue("\"u3\"" in body, body)
        assertEquals(null, vm.state.value.dialog)
    }

    @Test
    fun aPersonAddedByNameCannotBePicked() = runTest {
        val vm = vm(api())
        vm.askLeave()
        vm.pickNewOwner(vm.state.value.members.first { it.isGuest })
        assertEquals("u2", (vm.state.value.dialog as MembersDialog.PickNewOwner).selected)
    }

    @Test
    fun nobodyWithAnAccountMeansNoTransferButDeleting() = runTest {
        val vm = vm(api(members = onlyGuests))
        vm.askLeave()
        assertEquals(MembersDialog.NoNewOwner, vm.state.value.dialog)
        vm.confirmDialog()
        assertEquals(MembersEvent.OpenDeleteTrip, vm.nextEvent())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun aFailedTransferDoesNotLeave() = runTest {
        val vm = vm(api(transferStatus = HttpStatusCode.Forbidden))
        vm.askLeave()
        vm.confirmDialog()
        assertEquals(MembersEvent.Problem("FORBIDDEN"), vm.nextEvent())
        assertEquals(listOf("POST /api/v1/trips/$TRIP/transfer-ownership"), calls)
        eventually { !vm.state.value.busy }
        assertFalse(vm.state.value.leaving)
        assertEquals(null, vm.state.value.dialog)
    }

    @Test
    fun aFailedLeaveAfterTheTransferReportsTheProblem() = runTest {
        val vm = vm(api(leaveStatus = HttpStatusCode.ServiceUnavailable))
        vm.askLeave()
        vm.confirmDialog()
        assertEquals(MembersEvent.Problem(ErrorCode.NETWORK_UNAVAILABLE), vm.nextEvent())
        assertEquals(2, calls.size)
        eventually { !vm.state.value.busy }
    }

    @Test
    fun ownerCannotLeaveFromAStaleListOpensThePicker() = runTest {
        // The list said EDITOR, but the server says we own the trip now.
        val stale = listOf(member("m-me", ME, "Ayu", Role.EDITOR, 1), member("m-dinda", "u2", "Dinda", Role.OWNER, 3))
        val members = MutableStateFlow(stale)
        val api = RanselApi(
            createApiHttpClient(
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/members") -> respond("""{"data":[${members.value.joinToString(",")}]}""", HttpStatusCode.OK, json)
                        path.endsWith("/leave") -> respond(
                            """{"error":{"code":"${ErrorCode.OWNER_CANNOT_LEAVE}","message":"x"}}""", HttpStatusCode.Conflict, json,
                        )
                        else -> respond("""{"data":[]}""", HttpStatusCode.OK, json)
                    }
                },
            ),
            origin = "http://api.test",
        )
        val vm = vm(api)
        vm.askLeave()
        assertEquals(MembersDialog.Leave, vm.state.value.dialog)
        members.value = listOf(member("m-me", ME, "Ayu", Role.OWNER, 1), member("m-dinda", "u2", "Dinda", Role.EDITOR, 3))
        vm.confirmDialog()
        eventually { vm.state.value.dialog is MembersDialog.PickNewOwner }
        assertEquals(listOf("Dinda"), (vm.state.value.dialog as MembersDialog.PickNewOwner).candidates.map { it.displayName })
    }
}
