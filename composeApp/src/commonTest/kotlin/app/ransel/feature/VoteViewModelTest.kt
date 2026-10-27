package app.ransel.feature

import app.ransel.data.credit.CreditsStore
import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.data.voting.VotingStore
import app.ransel.feature.voting.VoteMode
import app.ransel.feature.voting.VoteViewModel
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.Vote
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class VoteViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private suspend fun eventually(check: suspend () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    // The engine answers on its own thread while the test reads: a StateFlow keeps that safe on every platform.
    private val log = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val requests get() = log.value
    private val now = Clock.System.now()

    private fun place(id: String) =
        """{"category":"ATTRACTION","item":{"placeId":"$id","name":"Place $id","reason":"because","suggestedDurationMin":90,"lat":-8.5,"lng":115.2}}"""

    /** [owner] adds the owner-only tally and voters; [closesIn] in hours (negative = closed). */
    private fun round(ids: List<String>, myVotes: Map<String, String> = emptyMap(), owner: Boolean = false, closesIn: Int = 20, decided: Boolean = false): String {
        val votes = myVotes.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
        val ownerPart = if (owner) {
            ""","tally":[${ids.mapIndexed { i, id -> """{"placeId":"$id","likes":${3 - i},"skips":$i}""" }.joinToString(",")}],""" +
                """"voters":[{"userId":"u1","displayName":"Rina","votedCount":${ids.size}},{"userId":"u2","displayName":"Dina","votedCount":0}]"""
        } else ""
        val decidedPart = if (decided) ""","decidedAt":"${now - 1.hours}","chosenPlaceIds":["${ids.first()}"]""" else ""
        return """{"data":{"id":"r1","tripId":"t1","createdByUserId":"u1","createdByName":"Rina","createdAt":"${now - 2.hours}",
            "closesAt":"${now + closesIn.hours}","expiresAt":"${now + 160.hours}","places":[${ids.joinToString(",") { place(it) }}],
            "myVotes":{$votes},"memberCount":3$ownerPart$decidedPart}}"""
    }

    private fun api(roundJson: () -> String) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                val entry = request.method.value + " " + path to request.body.toByteArray().decodeToString()
                log.update { it + entry }
                when {
                    path.endsWith("/rounds/r1") -> respond(roundJson(), HttpStatusCode.OK, json)
                    path.endsWith("/rounds") -> respond("""{"data":[]}""", HttpStatusCode.OK, json)
                    path.endsWith("/members") -> respond(
                        """{"data":[{"tripId":"t1","memberId":"m1","userId":"u1","displayName":"Rina","role":"OWNER","joinedAt":"$now"}]}""",
                        HttpStatusCode.OK, json,
                    )
                    path.endsWith("/notifications") -> respond("""{"data":{"items":[],"unreadCount":0}}""", HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private fun vm(
        api: RanselApi,
        showResult: Boolean = false,
        itinerary: InMemoryItineraryRepository = InMemoryItineraryRepository(InMemoryStore()),
        explore: ExploreSessionStore = ExploreSessionStore(InMemoryPreferencesRepository()),
    ) = VoteViewModel(
        "t1", "r1", showResult, api, itinerary, explore, VotingStore(), NotificationsStore(), CreditsStore(),
        InMemoryTripRepository(InMemoryStore()),
    )

    @Test
    fun aMemberVotesEveryCardThenWaits() = runTest {
        var votes = mapOf<String, String>()
        val vm = vm(api { round(listOf("a", "b"), votes) })
        eventually { vm.state.value.mode == VoteMode.VOTING }
        assertEquals(0, vm.state.value.cursor)
        eventually { vm.state.value.ownerName == "Rina" }

        vm.like()
        vm.skip()
        votes = mapOf("a" to "LIKE", "b" to "SKIP")
        eventually { requests.count { it.first.startsWith("PUT") } == 2 }
        assertTrue(requests.any { it.first == "PUT /api/v1/trips/t1/rounds/r1/votes/a" && "LIKE" in it.second })
        assertEquals(VoteMode.WAITING, vm.state.value.mode)
        assertEquals(1, vm.state.value.likedCount)
    }

    @Test
    fun undoRemovesTheVoteAndGoesBackToThatCard() = runTest {
        val vm = vm(api { round(listOf("a", "b")) })
        eventually { vm.state.value.mode == VoteMode.VOTING }
        vm.like()
        vm.undo()
        eventually { requests.any { it.first == "DELETE /api/v1/trips/t1/rounds/r1/votes/a" } }
        assertEquals(0, vm.state.value.cursor)
        assertNull(vm.state.value.votes["a"])
    }

    @Test
    fun aVotedMemberResumesAtTheFirstCardWithoutAVote() = runTest {
        val vm = vm(api { round(listOf("a", "b", "c"), mapOf("a" to "LIKE")) })
        eventually { vm.state.value.mode == VoteMode.VOTING }
        assertEquals(1, vm.state.value.cursor)
    }

    @Test
    fun aClosedRoundShowsTheClosedStateToMembers() = runTest {
        val vm = vm(api { round(listOf("a"), closesIn = -2) })
        eventually { !vm.state.value.loading }
        assertEquals(VoteMode.CLOSED, vm.state.value.mode)
    }

    @Test
    fun theOwnerDecidesTheItemsGoToUnscheduledThenTheRoundCloses() = runTest {
        val itinerary = InMemoryItineraryRepository(InMemoryStore())
        val vm = vm(api { round(listOf("a", "b", "c"), owner = true) }, showResult = true, itinerary = itinerary)
        eventually { vm.state.value.mode == VoteMode.RESULT }
        // More than half of the 3 members liked a (3) and b (2): they start ticked.
        assertEquals(setOf("a", "b"), vm.state.value.selected)
        vm.toggle("b")
        vm.decide()
        eventually { requests.any { it.first == "POST /api/v1/trips/t1/rounds/r1/decide" } }
        assertTrue(requests.single { it.first.endsWith("/decide") }.second.contains("\"placeIds\":[\"a\"]"))
        val items = itinerary.observe("t1").first()
        assertEquals(listOf("a"), items.map { it.placeId })
        assertEquals(ItemSource.AI, items.single().source)
        assertNull(items.single().dayDate)
    }

    @Test
    fun theRequesterAsksForTheNextBatchOfTheSameRound() = runTest {
        val explore = ExploreSessionStore(InMemoryPreferencesRepository())
        val form = ExploreForm("t1", "Bali", "Bali", LocalDate(2026, 11, 20), LocalDate(2026, 11, 23))
        explore.save(ExploreSession(form, roundId = "r1"))
        val vm = vm(api { round(listOf("a", "b")) }, explore = explore)
        eventually { requests.any { it.first == "POST /api/v1/recommendations" } }
        val body = requests.first { it.first == "POST /api/v1/recommendations" }.second
        assertTrue("\"roundId\":\"r1\"" in body && "\"newSearch\":false" in body)
        assertEquals(VoteMode.VOTING, vm.state.value.mode)
        assertEquals(Vote.LIKE, Vote.valueOf("LIKE"))
    }
}
