package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.InterestSuggestions
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlacesClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

/** Every search proposes three new places; [gate] holds the model call until the test lets it go. */
private class GatedLlm : LlmClient {
    override val modelName = "fake-model"
    val calls = AtomicInteger()
    @Volatile var started = CompletableDeferred<Unit>()
    @Volatile var gate: CompletableDeferred<Unit>? = null
    @Volatile var pauseMillis = 0L

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> {
        val n = calls.incrementAndGet()
        started.complete(Unit)
        gate?.await()
        if (pauseMillis > 0) delay(pauseMillis)
        return (1..3).map { LlmCandidate(name = "Tempat $n-$it", reason = "Cocok", suggestedDurationMin = 60) }
    }

    override suspend fun suggestInterests(destination: String, month: String, language: String) =
        InterestSuggestions(emptyList(), lowMaxUsd = 30.0, midMaxUsd = 90.0)
}

private class CancelTestPlaces : PlacesClient {
    override suspend fun findPlaceId(name: String, destination: String) = "place-$name"
    override suspend fun details(placeId: String, withPhotoCredit: Boolean) = PlaceDetails(placeId, -8.5, 115.2, "Jl. Bali")
    override suspend fun photo(placeId: String): PlacePhoto? = null
}

private var noteCount = 0

/** A different note each time keeps the recommendation cache out of the way, unless [cached] is true. */
private fun search(tripId: String, searchId: String?, cached: Boolean = false) =
    """{"tripId":"$tripId","priceLevel":"MEDIUM","interests":["NATURE"],"style":"SANTAI","newSearch":true""" +
        (if (cached) "" else ""","notes":"n${noteCount++}"""") +
        (searchId?.let { ""","searchId":"$it"""" } ?: "") + "}"

private suspend fun Api.credits() = get("/credits").data().jsonObject
private suspend fun Api.spendable(): Int = credits().let { it.str("balance").toInt() + it.str("dailyFreeLeft").toInt() }
private suspend fun Api.rounds(tripId: String) = get("/trips/$tripId/rounds").data().jsonArray.size
private suspend fun Api.voteRequests() =
    get("/notifications").data().jsonObject.getValue("items").jsonArray.count { it.jsonObject.str("type") == "VOTE_REQUESTED" }

/** "Batalkan pencarian": the server stops the search before it charges credits or makes a voting round. */
class CancelSearchRoutesTest {
    private val llm = GatedLlm()
    private val ai get() = AiServices(llm, CancelTestPlaces())

    private suspend fun Api.newTrip() = UUID.randomUUID().toString().also { post("/trips", tripJson(it, title = "Bali 4 hari")) }

    @Test
    fun aCancelWhileTheModelWorksStopsTheSearchAndChargesNothing() = apiTest(ai = ai) {
        val tripId = newTrip()
        val before = spendable()
        llm.gate = CompletableDeferred()
        val searchId = UUID.randomUUID().toString()
        coroutineScope {
            val search = async { post("/recommendations", search(tripId, searchId)) }
            llm.started.await()
            val cancel = delete("/recommendations/searches/$searchId")
            assertEquals(HttpStatusCode.OK, cancel.status)
            assertEquals("true", cancel.data().jsonObject.str("cancelled"))
            val answer = search.await()
            assertEquals(HttpStatusCode.Conflict, answer.status)
            assertEquals("SEARCH_CANCELLED", answer.errorCode())
        }
        assertEquals(before, spendable(), "nothing was charged")
        assertEquals(30, credits().str("balance").toInt())
        assertEquals(3, credits().str("dailyFreeLeft").toInt())
    }

    @Test
    fun aCancelThatArrivesBeforeTheSearchRefusesItWithoutAskingTheModel() = apiTest(ai = ai) {
        val tripId = newTrip()
        val searchId = UUID.randomUUID().toString()
        assertEquals("true", delete("/recommendations/searches/$searchId").data().jsonObject.str("cancelled"))
        val answer = post("/recommendations", search(tripId, searchId))
        assertEquals(HttpStatusCode.Conflict, answer.status)
        assertEquals("SEARCH_CANCELLED", answer.errorCode())
        assertEquals(0, llm.calls.get(), "the model was never asked")
        assertEquals(33, spendable())
    }

    @Test
    fun aCancelAfterThePointOfNoReturnIsTooLateAndTheCreditsAreChargedOnce() = apiTest(ai = ai) {
        val tripId = newTrip()
        val searchId = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.OK, post("/recommendations", search(tripId, searchId)).status)
        assertEquals(30, spendable(), "3 credits, once")
        assertEquals("false", delete("/recommendations/searches/$searchId").data().jsonObject.str("cancelled"))
        assertEquals("false", delete("/recommendations/searches/$searchId").data().jsonObject.str("cancelled"), "asking again changes nothing")
        assertEquals(30, spendable())
    }

    @Test
    fun aSearchWithoutAnIdRunsAsBefore() = apiTest(ai = ai) {
        val tripId = newTrip()
        assertEquals(HttpStatusCode.OK, post("/recommendations", search(tripId, null)).status)
        assertEquals(30, spendable())
        assertEquals("INVALID_UUID", post("/recommendations", search(tripId, "not-a-uuid")).jsonBody()
            .getValue("error").jsonObject.getValue("details").jsonObject.str("searchId"))
    }

    @Test
    fun aCancelOfAnotherUserDoesNotTouchMySearch() = apiTest(ai = ai) {
        val tripId = newTrip()
        val me = session()
        val searchId = UUID.randomUUID().toString()
        llm.gate = CompletableDeferred()
        coroutineScope {
            val search = async { post("/recommendations", search(tripId, searchId)) }
            llm.started.await()
            signUp()
            delete("/recommendations/searches/$searchId") // somebody else, same id
            use(me)
            llm.gate!!.complete(Unit)
            assertEquals(HttpStatusCode.OK, search.await().status)
        }
        assertEquals(30, spendable())
    }

    @Test
    fun aCancelledGroupSearchMakesNoRoundAndNotifiesNobody() = apiTest(ai = ai) {
        val owner = session()
        val tripId = newTrip()
        val code = post("/trips/$tripId/invites", """{"role":"EDITOR","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        signUp(displayName = "Raka")
        val friend = session()
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
        use(owner)
        llm.gate = CompletableDeferred()
        val searchId = UUID.randomUUID().toString()
        coroutineScope {
            val search = async { post("/recommendations", search(tripId, searchId)) }
            llm.started.await()
            delete("/recommendations/searches/$searchId")
            assertEquals("SEARCH_CANCELLED", search.await().errorCode())
        }
        assertEquals(0, rounds(tripId))
        use(friend)
        assertEquals(0, voteRequests(), "the other member was not asked to vote")

        // the same people can search again afterwards: the cancelled one released its place
        use(owner)
        llm.gate = null
        val again = post("/recommendations", search(tripId, UUID.randomUUID().toString()))
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(1, rounds(tripId))
    }

    /**
     * The invariant: a search is charged if and only if its answer was sent. A cancel is fired at every moment of the
     * search, before, during and after the model call and the commit; the two outcomes never mix.
     */
    @Test
    fun cancelRacingTheSearchNeverChargesWithoutAnAnswerOrTwice() = apiTest(ai = ai) {
        val tripId = newTrip()
        llm.pauseMillis = 40
        var answered = 0
        var cancelled = 0
        for (round in 0 until 10) {
            val before = spendable()
            val searchId = UUID.randomUUID().toString()
            val status = coroutineScope {
                val search = async { post("/recommendations", search(tripId, searchId)) }
                delay((round * 13L) % 120) // 0 to 117 ms after the start: before, during and after the work
                delete("/recommendations/searches/$searchId")
                search.await()
            }
            val charged = before - spendable()
            when (status.status) {
                HttpStatusCode.OK -> { answered++; assertEquals(3, charged, "answered: charged exactly once (round $round)") }
                HttpStatusCode.Conflict -> { cancelled++; assertEquals(0, charged, "cancelled: not charged (round $round)") }
                else -> error("unexpected ${status.status}")
            }
        }
        assertTrue(answered + cancelled == 10)
        println("CANCEL RACE: answered $answered, cancelled $cancelled")
    }
}
