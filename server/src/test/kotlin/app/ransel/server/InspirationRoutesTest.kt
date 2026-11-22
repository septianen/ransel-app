package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.InspirationCardsInput
import app.ransel.server.ai.InspirationQuestionInput
import app.ransel.server.ai.InterestSuggestions
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmInspirationCard
import app.ransel.server.ai.LlmInspirationCards
import app.ransel.server.ai.LlmInspirationChip
import app.ransel.server.ai.LlmInspirationOption
import app.ransel.server.ai.LlmInspirationQuestion
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlacesClient
import app.ransel.shared.model.InspirationSlot
import app.ransel.shared.model.Interest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Writes questions and cards on demand; counts calls so a test can prove an LLM call did or did not happen. */
private class InspirationLlm : LlmClient {
    override val modelName = "fake"
    var questionCalls = 0
    var cardCalls = 0
    var failSlots: Set<InspirationSlot> = emptySet()
    var cardsToFail = 0
    var cardsPerCall = 4
    var skillActivity = false
    /** Facts the fake "reads" from the hero text. */
    var facts: (InspirationQuestionInput) -> LlmInspirationQuestion? = { null }
    val inputs = mutableListOf<InspirationQuestionInput>()

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> = emptyList()
    override suspend fun suggestInterests(destination: String, month: String, language: String) = InterestSuggestions(emptyList(), 10.0, 30.0)

    override suspend fun inspirationQuestion(input: InspirationQuestionInput): LlmInspirationQuestion {
        questionCalls++
        inputs += input
        if (input.slot in failSlots && input.requirement == null) error("slow model")
        facts(input)?.let { return it }
        val options = if (input.slot == InspirationSlot.COMBINATION) {
            val (a, b) = input.interests.take(2)
            (1..3).map { LlmInspirationOption("k$it", "Combo $it", "foto + belanja", listOf(a.name, b.name)) }
        } else {
            (1..3).map { LlmInspirationOption("o$it", "Option ${input.slot} $it") }
        }
        return LlmInspirationQuestion(question = "Question ${input.slot} ${input.requirement ?: ""}".trim(), options = options, skillActivity = skillActivity)
    }

    override suspend fun inspirationCards(input: InspirationCardsInput): LlmInspirationCards {
        cardCalls++
        if (cardsToFail > 0) {
            cardsToFail--
            error("model down")
        }
        input.refresh?.let { return LlmInspirationCards(cards = listOf(card(it.activity, it.region, "fresh"))) }
        return LlmInspirationCards(
            profile = listOf(LlmInspirationChip("LEVEL", "Pemula"), LlmInspirationChip("BOGUS", "x")),
            cards = (1..cardsPerCall).map { card("Activity $it", "Region $cardCalls-$it") },
            surprise = card("Surprise", "Elsewhere $cardCalls"),
        )
    }

    private fun card(activity: String, region: String, why: String = "Because it fits") =
        LlmInspirationCard(activity = activity, region = region, why = why, costMinIdr = 900_000, costMaxIdr = 300_000, countryCode = "id")
}

private object PhotoPlaces : PlacesClient {
    override suspend fun findPlaceId(name: String, destination: String): String? = "place-${destination.hashCode()}"
    override suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails? = null
    override suspend fun photo(placeId: String): PlacePhoto? = null
}

private const val SESSIONS = "/explore/inspiration/sessions"
private const val SAVED = "/explore/inspiration/saved"

private suspend fun Api.start(interests: List<Interest>, text: String? = null, id: String = UUID.randomUUID().toString()) =
    post(SESSIONS, """{"id":"$id","interests":[${interests.joinToString { "\"$it\"" }}]${text?.let { ",\"text\":\"$it\"" } ?: ""}}""")

private suspend fun Api.answer(id: String, optionId: String) = post("$SESSIONS/$id/answers", """{"optionId":"$optionId"}""")

private val JsonObject.question get() = this["question"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
private val JsonObject.status get() = getValue("status").jsonPrimitive.content
private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull

/** Answers the first option of every question until the interview is complete; returns every question seen. */
private suspend fun Api.answerAll(id: String, first: JsonObject, pick: (JsonObject) -> String = { it.getValue("options").jsonArray[0].jsonObject.str("id")!! }): Pair<JsonObject, List<JsonObject>> {
    var session = first
    val seen = mutableListOf<JsonObject>()
    repeat(12) {
        val q = session.question ?: return session to seen
        seen += q
        val r = answer(id, pick(q))
        assertEquals(HttpStatusCode.OK, r.status, r.jsonBody().toString())
        session = r.data().jsonObject
    }
    return session to seen
}

private fun spentOn(action: String, userId: String) =
    TestDb.column("SELECT coalesce(sum(-delta), 0) FROM credit_ledger WHERE action = '$action' AND user_id = '$userId'").single().toInt()

class InspirationRoutesTest {
    @Test
    fun aFullSessionAsksMandatoryQuestionsAndChargesOnceOnSuccess() {
        val llm = InspirationLlm()
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val id = UUID.randomUUID().toString()
            val started = start(listOf(Interest.PHOTO, Interest.SHOPPING), id = id)
            assertEquals(HttpStatusCode.Created, started.status, started.jsonBody().toString())
            val first = started.data().jsonObject
            // two interests: slot 2 offers combinations that use both
            assertEquals("COMBINATION", first.question!!.str("slot"))
            first.question!!.getValue("options").jsonArray.forEach { assertEquals(2, it.jsonObject.getValue("interests").jsonArray.size) }
            assertFalse(first.question!!.getValue("canFinishEarly").jsonPrimitive.booleanOrNull!!)

            val (done, seen) = answerAll(id, first)
            assertEquals("READY", done.status)
            // shopping asks the budget (server rule), and the distance question has the fixed options
            assertTrue(seen.any { it.str("requirement") == "BUDGET" }, seen.toString())
            assertTrue(seen.any { it.str("slot") == "DISTANCE" && it.getValue("options").jsonArray.any { o -> o.jsonObject.str("id") == "same_city" } })
            assertNull(done["result"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }, "no cards before paying")

            val finished = post("$SESSIONS/$id/finish")
            assertEquals(HttpStatusCode.OK, finished.status, finished.jsonBody().toString())
            val body = finished.data().jsonObject
            assertEquals("CHARGED", body.status)
            val result = body.getValue("result").jsonObject
            val cards = result.getValue("cards").jsonArray.map { it.jsonObject }
            assertTrue(cards.size in 3..5)
            assertEquals(listOf("c1", "c2", "c3", "c4"), cards.map { it.str("id") })
            val surprise = result.getValue("surprise").jsonObject
            assertTrue(cards.none { it.str("region") == surprise.str("region") })
            // costs are a range in rupiah, min below max even when the model swapped them
            val cost = cards[0].getValue("costRange").jsonObject
            assertEquals(300_000, cost.getValue("minMinor").jsonPrimitive.int)
            assertEquals("IDR", cost.str("currency"))
            assertEquals("ID", cards[0].str("countryCode"))
            assertTrue(cards[0].str("photoUrl")!!.contains("sig="))
            assertEquals("Foto: Google", cards[0].str("photoAttribution"))
            // profile: interests first, the model's valid chips, unknown keys dropped
            assertEquals(listOf("INTEREST", "LEVEL"), result.getValue("profile").jsonArray.map { it.jsonObject.str("key") })
            assertNotNull(body["credits"]?.jsonObject)
            assertEquals(3, spentOn("INSPIRATION", userId))

            // a double tap: same session, no second charge, no second AI call
            val calls = llm.cardCalls
            assertEquals(HttpStatusCode.OK, post("$SESSIONS/$id/finish").status)
            assertEquals(3, spentOn("INSPIRATION", userId))
            assertEquals(calls, llm.cardCalls)

            // resume within 24 h shows the cards again
            val resumed = get("$SESSIONS/$id").data().jsonObject
            assertEquals(4, resumed.getValue("result").jsonObject.getValue("cards").jsonArray.size)
        }
    }

    @Test
    fun beginnerFreedivingAsksLevelAndBuddyBeforeResults() {
        val llm = InspirationLlm().apply { skillActivity = true }
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val id = UUID.randomUUID().toString()
            val first = start(listOf(Interest.ADVENTURE), "freediving pemula dekat Bali", id).data().jsonObject
            // one interest: no combination question
            assertEquals("DISTANCE", first.question!!.str("slot"))
            var session = answer(id, "domestic").data().jsonObject
            // DETAIL, read as a skill activity
            assertEquals("DETAIL", session.question!!.str("slot"))
            session = answer(id, "o1").data().jsonObject
            assertEquals("LEVEL", session.question!!.str("requirement"))
            assertFalse(session.question!!.getValue("canFinishEarly").jsonPrimitive.booleanOrNull!!)
            // "Sudah cukup" is refused while a mandatory question is left
            val early = post("$SESSIONS/$id/finish")
            assertEquals(HttpStatusCode.UnprocessableEntity, early.status)
            assertEquals("MANDATORY_PENDING", early.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("session"))
            session = answer(id, "o1").data().jsonObject
            assertEquals("BUDDY", session.question!!.str("requirement"))
            session = answer(id, "buddy_no").data().jsonObject
            val (done, _) = answerAll(id, session)
            assertEquals("READY", done.status)
            val result = post("$SESSIONS/$id/finish").data().jsonObject.getValue("result").jsonObject
            assertTrue(result.getValue("showFindBuddy").jsonPrimitive.booleanOrNull!!)
            result.getValue("cards").jsonArray.forEach {
                assertEquals("Ransel bukan operator. Pastikan lisensi dan buddy sebelum berangkat.", it.jsonObject.str("safetyLine"))
            }
        }
    }

    @Test
    fun sameCityAsksTheCityAndASlowStepFallsBackWithoutOvernightOptions() {
        val llm = InspirationLlm().apply { failSlots = setOf(InspirationSlot.CONTEXT) }
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val id = UUID.randomUUID().toString()
            start(listOf(Interest.FOOD), id = id)
            val city = answer(id, "same_city").data().jsonObject.question!!
            assertEquals("Kota mana?", city.str("text"))
            val afterCity = post("$SESSIONS/$id/answers", """{"otherText":"Medan"}""").data().jsonObject
            val (_, seen) = answerAll(id, afterCity)
            val context = seen.single { it.str("slot") == "CONTEXT" }
            assertEquals("FALLBACK", context.str("source"))
            assertEquals(listOf("half_day", "full_day"), context.getValue("options").jsonArray.map { it.jsonObject.str("id") })
            assertTrue(llm.inputs.last().city == "Medan")
        }
    }

    @Test
    fun freeTextSkipsWhatItAlreadySaysAndAbroadCardsCarryAVisaNote() {
        val llm = InspirationLlm().apply {
            facts = { input ->
                if (input.slot == InspirationSlot.DETAIL) null
                else LlmInspirationQuestion(distance = "ABROAD", city = "Bangkok", answeredSlots = listOf("DISTANCE", input.slot.name))
            }
        }
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val id = UUID.randomUUID().toString()
            val first = start(emptyList(), "street photography di Bangkok", id).data().jsonObject
            // interest and distance come from the text: no DISTANCE question, the first is DETAIL
            assertEquals("DETAIL", first.question!!.str("slot"))
            assertTrue(first.question!!.getValue("skippedSlots").jsonArray.any { it.jsonPrimitive.content == "DISTANCE" })
            val (done, seen) = answerAll(id, first)
            assertTrue(seen.none { it.str("slot") == "DISTANCE" })
            assertEquals("READY", done.status)
            post("$SESSIONS/$id/finish").data().jsonObject.getValue("result").jsonObject.getValue("cards").jsonArray.forEach {
                assertTrue(it.jsonObject.str("visaNote")!!.startsWith("Perkiraan AI"))
            }
        }
    }

    @Test
    fun failedCardsChargeNothingAndARetryWorks() {
        val llm = InspirationLlm().apply { cardsToFail = 2 }
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val id = UUID.randomUUID().toString()
            val (done, _) = answerAll(id, start(listOf(Interest.NATURE), id = id).data().jsonObject)
            assertEquals("READY", done.status)
            val failed = post("$SESSIONS/$id/finish")
            assertEquals(HttpStatusCode.ServiceUnavailable, failed.status)
            assertEquals("AI_UNAVAILABLE", failed.errorCode())
            assertEquals(0, spentOn("INSPIRATION", userId))
            assertEquals("READY", get("$SESSIONS/$id").data().jsonObject.status)
            assertEquals(HttpStatusCode.OK, post("$SESSIONS/$id/finish").status)
            assertEquals(3, spentOn("INSPIRATION", userId))
        }
    }

    @Test
    fun fewerThanThreeCardsIsRetriedOnce() {
        val llm = InspirationLlm().apply { cardsPerCall = 2 }
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val id = UUID.randomUUID().toString()
            answerAll(id, start(listOf(Interest.NATURE), id = id).data().jsonObject)
            assertEquals("AI_UNAVAILABLE", post("$SESSIONS/$id/finish").errorCode())
            assertEquals(2, llm.cardCalls)
            assertEquals(0, spentOn("INSPIRATION", userId))
        }
    }

    @Test
    fun creditsAreCheckedAtTheStartAndAgainAtTheEnd() {
        val llm = InspirationLlm()
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            get("/credits")
            fun drain() {
                TestDb.exec("UPDATE credit_wallets SET balance = 0 WHERE user_id = '$userId'")
                TestDb.exec(
                    "INSERT INTO credit_daily (user_id, usage_date, free_used) VALUES ('$userId', (now() AT TIME ZONE 'Asia/Jakarta')::date, 3) " +
                        "ON CONFLICT (user_id, usage_date) DO UPDATE SET free_used = 3",
                )
            }
            val id = UUID.randomUUID().toString()
            answerAll(id, start(listOf(Interest.CULTURE), id = id).data().jsonObject)
            // the balance went elsewhere mid-session: 402, the session stays READY with its cards
            drain()
            val poor = post("$SESSIONS/$id/finish")
            assertEquals(HttpStatusCode.PaymentRequired, poor.status)
            assertEquals("INSUFFICIENT_CREDITS", poor.errorCode())
            // the second check is after the cards are built, so they were kept
            val calls = llm.cardCalls
            TestDb.exec("UPDATE credit_wallets SET balance = 10 WHERE user_id = '$userId'")
            val paid = post("$SESSIONS/$id/finish")
            assertEquals(HttpStatusCode.OK, paid.status)
            assertEquals(calls, llm.cardCalls, "no second AI call after a top-up")
            assertEquals(3, spentOn("INSPIRATION", userId))
            // a new session with nothing left: 402 before any AI work
            drain()
            val before = llm.questionCalls
            assertEquals(HttpStatusCode.PaymentRequired, start(listOf(Interest.CULTURE)).status)
            assertEquals(before, llm.questionCalls)
        }
    }

    @Test
    fun theEleventhSessionInAnHourIs429WithRetryAfter() {
        apiTest(config = TestDb.config().copy(inspirationSessionsPerHour = 2), ai = AiServices(InspirationLlm(), PhotoPlaces)) {
            assertEquals(HttpStatusCode.Created, start(listOf(Interest.FOOD)).status)
            val repeatId = UUID.randomUUID().toString()
            assertEquals(HttpStatusCode.Created, start(listOf(Interest.FOOD), id = repeatId).status)
            // the same id again is the same session, not a new one
            assertEquals(HttpStatusCode.Created, start(listOf(Interest.FOOD), id = repeatId).status)
            val limited = start(listOf(Interest.FOOD))
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("RATE_LIMITED", limited.errorCode())
            assertTrue(limited.headers[HttpHeaders.RetryAfter]!!.toLong() in 1..3600)
            assertEquals(0, spentOn("INSPIRATION", userId))
            // an hour later it works again
            TestDb.exec("UPDATE inspiration_sessions SET created_at = created_at - interval '61 minutes'")
            assertEquals(HttpStatusCode.Created, start(listOf(Interest.FOOD)).status)
        }
    }

    @Test
    fun askAgainAndChipEditsAreFreeAFewTimesThenCostTheNextBatchPrice() {
        val llm = InspirationLlm()
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            get("/credits")
            TestDb.exec("UPDATE credit_wallets SET balance = 100 WHERE user_id = '$userId'")
            val id = UUID.randomUUID().toString()
            answerAll(id, start(listOf(Interest.NATURE), id = id).data().jsonObject)
            val paid = post("$SESSIONS/$id/finish").data().jsonObject
            assertEquals(1, paid.getValue("askAgainFreeLeft").jsonPrimitive.int)
            assertEquals(3, paid.getValue("chipEditsFreeLeft").jsonPrimitive.int)

            // "Tanya lagi": one question, its answer appends cards with new regions
            val asked = post("$SESSIONS/$id/ask-again").data().jsonObject
            assertEquals("ACTIVE", asked.status)
            assertEquals(4, asked.getValue("result").jsonObject.getValue("cards").jsonArray.size, "the old cards stay visible")
            val more = answer(id, asked.question!!.getValue("options").jsonArray[0].jsonObject.str("id")!!).data().jsonObject
            val cards = more.getValue("result").jsonObject.getValue("cards").jsonArray.map { it.jsonObject }
            assertEquals(8, cards.size)
            assertEquals(cards.size, cards.map { it.str("region") }.toSet().size)
            assertEquals(0, more.getValue("askAgainFreeLeft").jsonPrimitive.int)
            assertEquals(0, spentOn("NEXT_BATCH", userId))
            // the second "Tanya lagi" costs the next-batch price
            val second = post("$SESSIONS/$id/ask-again").data().jsonObject
            answer(id, second.question!!.getValue("options").jsonArray[0].jsonObject.str("id")!!)
            assertEquals(1, spentOn("NEXT_BATCH", userId))

            repeat(3) {
                val edited = patch("$SESSIONS/$id/profile", """{"key":"DURATION","value":"Sampai 3 hari"}""")
                assertEquals(HttpStatusCode.OK, edited.status, edited.jsonBody().toString())
            }
            assertEquals(1, spentOn("NEXT_BATCH", userId))
            val fourth = patch("$SESSIONS/$id/profile", """{"key":"DURATION","value":"4–7 hari"}""").data().jsonObject
            assertEquals(2, spentOn("NEXT_BATCH", userId))
            val profile = fourth.getValue("result").jsonObject.getValue("profile").jsonArray.map { it.jsonObject }
            assertEquals("4–7 hari", profile.single { it.str("key") == "DURATION" }.str("value"))
            assertEquals(3, spentOn("INSPIRATION", userId))
        }
    }

    @Test
    fun aChipEditThatSwitchesShoppingOnAsksTheBudgetFirst() {
        apiTest(ai = AiServices(InspirationLlm(), PhotoPlaces)) {
            val id = UUID.randomUUID().toString()
            answerAll(id, start(listOf(Interest.NATURE), id = id).data().jsonObject)
            post("$SESSIONS/$id/finish")
            val edited = patch("$SESSIONS/$id/profile", """{"key":"INTEREST","value":"Belanja"}""").data().jsonObject
            assertEquals("BUDGET", edited.question!!.str("requirement"))
            val after = answer(id, "budget_mid").data().jsonObject
            assertEquals("CHARGED", after.status)
            assertNull(after.question)
        }
    }

    @Test
    fun savedIdeasArePerAccountCappedAt30WithUndo() {
        val llm = InspirationLlm()
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val sessionId = UUID.randomUUID().toString()
            answerAll(sessionId, start(listOf(Interest.NATURE), id = sessionId).data().jsonObject)
            post("$SESSIONS/$sessionId/finish")
            // a session without a charge cannot be saved from
            val saveId = UUID.randomUUID().toString()
            val saved = post(SAVED, """{"id":"$saveId","sessionId":"$sessionId","cardId":"c1"}""")
            assertEquals(HttpStatusCode.Created, saved.status, saved.jsonBody().toString())
            assertNull(saved.data().jsonObject.str("removedOldestId"))
            // the same id again does not save twice
            post(SAVED, """{"id":"$saveId","sessionId":"$sessionId","cardId":"c1"}""")
            assertEquals(1, get(SAVED).data().jsonObject.getValue("ideas").jsonArray.size)
            assertEquals(HttpStatusCode.NotFound, post(SAVED, """{"id":"${UUID.randomUUID()}","sessionId":"$sessionId","cardId":"c99"}""").status)

            // 29 more (straight in the database), the oldest first
            repeat(29) { i ->
                TestDb.exec(
                    "INSERT INTO saved_ideas (id, user_id, session_id, card_id, card, profile, language, saved_at, refreshed_at) VALUES " +
                        "('${UUID.randomUUID()}', '$userId', '${UUID.randomUUID()}', 'c1', " +
                        "'{\"card\":{\"id\":\"c1\",\"activity\":\"Old $i\",\"region\":\"R$i\",\"why\":\"w\"}}', '[]', 'id', " +
                        "now() - interval '${100 - i} minutes', now() - interval '${100 - i} minutes')",
                )
            }
            assertEquals(30, get(SAVED).data().jsonObject.getValue("ideas").jsonArray.size)
            val oldestId = TestDb.column("SELECT id FROM saved_ideas WHERE user_id = '$userId' ORDER BY saved_at LIMIT 1").single()

            val thirtyFirst = UUID.randomUUID().toString()
            val pushed = post(SAVED, """{"id":"$thirtyFirst","sessionId":"$sessionId","cardId":"c2"}""").data().jsonObject
            assertEquals(oldestId, pushed.str("removedOldestId"))
            val ids = get(SAVED).data().jsonObject.getValue("ideas").jsonArray.map { it.jsonObject.str("id") }
            assertEquals(30, ids.size)
            assertFalse(oldestId in ids)
            assertEquals(thirtyFirst, ids.first(), "newest first")

            // "Urungkan": the new save goes, the oldest comes back, still 30
            assertEquals(HttpStatusCode.NoContent, post("$SAVED/$thirtyFirst/undo").status)
            val restored = get(SAVED).data().jsonObject.getValue("ideas").jsonArray.map { it.jsonObject.str("id") }
            assertEquals(30, restored.size)
            assertTrue(oldestId in restored)
            assertFalse(thirtyFirst in restored)

            // another account sees nothing and cannot save from this session
            val owner = session()
            signUp()
            assertEquals(0, get(SAVED).data().jsonObject.getValue("ideas").jsonArray.size)
            assertEquals(HttpStatusCode.NotFound, post(SAVED, """{"id":"${UUID.randomUUID()}","sessionId":"$sessionId","cardId":"c1"}""").status)
            assertEquals(HttpStatusCode.NotFound, get("$SESSIONS/$sessionId").status)
            assertEquals(HttpStatusCode.NotFound, delete("$SAVED/$saveId").status)
            use(owner)
            assertEquals(HttpStatusCode.NoContent, delete("$SAVED/$saveId").status)
            assertEquals(29, get(SAVED).data().jsonObject.getValue("ideas").jsonArray.size)
        }
    }

    @Test
    fun backShowsThePreviousQuestionAgainWithoutAnAiCall() {
        val llm = InspirationLlm()
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val id = UUID.randomUUID().toString()
            val first = start(listOf(Interest.NATURE), id = id).data().jsonObject
            assertEquals("DISTANCE", first.question!!.str("slot"))
            // nothing before the first question
            assertEquals(HttpStatusCode.Conflict, post("$SESSIONS/$id/back").status)

            val detail = answer(id, "abroad").data().jsonObject
            assertEquals("DETAIL", detail.question!!.str("slot"))
            val calls = llm.questionCalls
            val backed = post("$SESSIONS/$id/back")
            assertEquals(HttpStatusCode.OK, backed.status, backed.jsonBody().toString())
            val again = backed.data().jsonObject
            assertEquals("ACTIVE", again.status)
            assertEquals(first.question!!.str("text"), again.question!!.str("text"))
            assertEquals(0, again.question!!.getValue("stepsDone").jsonPrimitive.int)
            assertEquals(calls, llm.questionCalls, "back makes no AI call")
            // the earlier answer is gone: a different answer takes another path
            val sameCity = answer(id, "same_city").data().jsonObject
            assertEquals("Kota mana?", sameCity.question!!.str("text"))

            // once the cards are paid for there is no way back
            val (done, _) = answerAll(id, sameCity)
            assertEquals("READY", done.status)
            post("$SESSIONS/$id/finish")
            assertEquals(HttpStatusCode.Conflict, post("$SESSIONS/$id/back").status)
            assertEquals(3, spentOn("INSPIRATION", userId))
        }
    }

    @Test
    fun aiTextsStartWithACapitalAndThePromptUsesTheAppLabels() {
        val llm = InspirationLlm().apply {
            facts = { input ->
                if (input.slot != InspirationSlot.DETAIL) null
                else LlmInspirationQuestion(
                    question = "kombinasi pengalaman apa yang kamu mau?",
                    options = listOf(LlmInspirationOption("a", "petualangan alam", "satwa & alam"), LlmInspirationOption("b", "B"), LlmInspirationOption("c", "C")),
                )
            }
        }
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val id = UUID.randomUUID().toString()
            start(listOf(Interest.NATURE), id = id)
            val detail = answer(id, "domestic").data().jsonObject.question!!
            assertEquals("Kombinasi pengalaman apa yang kamu mau?", detail.str("text"))
            val option = detail.getValue("options").jsonArray[0].jsonObject
            assertEquals("Petualangan alam", option.str("title"))
            assertEquals("Satwa & alam", option.str("subtitle"))
        }
        val prompt = app.ransel.server.ai.InspirationPrompt.questionUser(
            InspirationQuestionInput("id", listOf(Interest.NATURE, Interest.RELAX), null, emptyList(), InspirationSlot.COMBINATION),
        )
        assertTrue("Satwa & alam" in prompt && "Healing" in prompt, prompt)
        assertTrue("huruf kecil kalimat biasa" !in prompt)
    }

    @Test
    fun aRemovedIdeaCanBeRestoredWithin24Hours() {
        apiTest(ai = AiServices(InspirationLlm(), PhotoPlaces)) {
            val sessionId = UUID.randomUUID().toString()
            answerAll(sessionId, start(listOf(Interest.NATURE), id = sessionId).data().jsonObject)
            post("$SESSIONS/$sessionId/finish")
            val first = UUID.randomUUID().toString()
            val second = UUID.randomUUID().toString()
            post(SAVED, """{"id":"$first","sessionId":"$sessionId","cardId":"c1"}""")
            post(SAVED, """{"id":"$second","sessionId":"$sessionId","cardId":"c2"}""")
            assertEquals(HttpStatusCode.NoContent, delete("$SAVED/$first").status)
            assertEquals(listOf(second), get(SAVED).data().jsonObject.getValue("ideas").jsonArray.map { it.jsonObject.str("id") })
            // a second delete of the same idea is 404: it is no longer in the list
            assertEquals(HttpStatusCode.NotFound, delete("$SAVED/$first").status)

            assertEquals(HttpStatusCode.NoContent, post("$SAVED/$first/restore").status)
            // back in its old place: older than the second save, so last in "newest first"
            assertEquals(listOf(second, first), get(SAVED).data().jsonObject.getValue("ideas").jsonArray.map { it.jsonObject.str("id") })
            // nothing to restore any more
            assertEquals(HttpStatusCode.NotFound, post("$SAVED/$first/restore").status)

            // the same card saved again meanwhile: 409
            delete("$SAVED/$first")
            post(SAVED, """{"id":"${UUID.randomUUID()}","sessionId":"$sessionId","cardId":"c1"}""")
            assertEquals(HttpStatusCode.Conflict, post("$SAVED/$first/restore").status)

            // after 24 h it is gone
            delete("$SAVED/$second")
            TestDb.exec("UPDATE saved_ideas SET removed_at = now() - interval '25 hours' WHERE id = '$second'")
            assertEquals(HttpStatusCode.NotFound, post("$SAVED/$second/restore").status)

            // another account cannot restore it
            delete("$SAVED/${get(SAVED).data().jsonObject.getValue("ideas").jsonArray.first().jsonObject.str("id")}")
            val owner = session()
            signUp()
            assertEquals(HttpStatusCode.NotFound, post("$SAVED/$first/restore").status)
            use(owner)
        }
    }

    @Test
    fun perbaruiWorksOncePerCardPer24Hours() {
        val llm = InspirationLlm()
        apiTest(ai = AiServices(llm, PhotoPlaces)) {
            val sessionId = UUID.randomUUID().toString()
            answerAll(sessionId, start(listOf(Interest.NATURE), id = sessionId).data().jsonObject)
            post("$SESSIONS/$sessionId/finish")
            val ideaId = UUID.randomUUID().toString()
            post(SAVED, """{"id":"$ideaId","sessionId":"$sessionId","cardId":"c1"}""")
            val before = get(SAVED).data().jsonObject.getValue("ideas").jsonArray.single().jsonObject
            assertNull(before.str("canRefreshAt"))

            val refreshed = post("$SAVED/$ideaId/refresh")
            assertEquals(HttpStatusCode.OK, refreshed.status, refreshed.jsonBody().toString())
            val idea = refreshed.data().jsonObject
            assertEquals("Fresh", idea.getValue("card").jsonObject.str("why"), "AI texts start with a capital")
            assertEquals("c1", idea.getValue("card").jsonObject.str("id"))
            assertNotNull(idea.str("canRefreshAt"))

            val calls = llm.cardCalls
            val again = post("$SAVED/$ideaId/refresh")
            assertEquals(HttpStatusCode.TooManyRequests, again.status)
            assertEquals("RATE_LIMITED", again.errorCode())
            assertNotNull(again.headers[HttpHeaders.RetryAfter])
            assertEquals(calls, llm.cardCalls, "a refresh within 24 h makes no AI call")
            assertEquals(0, spentOn("INSPIRATION", userId) - 3, "refresh is free")

            TestDb.exec("UPDATE saved_ideas SET refresh_requested_at = now() - interval '25 hours'")
            assertEquals(HttpStatusCode.OK, post("$SAVED/$ideaId/refresh").status)
        }
    }

    @Test
    fun guardsAndValidation() {
        apiTest(ai = AiServices(InspirationLlm(), PhotoPlaces)) {
            assertEquals("VALIDATION_FAILED", start(emptyList()).errorCode())
            val six = start(listOf(Interest.NATURE, Interest.FOOD, Interest.CULTURE, Interest.SHOPPING, Interest.ADVENTURE, Interest.PHOTO))
            assertEquals(HttpStatusCode.UnprocessableEntity, six.status)
            assertEquals("TOO_MANY", six.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("interests"))
            val id = UUID.randomUUID().toString()
            start(listOf(Interest.FOOD), id = id)
            assertEquals(HttpStatusCode.UnprocessableEntity, answer(id, "nope").status)
            assertEquals(HttpStatusCode.UnprocessableEntity, post("$SESSIONS/$id/answers", """{"otherText":"${"x".repeat(81)}"}""").status)
            // "Sudah cukup" before step 3
            assertEquals("TOO_EARLY", post("$SESSIONS/$id/finish").jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("session"))
            // past 24 h the session is gone
            TestDb.exec("UPDATE inspiration_sessions SET expires_at = now() - interval '1 minute'")
            assertEquals(HttpStatusCode.NotFound, get("$SESSIONS/$id").status)
            assertEquals(HttpStatusCode.NoContent, post("/explore/inspiration/buddy", """{"interest":"ADVENTURE"}""").status)

            signUp(verified = false)
            assertEquals("EMAIL_NOT_VERIFIED", start(listOf(Interest.FOOD)).errorCode())
        }
    }

    @Test
    fun withoutAnLlmTheSessionAnswersAiUnavailable() {
        apiTest {
            val r = start(listOf(Interest.NATURE, Interest.FOOD))
            assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
            assertEquals("AI_UNAVAILABLE", r.errorCode())
            assertEquals(0, spentOn("INSPIRATION", userId))
        }
    }
}
