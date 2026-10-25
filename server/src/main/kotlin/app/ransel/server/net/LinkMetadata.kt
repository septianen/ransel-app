package app.ransel.server.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URI

/** What a page says about itself. No price: the user types it (TRD "Fetching links that users paste"). */
data class PageMeta(
    val title: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val address: String? = null,
    val ratingValue: Double? = null,
    val ratingCount: Int? = null,
)

/** Reads Open Graph tags and schema.org JSON-LD out of HTML. Tolerant: anything it cannot read is left out. */
object LinkMetadata {
    private const val MAX_TITLE = 200
    private const val MAX_URL = 2000
    private const val MAX_ADDRESS = 300

    private val META = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val JSON_LD = Regex(
        "<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val PLACE_TYPES = setOf(
        "hotel", "lodgingbusiness", "resort", "bedandbreakfast", "hostel", "motel", "apartment", "vacationrental", "campground",
        "restaurant", "foodestablishment", "cafeorcoffeeshop", "barorpub", "touristattraction", "place", "localbusiness", "product",
    )
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(html: String, pageUrl: String): PageMeta {
        val head = html.take(MAX_SCAN)
        val meta = mutableMapOf<String, String>()
        META.findAll(head).forEach { tag ->
            val attrs = ATTRIBUTE.findAll(tag.value).associate { it.groupValues[1].lowercase() to (it.groupValues[2].ifEmpty { it.groupValues[3] }) }
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: return@forEach
            val content = attrs["content"] ?: return@forEach
            meta.putIfAbsent(key, content)
        }
        val ld = placeFromJsonLd(head)

        val title = clean(meta["og:title"], MAX_TITLE) ?: ld?.string("name")?.let { clean(it, MAX_TITLE) }
            ?: clean(meta["twitter:title"], MAX_TITLE) ?: TITLE.find(head)?.groupValues?.get(1)?.let { clean(it, MAX_TITLE) }
        val image = absoluteUrl(meta["og:image"] ?: meta["og:image:url"] ?: meta["twitter:image"] ?: ld?.get("image")?.let(::firstImage), pageUrl)
        val site = clean(meta["og:site_name"], 100) ?: runCatching { URI(pageUrl).host?.removePrefix("www.") }.getOrNull()
        val rating = ld?.get("aggregateRating") as? JsonObject
        return PageMeta(
            title = title,
            imageUrl = image,
            siteName = site,
            address = ld?.get("address")?.let(::addressText)?.let { clean(it, MAX_ADDRESS) },
            ratingValue = rating?.get("ratingValue")?.number()?.takeIf { it in 0.0..10.0 },
            ratingCount = (rating?.get("reviewCount") ?: rating?.get("ratingCount"))?.number()?.toInt()?.takeIf { it >= 0 },
        )
    }

    // ── JSON-LD ─────────────────────────────────────────────────────────────

    private fun placeFromJsonLd(html: String): JsonObject? {
        for (block in JSON_LD.findAll(html)) {
            val root = runCatching { json.parseToJsonElement(decode(block.groupValues[1].trim())) }.getOrNull() ?: continue
            flatten(root).firstOrNull { node -> types(node).any { it in PLACE_TYPES } }?.let { return it }
        }
        return null
    }

    private fun flatten(element: JsonElement): List<JsonObject> = when (element) {
        is JsonArray -> element.flatMap(::flatten)
        is JsonObject -> listOf(element) + ((element["@graph"] as? JsonArray)?.flatMap(::flatten).orEmpty())
        else -> emptyList()
    }

    private fun types(node: JsonObject): List<String> = when (val t = node["@type"]) {
        is JsonPrimitive -> listOfNotNull(t.contentOrNull)
        is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }.map { it.substringAfterLast('/').lowercase() }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonElement.number(): Double? = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.intOrNull?.toDouble() ?: it.contentOrNull?.toDoubleOrNull() }

    private fun firstImage(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.firstNotNullOfOrNull(::firstImage)
        is JsonObject -> element.string("url") ?: element.string("contentUrl")
        else -> null
    }

    private fun addressText(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> listOf("streetAddress", "addressLocality", "addressRegion", "postalCode")
            .mapNotNull { element.string(it)?.takeIf { s -> s.isNotBlank() } }
            .plus(listOfNotNull(element["addressCountry"]?.let { c -> (c as? JsonObject)?.string("name") ?: (c as? JsonPrimitive)?.contentOrNull }))
            .joinToString(", ")
            .ifEmpty { null }
        else -> null
    }

    // ── text and links ──────────────────────────────────────────────────────

    /** Decodes entities, collapses white space, and cuts to [max]; null when nothing is left. */
    internal fun clean(text: String?, max: Int): String? =
        text?.let { decode(it) }?.replace(Regex("\\s+"), " ")?.trim()?.take(max)?.takeIf { it.isNotEmpty() }

    internal fun decode(text: String): String {
        var out = text
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&#x27;", "'").replace("&apos;", "'").replace("&nbsp;", " ")
        out = Regex("&#(\\d{1,6});").replace(out) { m -> m.groupValues[1].toIntOrNull()?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value }
        out = Regex("&#[xX]([0-9a-fA-F]{1,6});").replace(out) { m -> m.groupValues[1].toIntOrNull(16)?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value }
        return out
    }

    /** An http or https address, made absolute against the page; anything else (data:, javascript:) is dropped. */
    internal fun absoluteUrl(raw: String?, pageUrl: String): String? {
        val value = decode(raw ?: return null).trim().takeIf { it.isNotEmpty() && it.length <= MAX_URL } ?: return null
        val resolved = runCatching { URI(pageUrl).resolve(value.replace(" ", "%20")) }.getOrNull() ?: return null
        return resolved.toString().takeIf { (resolved.scheme == "http" || resolved.scheme == "https") && !resolved.host.isNullOrEmpty() && it.length <= MAX_URL }
    }

    private const val MAX_SCAN = 600_000
}
