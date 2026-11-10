package app.ransel.server.ai

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    /**
     * The receipt JSON. Names are trimmed and cut. Summary lines (tax, service, discount, subtotal, total, cash)
     * are taken out of [LlmReceipt.items] and folded into the matching field when that field was empty.
     * Nothing is added that the model did not return. Amounts may be JSON numbers or printed strings ("130.000").
     */
    fun parseReceipt(content: String): LlmReceipt {
        val root = lenient.parseToJsonElement(extractJsonObject(content)) as? JsonObject ?: error("unexpected JSON")
        var tax = root.amount("tax")?.takeIf { it >= 0 } ?: 0.0
        var service = root.amount("service")?.takeIf { it >= 0 } ?: 0.0
        var discount = root.amount("discount")?.takeIf { it >= 0 } ?: 0.0
        val items = mutableListOf<LlmReceiptLine>()
        val rawItems = root["items"] as? JsonArray ?: JsonArray(emptyList())
        for (element in rawItems) {
            val obj = element as? JsonObject ?: continue
            val name = obj.text("name")?.trim()?.take(MAX_RECEIPT_NAME).orEmpty()
            if (name.isEmpty()) continue
            val amount = obj.amount("amount") ?: continue
            val quantity = obj.amount("quantity")?.toInt()?.takeIf { it in 1..999 }
            when (classifyReceiptLine(name)) {
                ReceiptLineKind.TAX -> if (tax == 0.0 && amount > 0) tax = amount
                ReceiptLineKind.SERVICE -> if (service == 0.0 && amount > 0) service = amount
                ReceiptLineKind.DISCOUNT -> if (discount == 0.0 && amount != 0.0) discount = kotlin.math.abs(amount)
                ReceiptLineKind.SKIP -> Unit
                ReceiptLineKind.ITEM -> if (amount >= 0 && items.size < MAX_RECEIPT_LINES) items.add(LlmReceiptLine(name, quantity, amount))
            }
        }
        return LlmReceipt(
            placeName = root.text("placeName")?.trim()?.take(MAX_RECEIPT_NAME)?.ifEmpty { null },
            date = root.text("date")?.trim()?.takeIf { it.length == 10 },
            currency = root.text("currency")?.trim()?.uppercase()?.takeIf { it.length == 3 },
            items = items,
            tax = tax,
            service = service,
            discount = discount,
            total = root.amount("total")?.takeIf { it > 0 },
        )
    }

    /** A printed amount: JSON number, or text such as "130.000", "1.250,50", "12.50", "Rp50.000". */
    internal fun parsePrintedAmount(raw: String): Double? {
        var text = raw.trim().lowercase()
            .replace("rp", "")
            .replace("idr", "")
            .replace("usd", "")
            .replace(" ", "")
            .replace("\u00a0", "")
        if (text.isEmpty()) return null
        val negative = text.startsWith("-") || (text.startsWith("(") && text.endsWith(")"))
        text = text.trim('-', '(', ')', '+')
        if (text.none { it.isDigit() }) return null
        val dots = text.count { it == '.' }
        val commas = text.count { it == ',' }
        val normalized = when {
            dots > 0 && commas > 0 ->
                if (text.lastIndexOf('.') > text.lastIndexOf(',')) text.replace(",", "")
                else text.replace(".", "").replace(',', '.')
            dots > 1 -> text.replace(".", "")
            commas > 1 -> text.replace(",", "")
            dots == 1 -> if (text.substringAfter('.').length == 3) text.replace(".", "") else text
            commas == 1 -> if (text.substringAfter(',').length == 3) text.replace(",", "") else text.replace(',', '.')
            else -> text.filter { it.isDigit() }
        }
        val value = normalized.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }

    private fun extractJsonObject(content: String): String {
        val trimmed = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) error("no json object")
        return trimmed.substring(start, end + 1)
    }

    private fun JsonObject.text(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.contentOrNull?.trim()?.ifEmpty { null }
    }

    private fun JsonObject.amount(key: String): Double? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        if (!primitive.isString) return primitive.doubleOrNull
        return parsePrintedAmount(primitive.content)
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

    private enum class ReceiptLineKind { ITEM, TAX, SERVICE, DISCOUNT, SKIP }

    /** Summary rows the model sometimes copies into items. Product names that merely contain these words stay items. */
    private fun classifyReceiptLine(name: String): ReceiptLineKind {
        val label = name.lowercase().replace(Regex("[^a-z0-9%]+"), " ").trim()
        return when {
            TAX.matches(label) -> ReceiptLineKind.TAX
            SERVICE.matches(label) -> ReceiptLineKind.SERVICE
            DISCOUNT.matches(label) || label == "promo" || PROMO_RATE.matches(label) -> ReceiptLineKind.DISCOUNT
            SKIP.matches(label) -> ReceiptLineKind.SKIP
            else -> ReceiptLineKind.ITEM
        }
    }

    private val TAX = Regex("""^(incl\s+)?(pajak|tax|pb\s*1|ppn|vat|gst)(\s.*)?$""")
    private val SERVICE = Regex("""^(service(\s+charge)?|sc|biaya\s+layanan)(\s+\d.*|\s*%.*)?$""")
    private val DISCOUNT = Regex("""^(diskon|discount|disc|potongan|voucher)(\s+\d.*|\s*%.*)?$""")
    private val PROMO_RATE = Regex("""^promo(\s+\d.*)?$""")
    private val SKIP = Regex(
        """^(sub\s*total|jumlah|grand\s*total|total(\s+(bayar|due|amount|belanja|harga))?|amount\s+due|nett|net|net\s+total|tunai|cash|kembali|kembalian|change|uang\s+kembali|pembulatan|rounding)$""",
    )

    private val DETAIL_KEYS = setOf("about", "highlights", "openingHours", "bestTime")
    const val MAX_ABOUT = 600
    const val MAX_HIGHLIGHT = 140
    const val MIN_INTERESTS = 3
    const val MAX_INTERESTS = 5
    const val MAX_INTEREST_LENGTH = 40
}
