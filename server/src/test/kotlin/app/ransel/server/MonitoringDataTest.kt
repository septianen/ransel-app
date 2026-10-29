package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.InterestSuggestions
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlacesClient
import app.ransel.server.service.Monitoring
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Karung stage B data (B9.6): ai_search_log, provider_usage, error_counts, users.last_seen_at. */
class MonitoringDataTest {
    private object Llm : LlmClient {
        override val modelName = "fake-model"
        override suspend fun suggest(query: LlmQuery) = listOf(
            LlmCandidate(name = "Pantai Kuta", reason = "Pasir putih", suggestedDurationMin = 90),
            LlmCandidate(name = "Tanah Lot", reason = "Pura di laut", suggestedDurationMin = 60),
        )
        override suspend fun suggestInterests(destination: String, month: String, language: String) =
            InterestSuggestions(listOf("Diving"), lowMaxUsd = 30.0, midMaxUsd = 90.0)
    }

    private object Places : PlacesClient {
        private val known = mapOf("Pantai Kuta" to (-8.72 to 115.17), "Tanah Lot" to (-8.62 to 115.09))
        override suspend fun findPlaceId(name: String, destination: String) = if (name in known) "place-$name" else null
        override suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails? {
            val (lat, lng) = known[placeId.removePrefix("place-")] ?: return null
            return PlaceDetails(placeId, lat, lng, null)
        }
        override suspend fun photo(placeId: String): PlacePhoto? = null
    }

    private val ai = AiServices(Llm, Places)

    private fun search(tripId: String) =
        """{"tripId":"$tripId","priceLevel":"MEDIUM","interests":["FOOD"],"extraInterests":["Diving"],"style":"SANTAI","newSearch":true,"excludePlaceIds":[]}"""

    private suspend fun Api.trip(): String =
        UUID.randomUUID().toString().also { post("/trips", tripJson(it, start = "2099-11-20", end = "2099-11-23", destination = "Bali")) }

    @Test
    fun aSearchIsLoggedNewThenCacheWithItsCostAndNoPromptText() = apiTest(ai = ai) {
        val trip = trip()
        assertEquals(HttpStatusCode.OK, post("/recommendations", search(trip)).status)
        assertEquals(HttpStatusCode.OK, post("/recommendations", search(trip)).status)
        val rows = TestDb.column(
            "SELECT source || '|' || kind || '|' || destination || '|' || cards || '|' || credits_charged || '|' || array_to_string(interests, ',') || '|' || llm_cost_micro_usd " +
                "FROM ai_search_log WHERE user_id = '$userId' ORDER BY created_at",
        )
        assertEquals(listOf("NEW|EXPLORE|Bali|2|3|FOOD,Diving|3000", "CACHE|EXPLORE|Bali|2|3|FOOD,Diving|0"), rows)

        Monitoring.flush()
        // the search's call, plus the destination interests warmed in the background when the trip was saved
        val (calls, cost) = TestDb.column("SELECT calls || '|' || cost_micro_usd FROM provider_usage WHERE call = 'LLM'").single().split('|').map { it.toLong() }
        assertTrue(calls in 1..2, "LLM calls: $calls")
        assertEquals(calls * 3000, cost)
        assertTrue(TestDb.column("SELECT calls FROM provider_usage WHERE call = 'PLACES_DETAILS'").single().toLong() >= 2)
    }

    @Test
    fun aSearchWithoutCreditsIsLoggedAsNoCredits() = apiTest(TestDb.config().copy(creditSignupGrant = 0, creditDailyFree = 0), ai = ai) {
        val trip = trip()
        assertEquals(HttpStatusCode.PaymentRequired, post("/recommendations", search(trip)).status)
        assertEquals(listOf("NO_CREDITS|0|0"), TestDb.column("SELECT source || '|' || cards || '|' || credits_charged FROM ai_search_log"))
    }

    @Test
    fun badInputLeavesNoSearchRow() = apiTest(ai = ai) {
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/recommendations", search("not-a-uuid")).status)
        assertEquals(emptyList(), TestDb.column("SELECT id FROM ai_search_log"))
    }

    @Test
    fun errorsAreCountedPerRouteTemplateWithoutIds() = apiTest {
        val missing = UUID.randomUUID()
        repeat(2) { assertEquals(HttpStatusCode.NotFound, get("/trips/$missing").status) }
        token = null
        assertEquals(HttpStatusCode.Unauthorized, get("/trips").status)
        Monitoring.flush()
        val rows = TestDb.column("SELECT code || '|' || route || '|' || status || '|' || count FROM error_counts ORDER BY code")
        assertEquals(listOf("TRIP_NOT_FOUND|GET /trips/{id}|404|2", "UNAUTHORIZED|GET /trips|401|1"), rows)
        assertEquals(listOf("1"), TestDb.column("SELECT count(DISTINCT user_id) FROM error_count_users WHERE code = 'TRIP_NOT_FOUND'"))
        assertTrue(TestDb.column("SELECT route FROM error_counts").none { missing.toString() in it })
    }

    @Test
    fun anAuthenticatedRequestSetsLastSeenAndTheActiveDay() = apiTest {
        get("/trips")
        // written off the request's path
        var seen = emptyList<String>()
        repeat(50) {
            seen = TestDb.column("SELECT last_seen_at FROM users WHERE id = '$userId' AND last_seen_at IS NOT NULL")
            if (seen.isNotEmpty()) return@repeat
            Thread.sleep(100)
        }
        assertEquals(1, seen.size)
        assertEquals(listOf("1"), TestDb.column("SELECT count(*) FROM user_active_days WHERE user_id = '$userId'"))
    }
}
