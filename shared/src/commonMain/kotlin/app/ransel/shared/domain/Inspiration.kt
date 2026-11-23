package app.ransel.shared.domain

import app.ransel.shared.dto.InspirationOptionDto
import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.InspirationRefine
import app.ransel.shared.model.InspirationRequirement
import app.ransel.shared.model.InspirationDistance
import app.ransel.shared.model.InspirationRuleTrigger
import app.ransel.shared.model.InspirationSlot
import app.ransel.shared.model.Interest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/*
 * Explore Inspirasi rules both sides need (PRD v0.8, `docs/PRD.md` "Explore Inspirasi"). The server decides and enforces;
 * the app uses the same functions to enable buttons and to show the fallback question when a step is slow.
 */

/** The numbers of the PRD that are not prices. Prices come from the server (`CreditCostsDto.inspiration`, next-batch). */
object InspirationLimits {
    const val MAX_INTERESTS = 5
    /** At most 6 questions in a session. */
    const val MAX_STEPS = 6
    /** "Sudah cukup, lihat inspirasi" can appear from this step (1-based), never while a mandatory question is left. */
    const val FINISH_EARLY_FROM_STEP = 3
    const val HERO_TEXT_MAX = 120
    const val OTHER_TEXT_MAX = 80
    /** 3–5 options per question. */
    const val MIN_OPTIONS = 3
    const val MAX_OPTIONS = 5
    const val FREE_ASK_AGAIN = 1
    const val FREE_CHIP_EDITS = 3
    const val SAVED_MAX = 30
    const val STALE_DAYS = 60
    const val SESSION_RESUME_HOURS = 24
    /** "Perbarui" on a saved card: once per card per 24 h. */
    const val REFRESH_INTERVAL_HOURS = 24
    /** Default for the server setting: new sessions per user per hour (429 RATE_LIMITED above it). */
    const val SESSIONS_PER_HOUR = 10
    /** Interview logs on the server. */
    const val LOG_RETENTION_DAYS = 90
    /** A step shows a skeleton for this long, then its fallback question. Target: every step, p95. */
    const val STEP_TIMEOUT_MS = 4_000
    /** Target for a whole session, p95. */
    const val SESSION_TARGET_MS = 20_000
}

/**
 * Switches a mandatory rule on and what it asks. The rules live in a server table (seeded by a migration, so they can grow
 * without an app release); [DEFAULT_INSPIRATION_RULES] is that seed.
 */
data class InspirationRule(val trigger: InspirationRuleTrigger, val requires: List<InspirationRequirement>)

/** What the rules look at: the chosen interests, whether the chosen combination is a skill activity, and slot 3. */
data class InspirationFacts(
    val interests: List<Interest>,
    /** Diving, freediving, climbing, and so on; the AI marks it, the server confirms it. */
    val skillActivity: Boolean = false,
    val distance: InspirationDistance? = null,
)

/** Skill activities ask level/certification and "Sudah punya buddy?"; shopping asks the budget; abroad needs a passport/visa note. */
val DEFAULT_INSPIRATION_RULES: List<InspirationRule> = listOf(
    InspirationRule(InspirationRuleTrigger.SKILL_ACTIVITY, listOf(InspirationRequirement.LEVEL, InspirationRequirement.BUDDY)),
    InspirationRule(InspirationRuleTrigger.SHOPPING, listOf(InspirationRequirement.BUDGET)),
    InspirationRule(InspirationRuleTrigger.ABROAD, listOf(InspirationRequirement.VISA_NOTE)),
)

private fun triggered(rule: InspirationRule, facts: InspirationFacts): Boolean = when (rule.trigger) {
    InspirationRuleTrigger.SKILL_ACTIVITY -> facts.skillActivity
    InspirationRuleTrigger.SHOPPING -> Interest.SHOPPING in facts.interests
    InspirationRuleTrigger.ABROAD -> facts.distance == InspirationDistance.ABROAD
}

/**
 * The mandatory questions still to ask: every requirement of a switched-on rule that is not answered yet, in the order
 * LEVEL, BUDDY, BUDGET. [InspirationRequirement.VISA_NOTE] is a note on the cards, not a question, so it is never here.
 */
fun pendingInspirationQuestions(
    rules: List<InspirationRule>,
    facts: InspirationFacts,
    answered: Set<InspirationRequirement>,
): List<InspirationRequirement> =
    rules.filter { triggered(it, facts) }.flatMap { it.requires }.toSet()
        .filter { it != InspirationRequirement.VISA_NOTE && it !in answered }
        .sortedBy { it.ordinal }

/** True when the cards must carry a passport/visa note (labelled "perkiraan AI", "cek lagi"). */
fun needsInspirationVisaNote(rules: List<InspirationRule>, facts: InspirationFacts): Boolean =
    rules.filter { triggered(it, facts) }.any { InspirationRequirement.VISA_NOTE in it.requires }

/**
 * "Sudah cukup, lihat inspirasi": from [InspirationLimits.FINISH_EARLY_FROM_STEP] (the 1-based number of the question on
 * screen) and only when no mandatory question is left.
 */
fun canFinishInspirationEarly(step: Int, pending: List<InspirationRequirement>): Boolean =
    step >= InspirationLimits.FINISH_EARLY_FROM_STEP && pending.isEmpty()

/** False once the session has asked [InspirationLimits.MAX_STEPS] questions. */
fun canAskAnotherInspirationQuestion(stepsAsked: Int): Boolean = stepsAsked < InspirationLimits.MAX_STEPS

/** The first slot that is neither answered nor skipped, in the fixed order; null = the interview is complete. */
fun nextInspirationSlot(done: Set<InspirationSlot>): InspirationSlot? =
    InspirationSlot.entries.firstOrNull { it !in done }

/** Slot 2 is skipped with one interest (or none): there is nothing to combine. */
fun skipsCombination(interests: List<Interest>): Boolean = interests.distinct().size <= 1

/**
 * Field problems of a start request, for 422 VALIDATION_FAILED `details`: `interests` = TOO_MANY (above 5), `text` = TOO_LONG
 * (above 120). Empty map = valid. The app uses it too, to block the 6th chip.
 */
fun validateStartInspiration(interests: List<Interest>, text: String?): Map<String, String> = buildMap {
    if (interests.distinct().size > InspirationLimits.MAX_INTERESTS) put("interests", "TOO_MANY")
    if ((text?.trim()?.length ?: 0) > InspirationLimits.HERO_TEXT_MAX) put("text", "TOO_LONG")
}

/** `answer` = REQUIRED (nothing) or TOO_MANY (both), `otherText` = TOO_LONG (above 80). Empty map = valid. */
fun validateInspirationAnswer(optionId: String?, otherText: String?): Map<String, String> = buildMap {
    val hasOption = !optionId.isNullOrBlank()
    val other = otherText?.trim().orEmpty()
    if (!hasOption && other.isEmpty()) put("answer", "REQUIRED")
    if (hasOption && other.isNotEmpty()) put("answer", "TOO_MANY")
    if (other.length > InspirationLimits.OTHER_TEXT_MAX) put("otherText", "TOO_LONG")
}

/**
 * Slot 2 options after the server's check: an option must combine at least two of the [chosen] interests (an option
 * with one is dropped), at most [InspirationLimits.MAX_OPTIONS]. When fewer than [InspirationLimits.MIN_OPTIONS] remain,
 * "Fokus <minat>" options for the chosen interests are added after them (valid combinations stay first), so the user
 * always has at least 3 choices (with 2 chosen interests, one combination and two "Fokus" options).
 */
fun checkCombinationOptions(
    options: List<InspirationOptionDto>,
    chosen: List<Interest>,
    language: String,
): List<InspirationOptionDto> {
    val chosenSet = chosen.toSet()
    val valid = options.filter { o -> o.interests.toSet().count { it in chosenSet } >= 2 }.take(InspirationLimits.MAX_OPTIONS)
    if (valid.size >= InspirationLimits.MIN_OPTIONS) return valid
    val focus = chosen.distinct().map { focusOption(it, language) }
    return (valid + focus).take(InspirationLimits.MAX_OPTIONS)
}

/** "Fokus Fotografi" / "Focus on Photography". Id `focus:PHOTO`. */
fun focusOption(interest: Interest, language: String): InspirationOptionDto {
    val label = inspirationInterestLabel(interest, language)
    return InspirationOptionDto(
        id = "focus:${interest.name}",
        title = if (language == "en") "Focus on $label" else "Fokus $label",
        interests = listOf(interest),
    )
}

/** The chip label of an interest, one list for the whole app (`language` is `id` or `en`; anything else is `id`). */
fun inspirationInterestLabel(interest: Interest, language: String): String {
    val en = language == "en"
    return when (interest) {
        Interest.PHOTO -> if (en) "Photography" else "Fotografi"
        Interest.SHOPPING -> if (en) "Shopping" else "Belanja"
        Interest.FOOD -> if (en) "Food" else "Kuliner"
        Interest.ADVENTURE -> if (en) "Sports & outdoors" else "Olahraga & outdoor"
        Interest.NIGHTLIFE -> if (en) "Music & events" else "Musik & event"
        Interest.CULTURE -> if (en) "History & culture" else "Sejarah & budaya"
        Interest.NATURE -> if (en) "Wildlife & nature" else "Satwa & alam"
        Interest.RELAX -> "Healing"
    }
}

/** Credits to charge for [refine] when [used] of that kind are already used: null = free, else the next-batch price applies. */
fun inspirationRefineCharge(refine: InspirationRefine, used: Int): CreditAction? {
    require(used >= 0) { "negative used" }
    return if (used < inspirationFreeRefines(refine)) null else CreditAction.NEXT_BATCH
}

fun inspirationFreeRefines(refine: InspirationRefine): Int = when (refine) {
    InspirationRefine.ASK_AGAIN -> InspirationLimits.FREE_ASK_AGAIN
    InspirationRefine.CHIP_EDIT -> InspirationLimits.FREE_CHIP_EDITS
}

fun inspirationFreeRefinesLeft(refine: InspirationRefine, used: Int): Int =
    (inspirationFreeRefines(refine) - used).coerceAtLeast(0)

/** A session resumes for 24 h after it started. */
fun inspirationSessionExpiresAt(startedAt: Instant): Instant = startedAt + InspirationLimits.SESSION_RESUME_HOURS.hours

/** The 31st save removes the oldest: true when [savedCount] ideas are already saved. */
fun savedIdeaEvictsOldest(savedCount: Int): Boolean = savedCount >= InspirationLimits.SAVED_MAX

/** Older than 60 days: the card shows "Perkiraan AI · dibuat <bulan>" and "Perbarui". */
fun isSavedIdeaStale(refreshedAt: Instant, now: Instant): Boolean =
    now - refreshedAt > InspirationLimits.STALE_DAYS.days

/** When "Perbarui" works again: 24 h after the last refresh; null = now (never refreshed). */
fun savedIdeaRefreshAvailableAt(lastRefreshRequestAt: Instant?): Instant? =
    lastRefreshRequestAt?.plus(InspirationLimits.REFRESH_INTERVAL_HOURS.hours)

fun canRefreshSavedIdea(lastRefreshRequestAt: Instant?, now: Instant): Boolean {
    val at = savedIdeaRefreshAvailableAt(lastRefreshRequestAt) ?: return true
    return now >= at
}
