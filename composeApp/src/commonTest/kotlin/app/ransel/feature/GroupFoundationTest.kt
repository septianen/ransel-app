package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.invite.InviteLinks
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.data.sync.NoSyncStatus
import app.ransel.feature.comments.CommentTargetRef
import app.ransel.feature.comments.CommentsViewModel
import app.ransel.feature.group.Countdown
import app.ransel.feature.group.countdownFor
import app.ransel.feature.invite.JoinTripEvent
import app.ransel.feature.invite.JoinTripViewModel
import app.ransel.feature.members.MembersEvent
import app.ransel.feature.members.MembersViewModel
import app.ransel.feature.members.NameProblem
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.CommentTarget
import app.ransel.shared.model.Role
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val TRIP = "t1"
private const val ME = "u1"
private val ME_MEMBER = memberIdFor(TRIP, ME)

private val trip = TripDto(
    id = TRIP, ownerId = "u9", title = "Bali 4 hari", destination = "Bali",
    startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
    createdAt = Instant.parse("2026-10-01T00:00:00Z"), updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
)

class CountdownTest {
    @Test
    fun followsTheTripDates() {
        assertEquals(Countdown.Upcoming(18), countdownFor(trip, LocalDate(2026, 11, 2)))
        assertEquals(Countdown.Upcoming(1), countdownFor(trip, LocalDate(2026, 11, 19)))
        assertEquals(Countdown.Ongoing(day = 1, totalDays = 4), countdownFor(trip, LocalDate(2026, 11, 20)))
        assertEquals(Countdown.Ongoing(day = 2, totalDays = 4), countdownFor(trip, LocalDate(2026, 11, 21)))
        assertEquals(Countdown.Ongoing(day = 4, totalDays = 4), countdownFor(trip, LocalDate(2026, 11, 23)))
        assertEquals(Countdown.Finished, countdownFor(trip, LocalDate(2026, 11, 24)))
    }
}

/** A signed-in session without a real store. */
private suspend fun signedIn(): SessionManager {
    val store = object : TokenStore {
        var saved: StoredSession? = null
        override suspend fun read() = saved
        override suspend fun write(session: StoredSession) { saved = session }
        override suspend fun clear() { saved = null }
    }
    val manager = SessionManager(store) { error("not used") }
    manager.start(
        AuthSessionDto("access", Instant.parse("2099-01-01T00:00:00Z"), "refresh", MeDto(ME, "ayu@contoh.id", "Ayu")),
    )
    return manager
}

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private val membersJson = """{"data":[
    {"tripId":"$TRIP","memberId":"m-owner","userId":"u9","displayName":"Septian","role":"OWNER","joinedAt":"2026-10-01T00:00:00Z"},
    {"tripId":"$TRIP","memberId":"$ME_MEMBER","userId":"$ME","displayName":"Ayu","role":"EDITOR","joinedAt":"2026-10-02T00:00:00Z"},
    {"tripId":"$TRIP","memberId":"m-dinda","userId":"u2","displayName":"Dinda","role":"EDITOR","joinedAt":"2026-10-03T00:00:00Z"},
    {"tripId":"$TRIP","memberId":"m-rina","displayName":"Rina","role":"VIEWER","joinedAt":"2026-10-04T00:00:00Z"}]}"""

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(10) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val log = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    private fun api(sendStatus: HttpStatusCode = HttpStatusCode.Created) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                val body = request.body.toByteArray().decodeToString()
                log.update { it + (request.method.value + " " + path to body) }
                when {
                    path.endsWith("/members") -> respond(membersJson, HttpStatusCode.OK, json)
                    request.method == HttpMethod.Get && path.endsWith("/comments") -> respond(
                        """{"data":[{"id":"c1","tripId":"$TRIP","targetKind":"ITINERARY_ITEM","targetId":"i1","authorMemberId":"m-dinda","authorName":"Dinda","body":"Berangkat jam 8?","createdAt":"2026-10-25T01:00:00Z"}]}""",
                        HttpStatusCode.OK, json,
                    )
                    request.method == HttpMethod.Post && path.endsWith("/comments") ->
                        if (sendStatus == HttpStatusCode.Created) respond(
                            """{"data":{"id":"c2","tripId":"$TRIP","targetKind":"ITINERARY_ITEM","targetId":"i1","authorMemberId":"$ME_MEMBER","authorName":"Ayu","body":"ok","createdAt":"2026-10-25T01:05:00Z"}}""",
                            HttpStatusCode.Created, json,
                        ) else respond("""{"error":{"code":"NOT_FOUND","message":"x"}}""", sendStatus, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun vm(api: RanselApi) =
        CommentsViewModel(CommentTargetRef(TRIP, CommentTarget.ITINERARY_ITEM, "i1"), api, signedIn())

    @Test
    fun loadsCommentsAndOffersAccountsToMention() = runTest {
        val vm = vm(api())
        eventually { !vm.state.value.loading }
        assertEquals(listOf("Berangkat jam 8?"), vm.state.value.comments.map { it.body })
        // not me, and not the person added by name (Rina has no inbox)
        assertEquals(listOf("Septian", "Dinda"), vm.state.value.mentionable.map { it.displayName })
    }

    @Test
    fun mentioningAddsTheNameToTheDraftAndSendingResolvesTheIds() = runTest {
        val vm = vm(api())
        eventually { !vm.state.value.loading }
        vm.onDraft("Siap")
        vm.mention(vm.state.value.mentionable.first { it.displayName == "Dinda" })
        assertEquals("Siap @Dinda ", vm.state.value.draft)

        vm.send()
        eventually { vm.state.value.comments.size == 2 }
        val post = log.value.single { it.first == "POST /api/v1/trips/$TRIP/comments" }.second
        assertTrue("\"mentions\":[\"m-dinda\"]" in post, post)
        assertTrue("\"targetKind\":\"ITINERARY_ITEM\"" in post, post)
        assertEquals("", vm.state.value.draft)
        assertEquals(false, vm.state.value.sending)
    }

    @Test
    fun aBlankDraftSendsNothing() = runTest {
        val vm = vm(api())
        eventually { !vm.state.value.loading }
        vm.onDraft("   ")
        vm.send()
        assertTrue(log.value.none { it.first.startsWith("POST") })
    }

    @Test
    fun aFailedSendKeepsTheDraftAndSaysSo() = runTest {
        val vm = vm(api(sendStatus = HttpStatusCode.NotFound))
        eventually { !vm.state.value.loading }
        vm.onDraft("Halo")
        vm.send()
        eventually { vm.state.value.failed }
        assertEquals("Halo", vm.state.value.draft)
        assertEquals(1, vm.state.value.comments.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MembersViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val log = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                log.update { it + (request.method.value + " " + path to request.body.toByteArray().decodeToString()) }
                when {
                    request.method == HttpMethod.Get && path.endsWith("/members") -> respond(membersJson, HttpStatusCode.OK, json)
                    request.method == HttpMethod.Get && path.endsWith("/invites") -> respond("""{"data":[]}""", HttpStatusCode.OK, json)
                    request.method == HttpMethod.Post && path.endsWith("/invites") -> respond(
                        """{"data":{"code":"BALI-7K2Q","tripId":"$TRIP","role":"EDITOR","usedCount":0,"expiresAt":"2099-01-01T00:00:00Z","url":"https://ransel.mietek.tech/j/BALI-7K2Q","createdAt":"2026-10-25T00:00:00Z"}}""",
                        HttpStatusCode.Created, json,
                    )
                    request.method == HttpMethod.Post && path.endsWith("/members") -> respond(
                        """{"data":{"tripId":"$TRIP","memberId":"m-new","displayName":"Rina","role":"VIEWER","joinedAt":"2026-10-25T00:00:00Z"}}""",
                        HttpStatusCode.Created, json,
                    )
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun vm(): MembersViewModel =
        MembersViewModel(TRIP, api(), signedIn(), InMemoryTripRepository(InMemoryStore()))

    private val posts get() = log.value.filter { it.first == "POST /api/v1/trips/$TRIP/members" }

    @Test
    fun aNameIsRequiredAndTheNumberMustLookRight() = runTest {
        val vm = vm()
        eventually { !vm.state.value.loading }
        vm.openAddForm()
        vm.submitAdd()
        assertEquals(NameProblem.REQUIRED, vm.state.value.addForm?.nameProblem)

        vm.onAddName("Rina")
        vm.onAddPhone("123")
        vm.submitAdd()
        assertEquals(true, vm.state.value.addForm?.phoneInvalid)
        assertTrue(posts.isEmpty())
    }

    @Test
    fun addingAPersonByNameSendsItAndReloadsTheList() = runTest {
        val vm = vm()
        eventually { !vm.state.value.loading }
        vm.openAddForm()
        vm.onAddName("  Rina ")
        vm.onAddPhone("0812-3456-7890")
        vm.submitAdd()
        eventually { posts.size == 1 && vm.state.value.addForm == null }
        val body = posts.single().second
        assertTrue("\"displayName\":\"Rina\"" in body, body)
        assertTrue("\"phone\":\"0812-3456-7890\"" in body, body)
        assertEquals(4, vm.state.value.members.size)
    }

    @Test
    fun sendLinkMakesAnInviteForThatPerson() = runTest {
        val vm = vm()
        eventually { vm.state.value.members.isNotEmpty() }
        val rina = vm.state.value.members.first { it.isGuest }
        vm.sendLink(rina)
        val event = withContext(Dispatchers.Default) { withTimeout(5_000) { vm.eventFlow.first() } }
        assertTrue(event is MembersEvent.SendLink)
        assertEquals("https://ransel.mietek.tech/j/BALI-7K2Q", event.url)
        assertEquals("Rina", event.member.displayName)
    }

    @Test
    fun aPersonWithoutAnAccountKeepsTheViewerRole() = runTest {
        val vm = vm()
        eventually { vm.state.value.members.isNotEmpty() }
        vm.changeRole(vm.state.value.members.first { it.isGuest }, Role.EDITOR)
        assertTrue(log.value.none { it.first.startsWith("PATCH") })
    }

    @Test
    fun undoingAClaimCallsTheOwnerEndpoint() = runTest {
        val vm = vm()
        eventually { vm.state.value.members.isNotEmpty() }
        vm.showClaimUndo("m-rina", "Rina", "Dinda")
        assertNotNull(vm.state.value.claimBanner)
        vm.undoClaim()
        eventually { vm.state.value.claimBanner == null }
        assertTrue(log.value.any { it.first == "DELETE /api/v1/trips/$TRIP/members/m-rina/claim" })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class JoinTripClaimTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val log = MutableStateFlow<List<String>>(emptyList())

    private fun api(guests: Boolean) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                log.update { it + (request.method.value + " " + path) }
                when {
                    path.endsWith("/invites/BALI-7K2Q") -> respond(
                        """{"data":{"code":"BALI-7K2Q","tripId":"$TRIP","tripTitle":"Bali 4 hari","destination":"Bali","startDate":"2026-11-20","endDate":"2026-11-23","ownerName":"Septian","memberCount":3,"role":"EDITOR"}}""",
                        HttpStatusCode.OK, json,
                    )
                    path.endsWith("/accept") -> respond(
                        """{"data":{"id":"$TRIP","ownerId":"u9","title":"Bali 4 hari","destination":"Bali","startDate":"2026-11-20","endDate":"2026-11-23","baseCurrency":"IDR","myRole":"EDITOR","createdAt":"2026-10-01T00:00:00Z","updatedAt":"2026-10-01T00:00:00Z"}}""",
                        HttpStatusCode.OK, json,
                    )
                    path.endsWith("/members") && request.method == HttpMethod.Get ->
                        respond(if (guests) membersJson else """{"data":[]}""", HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun vm(guests: Boolean): JoinTripViewModel {
        val store = InMemoryStore().also { it.trips.value = listOf(trip) }
        return JoinTripViewModel("BALI-7K2Q", api(guests), signedIn(), InviteLinks(), InMemoryTripRepository(store), NoSyncStatus)
    }

    @Test
    fun joiningATripWithNamedPeopleAsksWhichOneYouAre() = runTest {
        val vm = vm(guests = true)
        eventually { vm.state.value.preview != null }
        vm.join()
        eventually { vm.state.value.claimStep != null }
        assertEquals(listOf("Rina"), vm.state.value.claimStep!!.candidates.map { it.displayName })

        vm.claim(vm.state.value.claimStep!!.candidates.single())
        val joined = withContext(Dispatchers.Default) { withTimeout(5_000) { vm.eventFlow.first() } }
        assertEquals(JoinTripEvent.Joined(TRIP), joined)
        assertTrue("POST /api/v1/trips/$TRIP/members/m-rina/claim" in log.value)
    }

    @Test
    fun skippingTheQuestionJustOpensTheTrip() = runTest {
        val vm = vm(guests = true)
        eventually { vm.state.value.preview != null }
        vm.join()
        eventually { vm.state.value.claimStep != null }
        vm.skipClaim()
        assertEquals(JoinTripEvent.Joined(TRIP), withContext(Dispatchers.Default) { withTimeout(5_000) { vm.eventFlow.first() } })
        assertTrue(log.value.none { it.endsWith("/claim") })
    }

    @Test
    fun noNamedPeopleMeansNoQuestion() = runTest {
        val vm = vm(guests = false)
        eventually { vm.state.value.preview != null }
        vm.join()
        assertEquals(JoinTripEvent.Joined(TRIP), withContext(Dispatchers.Default) { withTimeout(5_000) { vm.eventFlow.first() } })
        assertNull(vm.state.value.claimStep)
    }
}
