package app.ransel.server.ai

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** The model is asked for JSON but not forced to the schema ("soft" JSON), so parsing is lenient. */
object LlmOutput {
    private val lenient = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** Accepts `{"places":[...]}`, a bare array, and output wrapped in a markdown code fence. */
    fun parsePlaces(content: String): List<LlmCandidate> {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root: JsonElement = lenient.parseToJsonElement(cleaned)
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root["places"]?.jsonArray ?: error("no places array")
            else -> error("unexpected JSON")
        }
        return array.map(::decodeCandidate)
    }

    /** A malformed detail field (say an opening hours line without hours) must not cost the whole search: retry without them. */
    private fun decodeCandidate(element: JsonElement): LlmCandidate = try {
        lenient.decodeFromJsonElement(LlmCandidate.serializer(), element)
    } catch (e: SerializationException) {
        val obj = element as? JsonObject ?: throw e
        lenient.decodeFromJsonElement(LlmCandidate.serializer(), JsonObject(obj.filterKeys { it !in DETAIL_KEYS }))
    }

    /** `{"about":...,"highlights":[...],"openingHours":[{"label","hours"}],"bestTime":...,"costUsd":...}`; text is trimmed and capped. */
    fun parsePlaceText(content: String): LlmPlaceText {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val text = lenient.decodeFromJsonElement(LlmPlaceText.serializer(), lenient.parseToJsonElement(cleaned))
        return text.copy(
            about = text.about?.trim()?.take(MAX_ABOUT)?.ifEmpty { null },
            highlights = text.highlights.map { it.trim().take(MAX_HIGHLIGHT) }.filter { it.isNotEmpty() }.take(3),
            openingHours = text.openingHours.filter { it.label.isNotBlank() && it.hours.isNotBlank() }.take(3),
            bestTime = text.bestTime?.trim()?.take(MAX_HIGHLIGHT)?.ifEmpty { null },
            costUsd = text.costUsd?.takeIf { it > 0 },
        )
    }

    /** The receipt JSON; names are trimmed and cut, lines without a usable amount are dropped. */
    fun parseReceipt(content: String): LlmReceipt {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val receipt = lenient.decodeFromJsonElement(LlmReceipt.serializer(), lenient.parseToJsonElement(cleaned))
        return receipt.copy(
            placeName = receipt.placeName?.trim()?.take(MAX_RECEIPT_NAME)?.ifEmpty { null },
            currency = receipt.currency?.trim()?.uppercase()?.takeIf { it.length == 3 },
            items = receipt.items
                .filter { it.name.isNotBlank() && (it.amount ?: -1.0) >= 0 }
                .map { it.copy(name = it.name.trim().take(MAX_RECEIPT_NAME), quantity = it.quantity?.takeIf { q -> q in 1..999 }) }
                .take(MAX_RECEIPT_LINES),
            tax = receipt.tax?.takeIf { it >= 0 },
            service = receipt.service?.takeIf { it >= 0 },
            total = receipt.total?.takeIf { it > 0 },
        )
    }

    const val MAX_RECEIPT_NAME = 120
    const val MAX_RECEIPT_LINES = 100

    /** `{"interests":["Sakura",...],"priceGuide":{"lowMaxUsd":30,"midMaxUsd":90}}`; interests are cleaned, 3–5 kept. */
    fun parseInterests(content: String, coreLabels: Set<String> = emptySet()): InterestSuggestions {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = lenient.parseToJsonElement(cleaned) as? JsonObject ?: error("unexpected JSON")
        val interests = (root["interests"] as? JsonArray ?: error("no interests array"))
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_INTEREST_LENGTH && it.lowercase() !in coreLabels }
            .distinctBy { it.lowercase() }
            .take(MAX_INTERESTS)
        require(interests.size >= MIN_INTERESTS) { "too few interests" }
        val guide = root["priceGuide"] as? JsonObject ?: error("no priceGuide")
        val low = guide["lowMaxUsd"]?.jsonPrimitive?.doubleOrNull ?: error("no lowMaxUsd")
        val mid = guide["midMaxUsd"]?.jsonPrimitive?.doubleOrNull ?: error("no midMaxUsd")
        require(low > 0 && mid > low) { "bad price guide" }
        return InterestSuggestions(interests, low, mid)
    }

    private val DETAIL_KEYS = setOf("about", "highlights", "openingHours", "bestTime")
    const val MAX_ABOUT = 600
    const val MAX_HIGHLIGHT = 140
    const val MIN_INTERESTS = 3
    const val MAX_INTERESTS = 5
    const val MAX_INTEREST_LENGTH = 40
}
