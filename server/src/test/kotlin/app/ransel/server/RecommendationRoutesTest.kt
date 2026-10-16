package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.PhotoSigner
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlacesQuotaException
import app.ransel.server.ai.PlacesClient
import app.ransel.shared.model.Category
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scripted LLM: returns [candidates], or throws for the first [failTimes] calls. */
private class FakeLlm(
    var candidates: List<LlmCandidate>,
    var failTimes: Int = 0,
) : LlmClient {
    override val modelName = "fake-model"
    val calls = AtomicInteger()
    var lastQuery: LlmQuery? = null

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> {
        calls.incrementAndGet()
        lastQuery = query
        if (failTimes > 0) {
            failTimes--
            error("boom")
        }
        return candidates
    }
}

/** Knows a fixed set of places; everything else "does not exist". */
private class FakePlaces(
    private val known: Map<String, Pair<Double, Double>>,
    var allFail: Boolean = false,
) : PlacesClient {
    val searches = AtomicInteger()
    val details = AtomicInteger()
    val ratedLookups = AtomicInteger()

    override suspend fun findPlaceId(name: String, destination: String): String? {
        searches.incrementAndGet()
        if (allFail) error("places down")
        return if (name in known) "place-$name" else null
    }

    var noPhotoFor: Set<String> = emptySet()
    var quotaHit = false
    val photoCalls = AtomicInteger()

    override suspend fun photo(placeId: String): PlacePhoto? {
        photoCalls.incrementAndGet()
        if (quotaHit) throw PlacesQuotaException(429)
        return if (placeId in noPhotoFor) null else PlacePhoto(byteArrayOf(9, 8, 7), "image/jpeg")
    }

    override suspend fun details(placeId: String, withRating: Boolean): PlaceDetails? {
        details.incrementAndGet()
        if (withRating) ratedLookups.incrementAndGet()
        val name = placeId.removePrefix("place-")
        val (lat, lng) = known[name] ?: return null
        return PlaceDetails(placeId, lat, lng, "Jl. $name", if (withRating) 4.5 else null, if (withRating) "Foto $name" else null)
    }
}

private fun candidate(name: String, category: Category = Category.ATTRACTION) =
    LlmCandidate(name = name, category = category, reason = "Cocok untuk $name", priceLevel = 2, suggestedDurationMin = 90)

private val BALI = mapOf(
    "Pantai Kuta" to (-8.72 to 115.17),
    "Tanah Lot" to (-8.62 to 115.09),
    "Warung Babi Guling" to (-8.50 to 115.26),
    "Kopi Seniman" to (-8.51 to 115.26),
    "Pura Uluwatu" to (-8.83 to 115.08),
)

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

class RecommendationRoutesTest {
    private fun request(
        tripId: String,
        destination: String = "Bali",
        notes: String? = null,
        newSearch: Boolean = true,
        exclude: List<String> = emptyList(),
    ) = buildString {
        append("""{"tripId":"$tripId","destination":"$destination","startDate":"2026-11-20","endDate":"2026-11-23",""")
        append(""""budget":"SEDANG","interests":["FOOD","NATURE"],"style":"SANTAI","newSearch":$newSearch""")
        if (notes != null) append(""","notes":"$notes"""")
        append(""","excludePlaceIds":[${exclude.joinToString(",") { "\"$it\"" }}]}""")
    }

    private suspend fun Api.trip(): String = UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }

    private fun fakes(llmFail: Int = 0) = FakeLlm(
        listOf(
            candidate("Pantai Kuta"), candidate("Warung Babi Guling", Category.FOOD),
            candidate("Tanah Lot"), candidate("Kopi Seniman", Category.FOOD), candidate("Tempat Karangan AI"),
        ),
        failTimes = llmFail,
    ) to FakePlaces(BALI)

    @Test
    fun groundsSuggestionsDropsInventedPlacesAndGroupsByCategory() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val tripId = trip()
            val response = post("/recommendations", request(tripId))
            assertEquals(HttpStatusCode.OK, response.status)

            val data = response.data().jsonObject
            val groups = data.getValue("groups").jsonArray.map { it.jsonObject }
            assertEquals(listOf("ATTRACTION", "FOOD"), groups.map { it.str("category") })
            val names = groups.flatMap { g -> g.getValue("items").jsonArray.map { it.jsonObject.str("name") } }
            assertEquals(4, names.size)
            assertTrue("Tempat Karangan AI" !in names, "a place Places cannot find must be dropped")

            val first = groups.first().getValue("items").jsonArray.first().jsonObject
            assertEquals("place-Pantai Kuta", first.str("placeId"))
            assertEquals(-8.72, first.getValue("lat").jsonPrimitive.content.toDouble())
            assertEquals(90, first.str("suggestedDurationMin").toInt())
            assertTrue(first.str("photoUrl").startsWith("/places/photo/place-Pantai Kuta?exp="), "every card gets a signed photo path")
            assertEquals("Foto Pantai Kuta", first.str("photoAttribution"))

            val quota = data.getValue("quota").jsonObject
            assertEquals("1", quota.str("used"))
            assertEquals("10", quota.str("limit"))
            assertTrue(quota.str("resetsAt").endsWith("Z"))
        }
    }

    @Test
    fun onlyTheTopCardsGetARatingLookup() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val items = post("/recommendations", request(trip())).data().jsonObject.getValue("groups").jsonArray
                .flatMap { it.jsonObject.getValue("items").jsonArray }.map { it.jsonObject }
            assertEquals(3, places.ratedLookups.get())
            assertEquals(3, items.count { it["rating"] != null && it["rating"].toString() != "null" })
            assertEquals(4, places.searches.get() - 1) // 5 candidates searched; none repeated
        }
    }

    @Test
    fun secondIdenticalSearchComesFromCacheEvenForAnotherUser() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val tripA = trip()
            post("/recommendations", request(tripA))
            assertEquals(1, llm.calls.get())

            signUp()
            val tripB = trip()
            val again = post("/recommendations", request(tripB, destination = "  bali "))
            assertEquals(HttpStatusCode.OK, again.status)
            assertEquals(1, llm.calls.get(), "same destination/interests/budget/style must not call the LLM again")
            assertEquals("1", again.data().jsonObject.getValue("quota").jsonObject.str("used")) // a search is still a search

            post("/recommendations", request(tripB, notes = "tanpa babi"))
            assertEquals(2, llm.calls.get(), "notes change the answer, so they are part of the cache key")
        }
    }

    @Test
    fun likedAndSkippedPlacesAreExcludedAndUndoBringsThemBack() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val tripId = trip()
            assertEquals(
                HttpStatusCode.NoContent,
                post("/recommendations/feedback", """{"tripId":"$tripId","placeId":"place-Pantai Kuta","placeName":"Pantai Kuta","category":"ATTRACTION","action":"SKIP"}""").status,
            )
            post("/recommendations/feedback", """{"tripId":"$tripId","placeId":"place-Tanah Lot","placeName":"Tanah Lot","category":"ATTRACTION","action":"LIKE"}""")

            fun names(json: JsonObject) = json.getValue("groups").jsonArray.flatMap { it.jsonObject.getValue("items").jsonArray }.map { it.jsonObject.str("name") }
            val afterSwipes = names(post("/recommendations", request(tripId, exclude = listOf("place-Kopi Seniman"))).data().jsonObject)
            assertEquals(listOf("Warung Babi Guling"), afterSwipes)

            assertEquals(HttpStatusCode.NoContent, delete("/recommendations/feedback/$tripId/place-Pantai%20Kuta").status)
            val afterUndo = names(post("/recommendations", request(tripId, exclude = listOf("place-Kopi Seniman"))).data().jsonObject)
            assertTrue("Pantai Kuta" in afterUndo)

            // the next LLM request (a cache miss) carries the taste signal
            post("/recommendations", request(tripId, notes = "baru"))
            assertEquals(listOf("Tanah Lot"), llm.lastQuery!!.liked)
        }
    }

    @Test
    fun dailyQuotaIsTenAndBackgroundBatchesDoNotCount() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val tripId = trip()
            repeat(10) { assertEquals(HttpStatusCode.OK, post("/recommendations", request(tripId)).status) }

            val blocked = post("/recommendations", request(tripId))
            assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
            assertEquals("AI_QUOTA_EXCEEDED", blocked.errorCode())
            val details = blocked.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject
            assertEquals("10", details.str("used"))
            assertTrue(details.str("resetsAt").isNotBlank())

            val background = post("/recommendations", request(tripId, newSearch = false))
            assertEquals(HttpStatusCode.OK, background.status)
            assertEquals("10", background.data().jsonObject.getValue("quota").jsonObject.str("used"))
        }
    }

    @Test
    fun llmFailingTwiceIs503AndDoesNotSpendQuota() {
        val llm = FakeLlm(listOf(candidate("Pantai Kuta")), failTimes = 2)
        val places = FakePlaces(BALI)
        apiTest(ai = AiServices(llm, places)) {
            val tripId = trip()
            val failed = post("/recommendations", request(tripId))
            assertEquals(HttpStatusCode.ServiceUnavailable, failed.status)
            assertEquals("AI_UNAVAILABLE", failed.errorCode())
            assertEquals(2, llm.calls.get())

            // third call succeeds and shows nothing was charged
            val ok = post("/recommendations", request(tripId))
            assertEquals("1", ok.data().jsonObject.getValue("quota").jsonObject.str("used"))
        }
    }

    @Test
    fun oneLlmHiccupIsRetriedTransparently() {
        val (llm, places) = fakes(llmFail = 1)
        apiTest(ai = AiServices(llm, places)) {
            assertEquals(HttpStatusCode.OK, post("/recommendations", request(trip())).status)
            assertEquals(2, llm.calls.get())
        }
    }

    @Test
    fun placesBeingDownIs503NotAnEmptyResult() {
        val llm = FakeLlm(listOf(candidate("Pantai Kuta"), candidate("Tanah Lot")))
        apiTest(ai = AiServices(llm, FakePlaces(BALI, allFail = true))) {
            val response = post("/recommendations", request(trip()))
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("AI_UNAVAILABLE", response.errorCode())
        }
    }

    @Test
    fun nothingGroundedGivesAnEmptyAnswerAndIsNotCached() {
        val llm = FakeLlm(listOf(candidate("Tempat Karangan 1"), candidate("Tempat Karangan 2")))
        apiTest(ai = AiServices(llm, FakePlaces(BALI))) {
            val tripId = trip()
            val response = post("/recommendations", request(tripId))
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, response.data().jsonObject.getValue("groups").jsonArray.size)
            post("/recommendations", request(tripId))
            assertEquals(2, llm.calls.get(), "an empty result must not be cached")
        }
    }

    @Test
    fun accessAndValidation() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val tripId = trip()
            assertEquals("VALIDATION_FAILED", post("/recommendations", request(tripId, destination = " ")).errorCode())
            assertEquals("VALIDATION_FAILED", post("/recommendations", request("not-a-uuid")).errorCode())

            // a stranger cannot spend quota on somebody else's trip
            signUp()
            assertEquals("TRIP_NOT_FOUND", post("/recommendations", request(tripId)).errorCode())

            token = null
            assertEquals(HttpStatusCode.Unauthorized, post("/recommendations", request(tripId)).status)
        }
    }

    @Test
    fun withoutKeysExploreAnswers503ButTheRestKeepsWorking() = apiTest {
        val tripId = trip()
        val response = post("/recommendations", request(tripId))
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("AI_UNAVAILABLE", response.errorCode())
        assertEquals(HttpStatusCode.OK, get("/trips/$tripId").status)
    }

    // ── place photo proxy ───────────────────────────────────────────────────

    private val signer = PhotoSigner(TestDb.config().photoSigningSecret)

    /** The photo path of the first card, relative to /api/v1 and used without any bearer token. */
    private suspend fun Api.firstPhotoPath(): String {
        val groups = post("/recommendations", request(trip())).data().jsonObject.getValue("groups").jsonArray
        return groups.first().jsonObject.getValue("items").jsonArray.first().jsonObject.str("photoUrl")
    }

    @Test
    fun validSignatureStreamsThePhotoWithoutABearerToken() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val path = firstPhotoPath()
            token = null
            val response = get(path)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("image/jpeg", response.headers["Content-Type"])
            assertEquals(listOf<Byte>(9, 8, 7), response.readRawBytes().toList())
        }
    }

    @Test
    fun badOrExpiredSignatureIsRejectedWithoutCallingGoogle() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val path = firstPhotoPath()
            assertEquals(HttpStatusCode.NotFound, get(path.replace("sig=", "sig=x")).status)
            assertEquals(HttpStatusCode.NotFound, get(path.substringBefore("&sig=")).status)
            assertEquals(HttpStatusCode.NotFound, get("/places/photo/place-Tanah Lot?" + path.substringAfter('?')).status)

            val expired = PhotoSigner(TestDb.config().photoSigningSecret, validFor = (-1).minutes).signedPath("place-Pantai Kuta")
            assertEquals(HttpStatusCode.NotFound, get(expired).status)
            assertEquals(0, places.photoCalls.get())
        }
    }

    @Test
    fun placeWithoutPhotoOrRefusedByGoogleIs404NeverAServerError() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            places.noPhotoFor = setOf("place-Pantai Kuta")
            assertEquals(HttpStatusCode.NotFound, get(signer.signedPath("place-Pantai Kuta")).status)

            places.quotaHit = true
            assertEquals(HttpStatusCode.NotFound, get(signer.signedPath("place-Tanah Lot")).status)
        }
    }

    @Test
    fun repeatedRequestsForOnePhotoHitGoogleOnce() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val path = signer.signedPath("place-Pantai Kuta")
            repeat(3) { assertEquals(HttpStatusCode.OK, get(path).status) }
            assertEquals(1, places.photoCalls.get())
        }
    }
}
