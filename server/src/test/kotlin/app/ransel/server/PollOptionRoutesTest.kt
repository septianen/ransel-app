package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.FxRates
import app.ransel.server.ai.InterestSuggestions
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlacesClient
import app.ransel.shared.model.Category
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private class StayLlm(val candidates: List<LlmCandidate>) : LlmClient {
    override val modelName = "fake"
    var last: LlmQuery? = null
    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> {
        last = query
        return candidates
    }
    override suspend fun suggestInterests(destination: String, month: String, language: String) = InterestSuggestions(emptyList(), 30.0, 90.0)
}

private class PollPlaces : PlacesClient {
    var lastDestination: String? = null
    override suspend fun findPlaceId(name: String, destination: String): String? {
        lastDestination = destination
        return "place-$name"
    }
    override suspend fun details(placeId: String, withPhotoCredit: Boolean) = PlaceDetails(placeId, -8.7, 115.2, "Jl. ${placeId.removePrefix("place-")}")
    override suspend fun photo(placeId: String): PlacePhoto? = null
}

private object IdrFx : FxRates {
    override fun toUsd(amountMinor: Long, currency: String) = amountMinor / 16_000.0
    override fun fromUsd(usd: Double, currency: String): Long? = if (currency == "IDR") (usd * 16_000).toLong() else null
}

private fun hotel(name: String, usd: Double?) = LlmCandidate(name = name, category = Category.LODGING, reason = "Dekat pantai", costUsd = usd)

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

class PollOptionRoutesTest {
    private suspend fun Api.tripWithPoll(kind: String = "STAY", options: String = ""): Pair<String, String> {
        val tripId = UUID.randomUUID().toString()
        post("/trips", tripJson(tripId))
        val pollId = UUID.randomUUID().toString()
        val r = post("/trips/$tripId/polls", """{"id":"$pollId","kind":"$kind","question":"Menginap di mana?","closesAt":"2099-01-01T00:00:00Z","options":[$options]}""")
        assertEquals(HttpStatusCode.Created, r.status)
        return tripId to pollId
    }

    private fun opt(label: String, id: String = UUID.randomUUID().toString()) = """{"id":"$id","label":"$label"}"""

    @Test
    fun bulkAddsAllOptionsInOrderOrNone() = apiTest {
        val (_, pollId) = tripWithPoll(options = opt("Satu"))
        val ok = post("/polls/$pollId/options/bulk", """{"options":[${opt("Dua")},${opt("Tiga")}]}""")
        assertEquals(HttpStatusCode.Created, ok.status)
        assertEquals(listOf("Satu", "Dua", "Tiga"), ok.data().jsonObject.getValue("options").jsonArray.map { it.jsonObject.str("label") })

        // one invalid option rejects the whole batch
        val bad = post("/polls/$pollId/options/bulk", """{"options":[${opt("Empat")},{"id":"${UUID.randomUUID()}","label":" "}]}""")
        assertEquals("VALIDATION_FAILED", bad.errorCode())
        assertEquals(3, get("/polls/$pollId").data().jsonObject.getValue("options").jsonArray.size)

        assertEquals("VALIDATION_FAILED", post("/polls/$pollId/options/bulk", """{"options":[]}""").errorCode())
        val dup = UUID.randomUUID().toString()
        assertEquals("VALIDATION_FAILED", post("/polls/$pollId/options/bulk", """{"options":[${opt("A", dup)},${opt("B", dup)}]}""").errorCode())
    }

    @Test
    fun bulkRespectsTheOptionLimit() = apiTest {
        val (_, pollId) = tripWithPoll()
        val many = (1..20).joinToString(",") { opt("O$it") }
        assertEquals(HttpStatusCode.Created, post("/polls/$pollId/options/bulk", """{"options":[$many]}""").status)
        assertEquals("VALIDATION_FAILED", post("/polls/$pollId/options/bulk", """{"options":[${opt("Satu lagi")}]}""").errorCode())
    }

    @Test
    fun searchReturnsPricedResultsAndCharges() {
        val llm = StayLlm(listOf(hotel("Villa Murah", 30.0), hotel("Resort Mewah", 400.0), hotel("Hotel Pas", 60.0), hotel("Hotel Gratis", null)))
        val places = PollPlaces()
        apiTest(ai = AiServices(llm, places), fx = IdrFx) {
            val (_, pollId) = tripWithPoll()
            val r = post("/polls/$pollId/search", """{"area":"Ubud","minMinor":500000,"maxMinor":1200000,"needs":"kolam renang"}""")
            assertEquals(HttpStatusCode.OK, r.status)
            val results = r.data().jsonObject.getValue("results").jsonArray.map { it.jsonObject }
            // 30 USD = 480.000 (below 70% of the minimum) and 400 USD = 6.400.000 (above the maximum) are dropped
            assertEquals(listOf("Hotel Pas", "Hotel Gratis"), results.map { it.str("name") })
            assertEquals("960000", results[0].str("priceMinor"))
            assertEquals("NIGHT", results[0].str("priceUnit"))
            assertEquals("Ubud", places.lastDestination)
            val q = assertNotNull(llm.last)
            assertEquals(Category.LODGING, q.onlyCategory)
            assertEquals("kolam renang", q.notes)
            // a search costs 3 credits; the free daily allowance pays it first
            val credits = r.data().jsonObject.getValue("credits").jsonObject
            assertEquals("30", credits.str("balance"))
            assertEquals("0", credits.str("dailyFreeLeft"))
        }
    }

    @Test
    fun searchFallsBackToTheTripDestinationAndLeavesOutExistingPlaces() {
        val llm = StayLlm(listOf(hotel("Hotel Pas", 60.0), hotel("Hotel Lama", 50.0)))
        val places = PollPlaces()
        apiTest(ai = AiServices(llm, places), fx = IdrFx) {
            val (_, pollId) = tripWithPoll(options = """{"id":"${UUID.randomUUID()}","label":"Hotel Lama","placeId":"place-Hotel Lama"}""")
            val r = post("/polls/$pollId/search", "{}")
            assertEquals(HttpStatusCode.OK, r.status)
            assertEquals("Bali", places.lastDestination)
            assertEquals(listOf("Hotel Pas"), r.data().jsonObject.getValue("results").jsonArray.map { it.jsonObject.str("name") })
        }
    }

    @Test
    fun searchOnlyForOpenStayAndFoodPolls() {
        apiTest(ai = AiServices(StayLlm(listOf(hotel("X", 10.0))), PollPlaces()), fx = IdrFx) {
            val (_, custom) = tripWithPoll(kind = "CUSTOM")
            assertEquals("VALIDATION_FAILED", post("/polls/$custom/search", "{}").errorCode())
            val (_, stay) = tripWithPoll()
            TestDb.exec("UPDATE polls SET status = 'CLOSED' WHERE id = '$stay'")
            assertEquals("POLL_CLOSED", post("/polls/$stay/search", "{}").errorCode())
            val (_, food) = tripWithPoll(kind = "FOOD")
            assertEquals(HttpStatusCode.OK, post("/polls/$food/search", "{}").status)
        }
    }

    @Test
    fun searchValidatesInputNeedsAVerifiedEmailAndAnEditor() {
        apiTest(ai = AiServices(StayLlm(listOf(hotel("X", 10.0))), PollPlaces()), fx = IdrFx) {
            val (tripId, pollId) = tripWithPoll()
            assertEquals("VALIDATION_FAILED", post("/polls/$pollId/search", """{"minMinor":900,"maxMinor":100}""").errorCode())
            assertEquals("VALIDATION_FAILED", post("/polls/$pollId/search", """{"needs":"${"x".repeat(201)}"}""").errorCode())

            val owner = session()
            val code = post("/trips/$tripId/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
            signUp()
            post("/invites/$code/accept")
            assertEquals(HttpStatusCode.Forbidden, post("/polls/$pollId/search", "{}").status)

            use(owner)
            TestDb.exec("UPDATE users SET email_verified_at = NULL WHERE id = '${owner.userId}'")
            assertEquals("EMAIL_NOT_VERIFIED", post("/polls/$pollId/search", "{}").errorCode())
        }
    }
}
