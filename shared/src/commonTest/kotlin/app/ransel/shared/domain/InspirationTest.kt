package app.ransel.shared.domain

import app.ransel.shared.dto.CreditCostsDto
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.InspirationOptionDto
import app.ransel.shared.dto.InspirationQuestionDto
import app.ransel.shared.dto.InspirationResultDto
import app.ransel.shared.dto.InspirationSessionDto
import app.ransel.shared.dto.StartInspirationRequest
import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.InspirationDistance
import app.ransel.shared.model.InspirationQuestionSource
import app.ransel.shared.model.InspirationRefine
import app.ransel.shared.model.InspirationRequirement
import app.ransel.shared.model.InspirationSlot
import app.ransel.shared.model.InspirationStatus
import app.ransel.shared.model.Interest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class InspirationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val rules = DEFAULT_INSPIRATION_RULES
    private val now = Instant.parse("2026-11-21T10:00:00Z")

    // ── start validation ────────────────────────────────────────────────────

    @Test fun fiveInterestsAreValidAndASixthIsTooMany() {
        val five = listOf(Interest.PHOTO, Interest.FOOD, Interest.CULTURE, Interest.NATURE, Interest.RELAX)
        assertTrue(validateStartInspiration(five, null).isEmpty())
        assertEquals(mapOf("interests" to "TOO_MANY"), validateStartInspiration(five + Interest.SHOPPING, null))
    }

    @Test fun repeatedInterestsCountOnce() {
        val repeated = List(6) { Interest.PHOTO }
        assertTrue(validateStartInspiration(repeated, null).isEmpty())
    }

    @Test fun heroTextLimitIs120AfterTrim() {
        assertTrue(validateStartInspiration(emptyList(), "a".repeat(120)).isEmpty())
        assertTrue(validateStartInspiration(emptyList(), "  " + "a".repeat(120) + "  ").isEmpty())
        assertEquals(mapOf("text" to "TOO_LONG"), validateStartInspiration(emptyList(), "a".repeat(121)))
    }

    @Test fun anAnswerIsOneOptionOrOneText() {
        assertEquals(mapOf("answer" to "REQUIRED"), validateInspirationAnswer(null, null))
        assertEquals(mapOf("answer" to "REQUIRED"), validateInspirationAnswer(" ", "  "))
        assertEquals(mapOf("answer" to "TOO_MANY"), validateInspirationAnswer("a", "b"))
        assertTrue(validateInspirationAnswer("a", null).isEmpty())
        assertTrue(validateInspirationAnswer(null, "x".repeat(80)).isEmpty())
        assertEquals(mapOf("otherText" to "TOO_LONG"), validateInspirationAnswer(null, "x".repeat(81)))
    }

    // ── slots ───────────────────────────────────────────────────────────────

    @Test fun slotsComeInFixedOrder() {
        assertEquals(InspirationSlot.INTEREST, nextInspirationSlot(emptySet()))
        assertEquals(InspirationSlot.COMBINATION, nextInspirationSlot(setOf(InspirationSlot.INTEREST)))
        assertEquals(
            InspirationSlot.DETAIL,
            nextInspirationSlot(setOf(InspirationSlot.INTEREST, InspirationSlot.COMBINATION, InspirationSlot.DISTANCE)),
        )
        assertNull(nextInspirationSlot(InspirationSlot.entries.toSet()))
    }

    @Test fun answeredSlotsAreSkippedEvenOutOfOrder() {
        // "street photography di Bangkok": interest and distance are already known, so the first question is the combination.
        val done = setOf(InspirationSlot.INTEREST, InspirationSlot.DISTANCE)
        assertEquals(InspirationSlot.COMBINATION, nextInspirationSlot(done))
        assertEquals(InspirationSlot.DETAIL, nextInspirationSlot(done + InspirationSlot.COMBINATION))
    }

    @Test fun oneInterestSkipsTheCombinationSlot() {
        assertTrue(skipsCombination(listOf(Interest.PHOTO)))
        assertTrue(skipsCombination(emptyList()))
        assertFalse(skipsCombination(listOf(Interest.PHOTO, Interest.SHOPPING)))
    }

    @Test fun aSessionAsksAtMostSixQuestions() {
        assertTrue(canAskAnotherInspirationQuestion(5))
        assertFalse(canAskAnotherInspirationQuestion(6))
    }

    // ── mandatory rules ─────────────────────────────────────────────────────

    @Test fun beginnerFreedivingAsksLevelAndBuddyBeforeResults() {
        val facts = InspirationFacts(listOf(Interest.ADVENTURE), skillActivity = true)
        assertEquals(
            listOf(InspirationRequirement.LEVEL, InspirationRequirement.BUDDY),
            pendingInspirationQuestions(rules, facts, answered = emptySet()),
        )
        assertEquals(
            listOf(InspirationRequirement.BUDDY),
            pendingInspirationQuestions(rules, facts, answered = setOf(InspirationRequirement.LEVEL)),
        )
        assertTrue(pendingInspirationQuestions(rules, facts, answered = setOf(InspirationRequirement.LEVEL, InspirationRequirement.BUDDY)).isEmpty())
    }

    @Test fun shoppingAsksTheBudget() {
        val facts = InspirationFacts(listOf(Interest.PHOTO, Interest.SHOPPING))
        assertEquals(listOf(InspirationRequirement.BUDGET), pendingInspirationQuestions(rules, facts, emptySet()))
    }

    @Test fun abroadNeedsAVisaNoteButNoQuestion() {
        val abroad = InspirationFacts(listOf(Interest.PHOTO), distance = InspirationDistance.ABROAD)
        assertTrue(pendingInspirationQuestions(rules, abroad, emptySet()).isEmpty())
        assertTrue(needsInspirationVisaNote(rules, abroad))
        assertFalse(needsInspirationVisaNote(rules, abroad.copy(distance = InspirationDistance.SAME_CITY)))
    }

    @Test fun noRuleNoPendingQuestion() {
        val facts = InspirationFacts(listOf(Interest.FOOD), distance = InspirationDistance.SAME_CITY)
        assertTrue(pendingInspirationQuestions(rules, facts, emptySet()).isEmpty())
    }

    @Test fun anAddedServerRuleWorksWithoutAnAppRelease() {
        val extra = rules + InspirationRule(
            app.ransel.shared.model.InspirationRuleTrigger.SHOPPING,
            listOf(InspirationRequirement.LEVEL),
        )
        val facts = InspirationFacts(listOf(Interest.SHOPPING))
        assertEquals(
            listOf(InspirationRequirement.LEVEL, InspirationRequirement.BUDGET),
            pendingInspirationQuestions(extra, facts, emptySet()),
        )
    }

    @Test fun earlyFinishNeedsStepThreeAndNoMandatoryQuestionLeft() {
        assertFalse(canFinishInspirationEarly(step = 2, pending = emptyList()))
        assertTrue(canFinishInspirationEarly(step = 3, pending = emptyList()))
        assertFalse(canFinishInspirationEarly(step = 4, pending = listOf(InspirationRequirement.LEVEL)))
    }

    // ── combination options ─────────────────────────────────────────────────

    private fun option(id: String, vararg interests: Interest) = InspirationOptionDto(id, id, interests = interests.toList())

    @Test fun combinationOptionsWithOneInterestAreDropped() {
        val chosen = listOf(Interest.PHOTO, Interest.SHOPPING, Interest.FOOD)
        val options = listOf(
            option("a", Interest.PHOTO, Interest.SHOPPING),
            option("b", Interest.PHOTO),
            option("c", Interest.PHOTO, Interest.FOOD),
            option("d", Interest.SHOPPING, Interest.FOOD),
        )
        assertEquals(listOf("a", "c", "d"), checkCombinationOptions(options, chosen, "id").map { it.id })
    }

    @Test fun combinationOptionsOutsideTheChosenInterestsDoNotCount() {
        val chosen = listOf(Interest.PHOTO, Interest.SHOPPING)
        val options = listOf(option("a", Interest.PHOTO, Interest.NATURE), option("b", Interest.FOOD, Interest.CULTURE))
        // Nothing combines two chosen interests: only "Fokus" options remain.
        assertEquals(listOf("focus:PHOTO", "focus:SHOPPING"), checkCombinationOptions(options, chosen, "id").map { it.id })
    }

    @Test fun fewerThanThreeValidOptionsAddFokusOptionsAfterThem() {
        val chosen = listOf(Interest.PHOTO, Interest.SHOPPING)
        val result = checkCombinationOptions(listOf(option("combo", Interest.PHOTO, Interest.SHOPPING)), chosen, "id")
        assertEquals(listOf("combo", "focus:PHOTO", "focus:SHOPPING"), result.map { it.id })
        assertEquals("Fokus Fotografi", result[1].title)
        assertEquals("Focus on Photography", checkCombinationOptions(emptyList(), chosen, "en").first().title)
    }

    @Test fun combinationOptionsAreCappedAtFive() {
        val chosen = Interest.entries.take(5)
        val options = List(8) { option("o$it", chosen[0], chosen[1]) }
        assertEquals(5, checkCombinationOptions(options, chosen, "id").size)
    }

    // ── labels ──────────────────────────────────────────────────────────────

    @Test fun everyInterestHasOneLabelPerLanguage() {
        Interest.entries.forEach {
            assertTrue(inspirationInterestLabel(it, "id").isNotBlank())
            assertTrue(inspirationInterestLabel(it, "en").isNotBlank())
        }
        assertEquals("Musik & event", inspirationInterestLabel(Interest.NIGHTLIFE, "id"))
        assertEquals("Music & events", inspirationInterestLabel(Interest.NIGHTLIFE, "en"))
        assertEquals("Fotografi", inspirationInterestLabel(Interest.PHOTO, "fr")) // anything but en is Indonesian
    }

    // ── free limits and credits ─────────────────────────────────────────────

    @Test fun oneAskAgainIsFreeThenTheNextBatchPriceApplies() {
        assertNull(inspirationRefineCharge(InspirationRefine.ASK_AGAIN, used = 0))
        assertEquals(CreditAction.NEXT_BATCH, inspirationRefineCharge(InspirationRefine.ASK_AGAIN, used = 1))
        assertEquals(1, inspirationFreeRefinesLeft(InspirationRefine.ASK_AGAIN, 0))
        assertEquals(0, inspirationFreeRefinesLeft(InspirationRefine.ASK_AGAIN, 5))
    }

    @Test fun threeChipEditsAreFreeAndTheFourthIsCharged() {
        for (used in 0..2) assertNull(inspirationRefineCharge(InspirationRefine.CHIP_EDIT, used))
        assertEquals(CreditAction.NEXT_BATCH, inspirationRefineCharge(InspirationRefine.CHIP_EDIT, used = 3))
        assertEquals(3, inspirationFreeRefinesLeft(InspirationRefine.CHIP_EDIT, 0))
        assertEquals(1, inspirationFreeRefinesLeft(InspirationRefine.CHIP_EDIT, 2))
    }

    @Test fun theFreeAllowanceStillPaysFirstForASession() {
        // The same rule as every other Explore charge (PRD "Credit"): a deliberate decision, not a side effect.
        assertEquals(CreditSpend(fromFree = 3, fromWallet = 0), planCreditSpend(cost = 3, dailyFreeLeft = 3, balance = 0))
        assertNull(planCreditSpend(cost = 3, dailyFreeLeft = 1, balance = 1))
    }

    // ── sessions and saved ideas ────────────────────────────────────────────

    @Test fun aSessionResumesFor24Hours() {
        assertEquals(now + 24.hours, inspirationSessionExpiresAt(now))
    }

    @Test fun theThirtyFirstSaveEvictsTheOldest() {
        assertFalse(savedIdeaEvictsOldest(29))
        assertTrue(savedIdeaEvictsOldest(30))
    }

    @Test fun aCardIsStaleAfterSixtyDays() {
        assertFalse(isSavedIdeaStale(now - (60 * 24).hours, now))
        assertTrue(isSavedIdeaStale(now - (60 * 24 + 1).hours, now))
    }

    @Test fun refreshWorksOncePerCardPer24Hours() {
        assertTrue(canRefreshSavedIdea(null, now))
        assertNull(savedIdeaRefreshAvailableAt(null))
        val last = now - 23.hours
        assertFalse(canRefreshSavedIdea(last, now))
        assertEquals(last + 24.hours, savedIdeaRefreshAvailableAt(last))
        assertTrue(canRefreshSavedIdea(now - 24.hours, now))
    }

    // ── fallback questions ──────────────────────────────────────────────────

    @Test fun distanceFallbackHasTheFourOptionsAndNeverCostsAnything() {
        val q = assertNotNull(fallbackInspirationQuestion(InspirationSlot.DISTANCE, listOf(Interest.PHOTO), null, "id"))
        assertEquals(InspirationQuestionSource.FALLBACK, q.source)
        assertEquals(listOf("same_city", "domestic", "abroad", "any"), q.options.map { it.id })
        assertEquals(listOf("Dalam kota", "Luar kota (Indonesia)", "Luar negeri"), q.options.take(3).map { it.title })
        assertTrue(q.allowOther)
        q.options.forEach { assertNotNull(inspirationDistanceFromOptionId(it.id)) }
        assertNull(inspirationDistanceFromOptionId("nope"))
    }

    @Test fun contextFallbackFollowsTheDistance() {
        val same = assertNotNull(fallbackInspirationQuestion(InspirationSlot.CONTEXT, emptyList(), InspirationDistance.SAME_CITY, "id"))
        assertEquals(listOf("Setengah hari", "Sehari"), same.options.map { it.title })
        val abroad = assertNotNull(fallbackInspirationQuestion(InspirationSlot.CONTEXT, emptyList(), InspirationDistance.ABROAD, "en"))
        assertTrue(abroad.options.size in InspirationLimits.MIN_OPTIONS..InspirationLimits.MAX_OPTIONS)
    }

    @Test fun combinationFallbackNeedsTwoInterestsAndEveryOptionCombinesTwo() {
        assertNull(fallbackInspirationQuestion(InspirationSlot.COMBINATION, listOf(Interest.PHOTO), null, "id"))
        val q = assertNotNull(
            fallbackInspirationQuestion(
                InspirationSlot.COMBINATION, listOf(Interest.PHOTO, Interest.SHOPPING, Interest.FOOD), null, "id",
            ),
        )
        assertTrue(q.options.size in InspirationLimits.MIN_OPTIONS..InspirationLimits.MAX_OPTIONS)
        assertEquals("Fotografi + Belanja", q.options.first().title)
        // With two interests there is one pair; "Fokus <minat>" options fill the question up to three.
        val two = assertNotNull(fallbackInspirationQuestion(InspirationSlot.COMBINATION, listOf(Interest.PHOTO, Interest.SHOPPING), null, "id"))
        assertEquals(listOf("combo:PHOTO+SHOPPING", "focus:PHOTO", "focus:SHOPPING"), two.options.map { it.id })
        // The fallback passes the same check as an AI answer.
        assertEquals(q.options, checkCombinationOptions(q.options, listOf(Interest.PHOTO, Interest.SHOPPING, Interest.FOOD), "id"))
    }

    @Test fun slotsWithoutContentHaveNoFallback() {
        listOf(InspirationSlot.INTEREST, InspirationSlot.DETAIL, InspirationSlot.OPTIONAL).forEach {
            assertNull(fallbackInspirationQuestion(it, listOf(Interest.PHOTO, Interest.FOOD), null, "id"))
        }
    }

    // ── contract (wire shape) ───────────────────────────────────────────────

    @Test fun creditCostsWithoutTheNewFieldStillParse() {
        val old = json.decodeFromString<CreditCostsDto>("""{"search":3,"nextBatch":1,"placeDetail":0,"receipt":1}""")
        assertEquals(0, old.inspiration)
    }

    @Test fun theNewCreditActionSerializesAsItsName() {
        assertEquals("\"INSPIRATION\"", json.encodeToString(CreditAction.serializer(), CreditAction.INSPIRATION))
    }

    @Test fun startRequestNeedsOnlyTheId() {
        val r = json.decodeFromString<StartInspirationRequest>("""{"id":"7b3c"}""")
        assertTrue(r.interests.isEmpty())
        assertNull(r.text)
    }

    @Test fun aSessionWithAResultRoundTrips() {
        val card = InspirationCardDto(
            id = "c1", activity = "Memberi makan orang utan", region = "Tanjung Puting, Kalteng", why = "Cocok untuk pencinta satwa.",
        )
        val session = InspirationSessionDto(
            id = "s1", status = InspirationStatus.CHARGED,
            result = InspirationResultDto(cards = listOf(card), surprise = card.copy(id = "c2", surprise = true), createdAt = now),
            expiresAt = now + 24.hours,
        )
        val text = json.encodeToString(InspirationSessionDto.serializer(), session)
        assertEquals(session, json.decodeFromString<InspirationSessionDto>(text))
        // A newer server may add fields; this client ignores them.
        val extra = text.replace("\"id\":\"s1\"", "\"id\":\"s1\",\"futureField\":1")
        assertEquals(session, json.decodeFromString<InspirationSessionDto>(extra))
    }

    @Test fun aQuestionDefaultsToAnAiQuestionWithOther() {
        val q = InspirationQuestionDto(InspirationSlot.DETAIL, "Pilih satu", listOf(InspirationOptionDto("a", "A")))
        assertEquals(InspirationQuestionSource.LLM, q.source)
        assertTrue(q.allowOther)
        assertFalse(q.canFinishEarly)
    }
}
