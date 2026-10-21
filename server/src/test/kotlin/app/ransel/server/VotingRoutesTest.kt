package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.InterestSuggestions
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.PhotoSigner
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlacesClient
import app.ransel.server.service.RoundService
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

/** Every search proposes three new places, so later searches are never emptied by the round exclusion. */
private class FreshLlm : LlmClient {
    override val modelName = "fake-model"
    private val calls = AtomicInteger()

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> {
        val n = calls.incrementAndGet()
        return (1..3).map { LlmCandidate(name = "Tempat $n-$it", reason = "Cocok", suggestedDurationMin = 60) }
    }

    override suspend fun suggestInterests(destination: String, month: String, language: String) =
        InterestSuggestions(emptyList(), lowMaxUsd = 30.0, midMaxUsd = 90.0)
}

/** Every name exists, all near each other. */
private class AnyPlaces : PlacesClient {
    override suspend fun findPlaceId(name: String, destination: String, focusArea: String?) = "place-$name"
    override suspend fun details(placeId: String, withPhotoCredit: Boolean) = PlaceDetails(placeId, -8.5, 115.2, "Jl. Bali")
    override suspend fun photo(placeId: String): PlacePhoto? = null
}

/** Owner, an editor and a viewer on one trip. Starts signed in as the owner. */
private class VotingGroup(val tripId: String, val owner: Session, val editor: Session, val viewer: Session)

private suspend fun Api.group(): VotingGroup {
    val owner = session()
    val tripId = UUID.randomUUID().toString()
    post("/trips", tripJson(tripId, title = "Bali 4 hari"))
    signUp(displayName = "Dinda")
    val editor = session()
    signUp(displayName = "Raka")
    val viewer = session()
    for ((who, role) in listOf(editor to "EDITOR", viewer to "VIEWER")) {
        use(owner)
        val code = post("/trips/$tripId/invites", """{"role":"$role","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        use(who)
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
    }
    use(owner)
    return VotingGroup(tripId, owner, editor, viewer)
}

private var searchCount = 0

private fun search(tripId: String, newSearch: Boolean = true, roundId: String? = null) =
    // A different note each time keeps the recommendation cache out of the way.
    """{"tripId":"$tripId","priceLevel":"MEDIUM","interests":["NATURE"],"style":"SANTAI","notes":"n${searchCount++}","newSearch":$newSearch""" +
        (roundId?.let { ""","roundId":"$it"""" } ?: "") + "}"

private suspend fun Api.startRound(tripId: String): Pair<String, List<String>> {
    val response = post("/recommendations", search(tripId))
    assertEquals(HttpStatusCode.OK, response.status)
    val data = response.data().jsonObject
    val places = data.getValue("groups").jsonArray.flatMap { g -> g.jsonObject.getValue("items").jsonArray.map { it.jsonObject.str("placeId") } }
    return data.str("roundId") to places
}

private suspend fun Api.vote(tripId: String, roundId: String, placeId: String, vote: String) =
    put("/trips/$tripId/rounds/$roundId/votes/$placeId", """{"vote":"$vote"}""")

private suspend fun Api.round(tripId: String, roundId: String) = get("/trips/$tripId/rounds/$roundId").data().jsonObject

private suspend fun Api.inbox() = get("/notifications").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }

private fun JsonObject.params() = getValue("params").jsonObject.mapValues { it.value.jsonPrimitive.content }

class VotingRoutesTest {
    private val ai get() = AiServices(FreshLlm(), AnyPlaces())

    @Test
    fun aSoloTripSearchSavesDirectlyWithoutARound() = apiTest(ai = ai) {
        val tripId = UUID.randomUUID().toString()
        post("/trips", tripJson(tripId))
        val data = post("/recommendations", search(tripId)).data().jsonObject
        assertNull(data["roundId"]?.jsonPrimitive?.content?.takeIf { it != "null" })
        assertEquals(0, get("/trips/$tripId/rounds").data().jsonArray.size)
    }

    @Test
    fun aGroupSearchMakesARoundEveryoneVotesAndTheOwnerDecides() = apiTest(ai = ai) {
        val g = group()
        use(g.editor)
        val (roundId, places) = startRound(g.tripId)
        assertEquals(3, places.size)

        // The editor asked: the owner and the viewer are asked to vote, the editor is not.
        use(g.owner)
        val request = inbox().single { it.str("type") == "VOTE_REQUESTED" }.params()
        assertEquals("OPEN", request["stage"])
        assertEquals(roundId, request["roundId"])
        assertEquals("Dinda", request["actorName"])
        assertEquals("3", request["placeCount"])
        assertEquals("OWNER", request["recipientRole"], "the owner is told about a proposal, not asked for picks")
        use(g.viewer)
        assertNull(inbox().single { it.str("type") == "VOTE_REQUESTED" }.params()["recipientRole"])
        use(g.editor)
        assertTrue(inbox().none { it.str("type") == "VOTE_REQUESTED" })

        // Votes, including the viewer's. A member does not see the tally while voting is open.
        vote(g.tripId, roundId, places[0], "LIKE")
        assertEquals(HttpStatusCode.NoContent, vote(g.tripId, roundId, places[1], "SKIP").status)
        val mine = round(g.tripId, roundId)
        assertEquals("LIKE", mine.getValue("myVotes").jsonObject.str(places[0]))
        assertNull(mine["tally"]?.takeIf { it.toString() != "null" })
        assertNull(mine["voters"]?.takeIf { it.toString() != "null" })

        use(g.viewer)
        places.forEach { assertEquals(HttpStatusCode.NoContent, vote(g.tripId, roundId, it, "LIKE").status) }

        // The owner sees names live.
        use(g.owner)
        val owners = round(g.tripId, roundId)
        val first = owners.getValue("tally").jsonArray.first { it.jsonObject.str("placeId") == places[0] }.jsonObject
        assertEquals("2", first.str("likes"))
        assertEquals(setOf(g.editor.userId, g.viewer.userId), first.getValue("likedBy").jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals(3, owners.getValue("voters").jsonArray.size)
        assertEquals("1", owners.str("finishedCount")) // only the viewer voted on every card

        // Only the OWNER decides.
        use(g.editor)
        assertEquals(HttpStatusCode.Forbidden, post("/trips/${g.tripId}/rounds/$roundId/decide", """{"placeIds":["${places[0]}"]}""").status)
        use(g.owner)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${g.tripId}/rounds/$roundId/decide", """{"placeIds":["nope"]}""").status)
        assertEquals(HttpStatusCode.NoContent, post("/trips/${g.tripId}/rounds/$roundId/decide", """{"placeIds":["${places[0]}"]}""").status)
        assertEquals("ROUND_ALREADY_DECIDED", post("/trips/${g.tripId}/rounds/$roundId/decide", """{"placeIds":[]}""").errorCode())

        // Decided: votes are closed, members see counts without names and the owner's choice, and are told.
        use(g.viewer)
        assertEquals("VOTING_CLOSED", vote(g.tripId, roundId, places[2], "SKIP").errorCode())
        val closed = round(g.tripId, roundId)
        assertEquals(listOf(places[0]), closed.getValue("chosenPlaceIds").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(closed.getValue("tally").jsonArray.all { it.jsonObject.getValue("likedBy").jsonArray.isEmpty() })
        val result = inbox().single { it.str("type") == "VOTE_RESULT" }.params()
        assertEquals("DECIDED", result["stage"])
        assertEquals("1", result["chosenCount"])
        use(g.owner)
        assertTrue(inbox().none { it.params()["stage"] == "DECIDED" }, "the owner is not told about their own decision")

        val summary = get("/trips/${g.tripId}/rounds").data().jsonArray.single().jsonObject
        assertEquals("1", summary.str("chosenCount"))
    }

    @Test
    fun undoAndChangeReplaceTheVote() = apiTest(ai = ai) {
        val g = group()
        val (roundId, places) = startRound(g.tripId)
        vote(g.tripId, roundId, places[0], "LIKE")
        vote(g.tripId, roundId, places[0], "SKIP")
        assertEquals("SKIP", round(g.tripId, roundId).getValue("myVotes").jsonObject.str(places[0]))
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${g.tripId}/rounds/$roundId/votes/${places[0]}").status)
        assertTrue(round(g.tripId, roundId).getValue("myVotes").jsonObject.isEmpty())
        assertEquals("ROUND_NOT_FOUND", vote(g.tripId, roundId, "not-a-card", "LIKE").errorCode())
    }

    @Test
    fun aVoteAfterTheWindowIsRejected() = apiTest(ai = ai) {
        val g = group()
        val (roundId, places) = startRound(g.tripId)
        TestDb.exec("UPDATE suggestion_rounds SET created_at = now() - interval '25 hours', closes_at = now() - interval '1 hour'")
        assertEquals("VOTING_CLOSED", vote(g.tripId, roundId, places[0], "LIKE").errorCode())
        // The owner may still decide after the window.
        assertEquals(HttpStatusCode.NoContent, post("/trips/${g.tripId}/rounds/$roundId/decide", """{"placeIds":[]}""").status)
    }

    @Test
    fun anExpiredRoundIsGone() = apiTest(ai = ai) {
        val g = group()
        val (roundId, _) = startRound(g.tripId)
        TestDb.exec("UPDATE suggestion_rounds SET created_at = now() - interval '8 days', closes_at = now() - interval '7 days', expires_at = now() - interval '1 hour'")
        assertEquals("ROUND_NOT_FOUND", get("/trips/${g.tripId}/rounds/$roundId").errorCode())
        assertEquals(0, get("/trips/${g.tripId}/rounds").data().jsonArray.size)
    }

    @Test
    fun aRemovedMembersVotesStopCounting() = apiTest(ai = ai) {
        val g = group()
        val (roundId, places) = startRound(g.tripId)
        use(g.editor)
        vote(g.tripId, roundId, places[0], "LIKE")
        use(g.owner)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${g.tripId}/members/${g.editor.userId}").status)
        val tally = round(g.tripId, roundId).getValue("tally").jsonArray.first { it.jsonObject.str("placeId") == places[0] }.jsonObject
        assertEquals("0", tally.str("likes"))
        assertEquals("2", round(g.tripId, roundId).str("memberCount"))
    }

    @Test
    fun aTripHasAtMostThreeOpenRoundsAndRoundPlacesAreNotOfferedAgain() = apiTest(ai = ai) {
        val g = group()
        val seen = mutableSetOf<String>()
        repeat(3) { seen += startRound(g.tripId).second }
        val fourth = post("/recommendations", search(g.tripId))
        assertEquals(HttpStatusCode.Conflict, fourth.status)
        assertEquals("ROUND_LIMIT", fourth.errorCode())
        assertEquals(9, seen.size, "every round got new places")
    }

    @Test
    fun aNextBatchAppendsToTheRequestersOpenRound() = apiTest(ai = ai) {
        val g = group()
        val (roundId, _) = startRound(g.tripId)
        val next = post("/recommendations", search(g.tripId, newSearch = false, roundId = roundId))
        assertEquals(HttpStatusCode.OK, next.status)
        assertEquals(roundId, next.data().jsonObject.str("roundId"))
        assertEquals(6, round(g.tripId, roundId).getValue("places").jsonArray.size)

        use(g.editor)
        assertEquals(HttpStatusCode.Forbidden, post("/recommendations", search(g.tripId, newSearch = false, roundId = roundId)).status)
    }

    @Test
    fun theLastVoteAndTheClockTellTheOwnerAndTheClosingReminderGoesToThoseBehind() = apiTest(ai = ai) {
        val g = group()
        use(g.editor)
        val (roundId, places) = startRound(g.tripId)
        places.forEach { vote(g.tripId, roundId, it, "LIKE") }

        // A reminder a few hours before closing reaches the owner and the viewer (cards left), not the editor (done).
        val config = TestDb.config()
        val rounds = RoundService(config, PhotoSigner(config.photoSigningSecret))
        rounds.tick(Instant.now().plus(22, ChronoUnit.HOURS))
        rounds.tick(Instant.now().plus(22, ChronoUnit.HOURS))
        use(g.viewer)
        val closing = inbox().filter { it.params()["stage"] == "CLOSING" }
        assertEquals(1, closing.size, "dedupe: one reminder")
        assertEquals("3", closing.single().params()["remaining"])
        use(g.editor)
        assertTrue(inbox().none { it.params()["stage"] == "CLOSING" })

        // Everyone finishing tells the owner it is time to decide, once.
        use(g.viewer)
        places.forEach { vote(g.tripId, roundId, it, "SKIP") }
        use(g.owner)
        places.forEach { vote(g.tripId, roundId, it, "LIKE") } // the owner's own last vote sends nothing
        assertTrue(inbox().none { it.params()["stage"] == "READY" })
        rounds.tick(Instant.now().plus(25, ChronoUnit.HOURS))
        val ready = inbox().single { it.params()["stage"] == "READY" }.params()
        assertEquals("3", ready["voted"])
        assertEquals("3", ready["members"])
    }
}
