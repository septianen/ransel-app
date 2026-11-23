package app.ransel.shared.domain

import app.ransel.shared.dto.InspirationOptionDto
import app.ransel.shared.dto.InspirationQuestionDto
import app.ransel.shared.model.InspirationDistance
import app.ransel.shared.model.InspirationQuestionSource
import app.ransel.shared.model.InspirationSlot
import app.ransel.shared.model.Interest

/*
 * Pre-written questions for a step whose AI call took longer than `InspirationLimits.STEP_TIMEOUT_MS` or failed. A fallback
 * never costs a credit and never loses earlier answers. Only slots whose content does not depend on what the AI learned have
 * one: COMBINATION (built from the chosen interests), DISTANCE and CONTEXT. DETAIL and OPTIONAL have none: the session
 * retries the step, then offers "Lihat inspirasi dari jawaban sejauh ini" (PRD v0.8).
 *
 * COPY STATUS: the wording below is provisional (the PRD gives the options for DISTANCE and the same-city durations, not
 * the question text or the longer durations). Benn/Rockstar to confirm before the strings are final.
 */

private fun pick(en: Boolean, id: String, enText: String) = if (en) enText else id

/** The fallback for [slot], or null when that slot has none. [language] is `id` or `en`. */
fun fallbackInspirationQuestion(
    slot: InspirationSlot,
    interests: List<Interest>,
    distance: InspirationDistance?,
    language: String,
    stepsDone: Int = 0,
    canFinishEarly: Boolean = false,
): InspirationQuestionDto? {
    val en = language == "en"
    return when (slot) {
        InspirationSlot.COMBINATION -> {
            val chosen = interests.distinct()
            if (chosen.size < 2) return null
            val pairs = buildList {
                for (i in chosen.indices) for (j in i + 1 until chosen.size) add(chosen[i] to chosen[j])
            }.take(InspirationLimits.MAX_OPTIONS)
            val combos = pairs.map { (a, b) ->
                val name = "${inspirationInterestLabel(a, language)} + ${inspirationInterestLabel(b, language)}"
                InspirationOptionDto(id = "combo:${a.name}+${b.name}", title = name, interests = listOf(a, b))
            }
            // The same check as for an AI answer: with two interests there is one pair, so "Fokus <minat>" options fill it up to 3.
            val options = checkCombinationOptions(combos, chosen, language)
            question(slot, pick(en, "Mau menggabungkan minat yang mana?", "Which interests do you want to combine?"), options, stepsDone, canFinishEarly)
        }
        InspirationSlot.DISTANCE -> question(
            slot,
            pick(en, "Seberapa jauh kamu mau pergi?", "How far do you want to go?"),
            listOf(
                InspirationOptionDto("same_city", pick(en, "Dalam kota", "In my city")),
                InspirationOptionDto("domestic", pick(en, "Luar kota (Indonesia)", "Elsewhere in Indonesia")),
                InspirationOptionDto("abroad", pick(en, "Luar negeri", "Abroad")),
                InspirationOptionDto("any", pick(en, "Bebas, AI yang pilih", "Anywhere, let the AI choose")),
            ),
            stepsDone, canFinishEarly,
        )
        InspirationSlot.CONTEXT -> {
            val options = if (distance == InspirationDistance.SAME_CITY) {
                listOf(
                    InspirationOptionDto("half_day", pick(en, "Setengah hari", "Half a day")),
                    InspirationOptionDto("full_day", pick(en, "Sehari", "A full day")),
                )
            } else {
                listOf(
                    InspirationOptionDto("upto_3", pick(en, "Sampai 3 hari", "Up to 3 days")),
                    InspirationOptionDto("4_to_7", pick(en, "4–7 hari", "4–7 days")),
                    InspirationOptionDto("over_7", pick(en, "Lebih dari 7 hari", "More than 7 days")),
                )
            }
            question(slot, pick(en, "Berapa lama waktu yang kamu punya?", "How much time do you have?"), options, stepsDone, canFinishEarly)
        }
        InspirationSlot.INTEREST, InspirationSlot.DETAIL, InspirationSlot.OPTIONAL -> null
    }
}

private fun question(slot: InspirationSlot, text: String, options: List<InspirationOptionDto>, stepsDone: Int, canFinishEarly: Boolean) =
    InspirationQuestionDto(
        slot = slot,
        text = text,
        options = options,
        canFinishEarly = canFinishEarly,
        source = InspirationQuestionSource.FALLBACK,
        stepsDone = stepsDone,
    )

/** Maps the DISTANCE option ids of the fallback (and of the AI, which must reuse them) to slot 3's value. */
fun inspirationDistanceFromOptionId(id: String): InspirationDistance? = when (id) {
    "same_city" -> InspirationDistance.SAME_CITY
    "domestic" -> InspirationDistance.DOMESTIC
    "abroad" -> InspirationDistance.ABROAD
    "any" -> InspirationDistance.ANY
    else -> null
}
