package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryPackingRepository
import app.ransel.data.fake.InMemoryPersonalItemRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.packing.GroupPackingEvent
import app.ransel.feature.packing.GroupPackingViewModel
import app.ransel.feature.packing.PackingViewModel
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.model.PackingCategory
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
import kotlinx.coroutines.launch
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

private const val TRIP = "t1"
private const val ME = "u1"
private val ME_MEMBER = memberIdFor(TRIP, ME)
private val json = headersOf(HttpHeaders.ContentType, "application/json")
private val stamp = Instant.parse("2026-10-26T00:00:00Z")

private suspend fun signedIn(): SessionManager {
    val store = object : TokenStore {
        var saved: StoredSession? = null
        override suspend fun read() = saved
        override suspend fun write(session: StoredSession) { saved = session }
        override suspend fun clear() { saved = null }
    }
    return SessionManager(store) { error("not used") }.also {
        it.start(AuthSessionDto("access", Instant.parse("2099-01-01T00:00:00Z"), "refresh", MeDto(ME, "ayu@contoh.id", "Ayu")))
    }
}

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(10) }
}

private val membersJson = """{"data":[
    {"tripId":"$TRIP","memberId":"$ME_MEMBER","userId":"$ME","displayName":"Ayu","role":"OWNER","joinedAt":"2026-10-01T00:00:00Z"},
    {"tripId":"$TRIP","memberId":"m-dinda","userId":"u2","displayName":"Dinda","role":"VIEWER","joinedAt":"2026-10-02T00:00:00Z"}]}"""

private fun listJson(version: Int, sent: Boolean) = """{"data":{"tripId":"$TRIP","name":"Gunung","version":$version,
    "items":[{"id":"l1","category":"CLOTHING","name":"Jaket","quantity":1,"required":true,"sortOrder":0}],
    "sentToAll":true,"recipientMemberIds":[],"sentAt":${if (sent) "\"2026-10-26T01:00:00Z\"" else "null"},
    "createdByMemberId":"$ME_MEMBER","updatedAt":"2026-10-26T01:00:00Z"}}"""

@OptIn(ExperimentalCoroutinesApi::class)
class GroupPackingTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val requests = MutableStateFlow<List<String>>(emptyList())
    private var hasList = false
    private var sent = false
    private var stale = false

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                val body = request.body.toByteArray().decodeToString()
                requests.update { it + "${request.method.value} $path $body" }
                when {
                    path.endsWith("/members") -> respond(membersJson, HttpStatusCode.OK, json)
                    path.endsWith("/member-list/progress") ->
                        respond("""{"data":[{"memberId":"m-dinda","requiredDone":0,"requiredTotal":1,"done":0,"total":1}]}""", HttpStatusCode.OK, json)
                    path.endsWith("/member-list/send") -> { sent = true; respond(listJson(1, true), HttpStatusCode.OK, json) }
                    path.endsWith("/member-list/remind") -> respond("", HttpStatusCode.NoContent)
                    path.endsWith("/member-list") && request.method == HttpMethod.Put -> when {
                        stale -> respond("""{"error":{"code":"LIST_STALE","message":"stale"}}""", HttpStatusCode.Conflict, json)
                        else -> { hasList = true; respond(listJson(1, sent), HttpStatusCode.OK, json) }
                    }
                    path.endsWith("/member-list") ->
                        if (hasList) respond(listJson(1, sent), HttpStatusCode.OK, json)
                        else respond("""{"error":{"code":"NOT_FOUND","message":"none"}}""", HttpStatusCode.NotFound, json)
                    else -> respond("""{"data":[]}""", HttpStatusCode.OK, json)
                }
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun viewModel(store: InMemoryStore = InMemoryStore()) = GroupPackingViewModel(
        TRIP,
        InMemoryPersonalItemRepository(store) { memberIdFor(it, ME) },
        InMemoryPackingRepository(store),
        api(),
        signedIn(),
    )

    @Test
    fun ownLinesAreAddedAndFromListLinesOnlyTick() = runTest {
        val store = InMemoryStore()
        store.personal.value = listOf(
            PersonalItemDto("p1", TRIP, ME_MEMBER, PackingCategory.CLOTHING, "Jaket", sourceItemId = "l1", required = true, createdAt = stamp, updatedAt = stamp),
        )
        val vm = viewModel(store)
        eventually { vm.state.value.members.size == 2 }
        vm.add("Kamera", PackingCategory.ELECTRONICS, 1)
        eventually { vm.state.value.own.size == 1 }
        assertTrue(vm.state.value.receivedList)
        assertEquals(1, vm.state.value.requiredTotal)

        val fromList = vm.state.value.fromList.single().items.single()
        vm.edit(fromList, "Jas", PackingCategory.OTHER, 3)
        vm.toggle(fromList)
        eventually { vm.state.value.requiredDone == 1 }
        val after = vm.state.value.fromList.single().items.single()
        assertEquals("Jaket", after.name, "a line from the list keeps the owner's wording")
        vm.delete(after)
        assertEquals(2, vm.state.value.personal.size, "and cannot be deleted")
    }

    @Test
    fun theOwnerSavesSendsAndReminds() = runTest {
        val vm = viewModel()
        eventually { vm.state.value.members.size == 2 }
        assertNull(vm.state.value.list)

        var savedThen: Boolean? = null
        vm.save("Gunung", vm.linesFromMine()) { savedThen = it.sentAt != null }
        eventually { savedThen != null }
        assertEquals(false, savedThen, "a new list goes on to sending")
        assertEquals(GroupPackingEvent.Saved, vm.eventFlow.first())
        assertTrue(requests.value.any { it.startsWith("PUT") && "baseVersion" !in it }, "a first save has no version")

        vm.send(emptyList(), saveAsTemplate = true)
        assertEquals(GroupPackingEvent.Sent, vm.eventFlow.first())
        assertTrue(requests.value.any { it.contains("/member-list/send") && "\"saveAsTemplate\":true" in it })
        eventually { vm.state.value.progress.size == 1 }

        vm.remindNotReady()
        assertEquals(GroupPackingEvent.Reminded, vm.eventFlow.first())
        assertTrue(requests.value.any { it.contains("/member-list/remind") && "m-dinda" in it })
    }

    @Test
    fun aStaleSaveSaysSoAndReloads() = runTest {
        hasList = true
        val vm = viewModel()
        eventually { vm.state.value.list != null }
        stale = true
        vm.save("Gunung", vm.state.value.list!!.items)
        assertEquals(GroupPackingEvent.Problem(ErrorCode.LIST_STALE), vm.eventFlow.first())
        assertTrue(requests.value.any { it.startsWith("PUT") && "\"baseVersion\":1" in it })
    }

    @Test
    fun bringingASharedItemAndGivingItBack() = runTest {
        val store = InMemoryStore()
        val packing = InMemoryPackingRepository(store)
        val vm = PackingViewModel(TRIP, packing)
        backgroundScope.launch { vm.state.collect {} }
        val group = viewModel(store)
        group.addShared("Kompor", PackingCategory.OTHER, 1, assigneeMemberId = null, order = 1.0)
        eventually { vm.state.value.total == 1 }
        val stove = vm.state.value.groups.single().items.single()
        vm.bring(stove, ME_MEMBER)
        eventually { vm.state.value.groups.single().items.single().assigneeMemberId == ME_MEMBER }
        vm.toggle(vm.state.value.groups.single().items.single())
        eventually { vm.state.value.packed == 1 }
        vm.giveBack(vm.state.value.groups.single().items.single())
        eventually { vm.state.value.groups.single().items.single().assigneeMemberId == null }
        assertEquals(0, vm.state.value.packed, "giving it back unticks it")

        group.addShared("Tenda", PackingCategory.OTHER, 1, assigneeMemberId = "m-dinda", order = 2.0)
        eventually { vm.state.value.total == 2 }
        assertEquals("m-dinda", store.packing.value.single { it.name == "Tenda" }.assigneeMemberId)
    }
}
