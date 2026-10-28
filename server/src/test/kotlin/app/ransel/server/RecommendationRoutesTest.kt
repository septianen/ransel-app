package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.FxRates
import app.ransel.server.ai.InterestSuggestions
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val interestCalls = AtomicInteger()
    var interestFailures = 0
    var interests = listOf("Diving", "Pura laut", "Sawah terasering")
    var lastInterestDestination: String? = null
    var lastExtra: List<String> = emptyList()

    override suspend fun suggestInterests(destination: String, month: String, language: String): InterestSuggestions {
        interestCalls.incrementAndGet()
        lastInterestDestination = destination
        if (interestFailures > 0) {
            interestFailures--
            error("boom")
        }
        return InterestSuggestions(interests, lowMaxUsd = 30.0, midMaxUsd = 90.0)
    }
    var lastQuery: LlmQuery? = null

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> {
        calls.incrementAndGet()
        lastQuery = query
        lastExtra = query.extraInterests
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
    var lastDestination: String? = null
    val searches = AtomicInteger()
    val details = AtomicInteger()
    val creditLookups = AtomicInteger()

    override suspend fun findPlaceId(name: String, destination: String): String? {
        lastDestination = destination
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

    override suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails? {
        details.incrementAndGet()
        if (withPhotoCredit) creditLookups.incrementAndGet()
        val name = placeId.removePrefix("place-")
        val (lat, lng) = known[name] ?: return null
        return PlaceDetails(placeId, lat, lng, "Jl. $name", if (withPhotoCredit) "Foto $name" else null)
    }
}

private fun candidate(name: String, category: Category = Category.ATTRACTION, cost: Double? = null) =
    LlmCandidate(name = name, category = category, reason = "Cocok untuk $name", priceLevel = 2, costUsd = cost, suggestedDurationMin = 90)

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
        price: String = "MEDIUM",
        notes: String? = null,
        newSearch: Boolean = true,
        exclude: List<String> = emptyList(),
        destination: String? = null,
        interests: List<String> = listOf("FOOD", "NATURE"),
        extra: List<String> = emptyList(),
    ) = buildString {
        append("""{"tripId":"$tripId","priceLevel":"$price","interests":[${interests.joinToString(",") { "\"$it\"" }}],""")
        append(""""extraInterests":[${extra.joinToString(",") { "\"$it\"" }}],"style":"SANTAI","newSearch":$newSearch""")
        if (destination != null) append(""","destination":"$destination"""")
        if (notes != null) append(""","notes":"$notes"""")
        append(""","excludePlaceIds":[${exclude.joinToString(",") { "\"$it\"" }}]}""")
    }

    private suspend fun Api.trip(destination: String = "Bali", start: String = "2026-11-20", end: String = "2026-11-23"): String =
        UUID.randomUUID().toString().also { post("/trips", tripJson(it, start = start, end = end, destination = destination)) }


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

            // a search costs 3 credits and the free daily allowance (3) pays it first
            val credits = data.getValue("credits").jsonObject
            assertEquals("30", credits.str("balance"))
            assertEquals("0", credits.str("dailyFreeLeft"))
            assertTrue(credits.str("resetsAt").endsWith("Z"))
            assertEquals(listOf("3", "1", "0"), credits.getValue("costs").jsonObject.let { c -> listOf("search", "nextBatch", "placeDetail").map { c.str(it) } })
        }
    }

    @Test
    fun onlyTheTopCardsGetAPhotoCreditAndNoRatingIsEverFetched() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val items = post("/recommendations", request(trip())).data().jsonObject.getValue("groups").jsonArray
                .flatMap { it.jsonObject.getValue("items").jsonArray }.map { it.jsonObject }
            assertEquals(3, places.creditLookups.get())
            assertEquals(3, items.count { it["photoAttribution"] != null })
            assertTrue(items.none { it["rating"] != null }, "ratings are Enterprise tier and are not fetched")
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
            val tripB = trip("  bali ")
            val again = post("/recommendations", request(tripB))
            assertEquals(HttpStatusCode.OK, again.status)
            assertEquals(1, llm.calls.get(), "same destination/interests/budget/style must not call the LLM again")
            // a cache hit is still a search: the second user paid 3 credits from the free daily allowance
            assertEquals("0", again.data().jsonObject.getValue("credits").jsonObject.str("dailyFreeLeft"))

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
    fun noCreditsIs402AndTheCheckHappensBeforeAnyAiCall() {
        val (llm, places) = fakes()
        // 6 credits and no free allowance: exactly two searches
        apiTest(config = TestDb.config().copy(creditSignupGrant = 6, creditDailyFree = 0), ai = AiServices(llm, places)) {
            val tripId = trip()
            repeat(2) { assertEquals(HttpStatusCode.OK, post("/recommendations", request(tripId, notes = "n$it")).status) }
            val callsBefore = llm.calls.get()

            val blocked = post("/recommendations", request(tripId, notes = "n3"))
            assertEquals(HttpStatusCode.PaymentRequired, blocked.status)
            assertEquals("INSUFFICIENT_CREDITS", blocked.errorCode())
            val details = blocked.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject
            assertEquals("3", details.str("cost"))
            assertEquals("0", details.str("available"))
            assertTrue(details.str("resetsAt").isNotBlank())
            assertEquals(callsBefore, llm.calls.get(), "a user without credits must not cost an LLM call")

            // the next batch is cheaper but still needs a credit
            assertEquals("INSUFFICIENT_CREDITS", post("/recommendations", request(tripId, newSearch = false)).errorCode())
        }
    }

    @Test
    fun parallelSearchesNeverSpendMoreThanTheUserHas() {
        val (llm, places) = fakes()
        apiTest(config = TestDb.config().copy(creditSignupGrant = 6, creditDailyFree = 0), ai = AiServices(llm, places)) {
            val tripId = trip()
            val statuses = kotlinx.coroutines.coroutineScope {
                (1..6).map { i -> async { post("/recommendations", request(tripId, notes = "p$i")).status } }.awaitAll()
            }
            // One search per user at a time: the others are turned away before the credit check and the LLM call.
            val paid = statuses.count { it == HttpStatusCode.OK }
            assertTrue(paid in 1..2, "$statuses")
            assertTrue(statuses.all { it == HttpStatusCode.OK || it == HttpStatusCode.TooManyRequests || it == HttpStatusCode.PaymentRequired }, "$statuses")
            assertEquals(paid, llm.calls.get(), "no LLM call that nobody pays for")

            // What is left still buys exactly the searches it covers, one after the other.
            var more = 0
            while (post("/recommendations", request(tripId, notes = "s$more")).status == HttpStatusCode.OK) more++
            assertEquals(2, paid + more, "6 credits pay exactly two searches")
            assertEquals("0", get("/credits").data().jsonObject.str("balance"))
        }
    }

    @Test
    fun nextBatchCostsOneCredit() {
        val (llm, places) = fakes()
        apiTest(config = TestDb.config().copy(creditSignupGrant = 4, creditDailyFree = 0), ai = AiServices(llm, places)) {
            val tripId = trip()
            assertEquals("1", post("/recommendations", request(tripId)).data().jsonObject.getValue("credits").jsonObject.str("balance"))
            assertEquals("0", post("/recommendations", request(tripId, newSearch = false)).data().jsonObject.getValue("credits").jsonObject.str("balance"))
            assertEquals(HttpStatusCode.PaymentRequired, post("/recommendations", request(tripId, newSearch = false)).status)
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

            // the third call succeeds and only that one search was charged
            val ok = post("/recommendations", request(tripId))
            assertEquals("0", ok.data().jsonObject.getValue("credits").jsonObject.str("dailyFreeLeft"))
            assertEquals("30", ok.data().jsonObject.getValue("credits").jsonObject.str("balance"))
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
            assertEquals("VALIDATION_FAILED", post("/recommendations", request(tripId, interests = emptyList())).errorCode())
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

    // ── Explore per trip (docs/specs/AI-Recommendation-Spec.md) ─────────────

    private suspend fun Api.names(response: io.ktor.client.statement.HttpResponse) =
        response.data().jsonObject.getValue("groups").jsonArray
            .flatMap { g -> g.jsonObject.getValue("items").jsonArray.map { it.jsonObject.str("name") } }.toSet()

    @Test
    fun priceLevelDropsPlacesMoreThanOneStepAwayAndKeepsFreeOnes() {
        // The fake guide (30 / 90 USD): Kuta 150 = HIGH, Tanah Lot 5 and Warung 8 = LOW, Kopi 40 = MEDIUM, Uluwatu free.
        val llm = FakeLlm(
            listOf(
                candidate("Pantai Kuta", cost = 150.0), candidate("Tanah Lot", cost = 5.0),
                candidate("Warung Babi Guling", Category.FOOD, cost = 8.0), candidate("Kopi Seniman", Category.FOOD, cost = 40.0),
                candidate("Pura Uluwatu"),
            ),
        )
        apiTest(ai = AiServices(llm, FakePlaces(BALI))) {
            val trip = trip()
            assertEquals(setOf("Tanah Lot", "Warung Babi Guling", "Kopi Seniman", "Pura Uluwatu"), names(post("/recommendations", request(trip, price = "LOW"))))
            assertEquals(5, names(post("/recommendations", request(trip, price = "MEDIUM"))).size, "MEDIUM is within one step of everything")
            assertEquals(setOf("Pantai Kuta", "Kopi Seniman", "Pura Uluwatu"), names(post("/recommendations", request(trip, price = "HIGH"))))
        }
    }

    @Test
    fun searchAsksTheLlmWithTheLocalPriceGuide() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            post("/recommendations", request(trip("Tokyo"), price = "LOW"))
            val guide = llm.lastQuery?.priceGuide
            assertEquals(30.0, guide?.lowMaxUsd)
            assertEquals(90.0, guide?.midMaxUsd)
        }
    }

    @Test
    fun withoutAPriceGuideNothingIsFiltered() {
        val llm = FakeLlm(listOf(candidate("Pantai Kuta", cost = 150.0), candidate("Tanah Lot", cost = 5.0)))
        llm.interestFailures = 100 // the guide cannot be had
        apiTest(ai = AiServices(llm, FakePlaces(BALI))) {
            assertEquals(2, names(post("/recommendations", request(trip(), price = "LOW"))).size)
        }
    }

    @Test
    fun searchUsesTheTripDestinationAndDestinationInterests() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val trip = trip("Ubud")
            post("/recommendations", request(trip, extra = listOf("Sawah terasering")))
            assertEquals(listOf("Sawah terasering"), llm.lastExtra)
            assertEquals("Ubud", llm.lastQuery?.destination)
            assertEquals("Ubud", places.lastDestination)
            assertEquals("2026-11-20", llm.lastQuery?.startDate)
        }
    }

    @Test
    fun aDestinationInTheRequestReplacesTheTripsForThatSearch() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val trip = trip("Yogyakarta")
            assertEquals(HttpStatusCode.OK, post("/recommendations", request(trip, destination = " Solo ")).status)
            assertEquals("Solo", llm.lastQuery?.destination)
            assertEquals("Solo", places.lastDestination)
            assertEquals("2026-11-20", llm.lastQuery?.startDate, "dates still come from the trip")
            assertEquals("Solo", llm.lastInterestDestination, "the price guide is the one of the searched destination")
            assertEquals("Yogyakarta", get("/trips/$trip").data().jsonObject.getValue("trip").jsonObject.str("destination"))
        }
    }

    @Test
    fun destinationAndDestinationInterestsArePartsOfTheCacheKey() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val trip = trip()
            post("/recommendations", request(trip))
            post("/recommendations", request(trip, destination = "Ubud"))
            post("/recommendations", request(trip, extra = listOf("Diving")))
            assertEquals(3, llm.calls.get())
            post("/recommendations", request(trip, extra = listOf(" diving ")))
            assertEquals(3, llm.calls.get(), "extra interests are normalized and order independent")
            post("/recommendations", request(trip, price = "LOW"))
            assertEquals(4, llm.calls.get(), "the price level is part of the key")
        }
    }

    @Test
    fun finishedTripViewerAndBadInputAreRejected() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val ended = trip(start = "2026-01-02", end = "2026-01-05")
            val response = post("/recommendations", request(ended))
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("TRIP_ENDED", response.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("tripId"))

            val open = trip()
            for ((body, field, code) in listOf(
                Triple(request(open, interests = emptyList()), "interests", "EMPTY"),
                Triple(request(open, extra = List(6) { "x$it" }), "extraInterests", "TOO_MANY"),
                Triple(request(open, extra = listOf("x".repeat(41))), "extraInterests", "TOO_LONG"),
                Triple(request(open, destination = "d".repeat(201)), "destination", "TOO_LONG"),
            )) {
                val r = post("/recommendations", body)
                assertEquals("VALIDATION_FAILED", r.errorCode())
                assertEquals(code, r.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str(field))
            }
            // an extra interest alone is enough
            assertEquals(HttpStatusCode.OK, post("/recommendations", request(open, interests = emptyList(), extra = listOf("Diving"))).status)

            val code = post("/trips/$open/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
            signUp()
            post("/invites/$code/accept")
            assertEquals(HttpStatusCode.Forbidden, post("/recommendations", request(open)).status)
            assertEquals(1, llm.calls.get(), "only the one valid search above reached the LLM")
        }
    }

    // ── GET /explore/suggestions ────────────────────────────────────────────

    private object FakeFx : FxRates {
        override fun toUsd(amountMinor: Long, currency: String) = if (currency == "IDR") amountMinor / 16_000.0 else null
    }

    @Test
    fun suggestionsForANewTripAreCachedPerDestinationAndMonthAndNeverCostCredits() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val first = get("/explore/suggestions?destination=Kyoto&month=2026-03")
            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(listOf("Diving", "Pura laut", "Sawah terasering"), first.data().jsonObject.getValue("interests").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(1, llm.interestCalls.get())

            signUp() // another user, same place and month
            get("/explore/suggestions?destination=%20kyoto%20&month=2026-03")
            assertEquals(1, llm.interestCalls.get(), "second user is served from the cache")

            get("/explore/suggestions?destination=Kyoto&month=2026-11")
            assertEquals(2, llm.interestCalls.get(), "the month is part of the key")

            val trip = trip()
            val credits = get("/credits").data().jsonObject
            assertEquals("30", credits.str("balance"), "suggestions never spend credits")
            assertEquals("3", credits.str("dailyFreeLeft"))
        }
    }

    @Test
    fun suggestionsFailureIsAnEmptyListAndIsNotCached() {
        val (llm, places) = fakes()
        llm.interestFailures = 2
        apiTest(ai = AiServices(llm, places)) {
            val failed = get("/explore/suggestions?destination=Lombok&month=2026-11")
            assertEquals(HttpStatusCode.OK, failed.status)
            assertEquals(0, failed.data().jsonObject.getValue("interests").jsonArray.size)
            assertEquals(2, llm.interestCalls.get(), "one retry")

            val again = get("/explore/suggestions?destination=Lombok&month=2026-11")
            assertEquals(3, again.data().jsonObject.getValue("interests").jsonArray.size)
        }
    }

    @Test
    fun suggestionsPreselectThePriceLevelFromTheTripBudget() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places), fx = FakeFx) {
            suspend fun trip(budget: Long?) = UUID.randomUUID().toString().also { post("/trips", tripJson(it, budget = budget)) }
            suspend fun level(budget: Long?) = get("/explore/suggestions?tripId=${trip(budget)}").data().jsonObject

            // 4 days, 1 member: 5.000.000 / 4 = 1.250.000 per day = about $78, between $30 and $90
            val medium = level(5_000_000)
            assertEquals("MEDIUM", medium.str("suggestedPriceLevel"))
            assertEquals("1250000", medium.str("budgetPerPersonPerDayMinor"))
            assertEquals("IDR", medium.str("currency"))
            assertEquals("LOW", level(1_000_000).str("suggestedPriceLevel"))
            assertEquals("HIGH", level(20_000_000).str("suggestedPriceLevel"))

            // Another destination changes the chips but not the budget maths, which stays the trip's.
            llm.lastInterestDestination = null
            val solo = get("/explore/suggestions?tripId=${trip(5_000_000)}&destination=Solo").data().jsonObject
            assertEquals("Solo", llm.lastInterestDestination)
            assertEquals("MEDIUM", solo.str("suggestedPriceLevel"))
            assertEquals("1250000", solo.str("budgetPerPersonPerDayMinor"))

            val noBudget = level(null)
            assertNull(noBudget["suggestedPriceLevel"])
            assertTrue(noBudget.getValue("interests").jsonArray.isNotEmpty())
        }
    }

    @Test
    fun suggestionsAccessAndValidation() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            val trip = trip()
            assertEquals("VALIDATION_FAILED", get("/explore/suggestions").errorCode())
            assertEquals("VALIDATION_FAILED", get("/explore/suggestions?destination=Kyoto&month=2026-13").errorCode())
            assertEquals("VALIDATION_FAILED", get("/explore/suggestions?tripId=nope").errorCode())
            assertEquals("VALIDATION_FAILED", get("/explore/suggestions?tripId=$trip&destination=${"d".repeat(201)}").errorCode())

            signUp()
            assertEquals("TRIP_NOT_FOUND", get("/explore/suggestions?tripId=$trip").errorCode())
            token = null
            assertEquals(HttpStatusCode.Unauthorized, get("/explore/suggestions?destination=Kyoto&month=2026-03").status)
        }
    }

    @Test
    fun newTripsWarmTheSuggestionsCache() {
        val (llm, places) = fakes()
        apiTest(ai = AiServices(llm, places)) {
            trip("Flores")
            var waited = 0
            while (llm.interestCalls.get() == 0 && waited++ < 50) kotlinx.coroutines.delay(100)
            assertEquals(1, llm.interestCalls.get(), "creating a trip asks for its destination interests in the background")
            get("/explore/suggestions?destination=Flores&month=2026-11")
            assertEquals(1, llm.interestCalls.get(), "and the form then reads them from the cache")
        }
    }

    @Test
    fun suggestionsHaveTheirOwnSoftLimitPerUser() {
        val (llm, places) = fakes()
        apiTest(config = TestDb.config().copy(suggestionsRateLimitPerHour = 3), ai = AiServices(llm, places)) {
            repeat(3) { assertEquals(HttpStatusCode.OK, get("/explore/suggestions?destination=Kyoto&month=2026-03").status) }
            val limited = get("/explore/suggestions?destination=Kyoto&month=2026-03")
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("RATE_LIMITED", limited.errorCode())
            signUp() // another user has their own budget
            assertEquals(HttpStatusCode.OK, get("/explore/suggestions?destination=Kyoto&month=2026-03").status)
        }
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
