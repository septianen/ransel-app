package app.ransel.shared.domain

import app.ransel.shared.dto.ErrorTextDto
import app.ransel.shared.dto.InterestChipDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.CategoryKind
import app.ransel.shared.model.InterestChipKind
import app.ransel.shared.model.PackingCategory

// Remote content rules (admin-spec stage D), the same in Karung and on the server.

const val MAX_CONTENT_STRING = 500
const val MAX_ERROR_TITLE = 80
const val MAX_ERROR_MESSAGE = 300
const val MAX_ERROR_BUTTON = 40
const val MAX_CATEGORY_LABEL = 30
const val MAX_INTEREST_LABEL = 40
const val MAX_INTEREST_CHIPS = 12

/** What an error message may fill in from the error's `details`. */
val ERROR_PLACEHOLDERS = setOf("{cost}", "{available}", "{resetsAt}", "{maxBytes}", "{usedBytes}", "{limitBytes}")

private val FORMAT_ARG = Regex("""%(\d+\$)?[sd]""")
private val NAMED = Regex("""\{[A-Za-z]+\}""")

/** The placeholders of a text: Android format arguments (`%1$s`, `%d`) and `{name}` tokens. */
fun placeholdersOf(text: String): Set<String> =
    (FORMAT_ARG.findAll(text) + NAMED.findAll(text)).map { it.value }.toSet()

/** An app string must keep exactly the placeholders of its bundled text, or the app could not fill it in. */
fun placeholdersMatch(bundled: String, value: String): Boolean = placeholdersOf(bundled) == placeholdersOf(value)

/** Problems of one string draft (`id` / `en`): PLACEHOLDER_MISMATCH or TOO_LONG. */
fun validateContentString(bundledId: String, bundledEn: String, id: String?, en: String?): Map<String, String> = buildMap {
    fun check(field: String, bundled: String, value: String?) {
        if (value == null) return
        if (value.length > MAX_CONTENT_STRING || value.isBlank()) put(field, "TOO_LONG")
        else if (!placeholdersMatch(bundled, value)) put(field, "PLACEHOLDER_MISMATCH")
    }
    check("id", bundledId, id)
    check("en", bundledEn, en)
}

/** Problems of one error-message draft, per field. */
fun validateErrorText(text: ErrorTextDto, illustrations: List<String>): Map<String, String> = buildMap {
    fun check(field: String, value: String?, max: Int) {
        if (value == null) return
        if (value.length > max) put(field, "TOO_LONG")
        else if (!ERROR_PLACEHOLDERS.containsAll(NAMED.findAll(value).map { it.value }.toSet()) || FORMAT_ARG.containsMatchIn(value)) {
            put(field, "PLACEHOLDER_MISMATCH")
        }
    }
    check("titleId", text.titleId, MAX_ERROR_TITLE)
    check("titleEn", text.titleEn, MAX_ERROR_TITLE)
    check("messageId", text.messageId, MAX_ERROR_MESSAGE)
    check("messageEn", text.messageEn, MAX_ERROR_MESSAGE)
    check("buttonId", text.buttonId, MAX_ERROR_BUTTON)
    check("buttonEn", text.buttonEn, MAX_ERROR_BUTTON)
    if (text.illustration != null && text.illustration !in illustrations) put("illustration", "UNKNOWN")
}

/** Icons a category may take (Material Symbols names the apps bundle). */
val CATEGORY_ICONS = listOf(
    "attractions", "local_activity", "restaurant", "directions_car", "directions_boat", "flight", "hotel", "shopping_bag",
    "event", "hiking", "beach_access", "forest", "landscape", "temple_hindu", "theater_comedy", "location_city",
    "apparel", "badge", "power", "soap", "medical_services", "category", "luggage", "payments", "more_horiz",
)

/** IRIS accent colors a category may take. */
val CATEGORY_COLORS = listOf("blue", "orange", "teal", "purple", "yellow", "brown", "red", "green", "magenta")

/** The constants of a category list, in the apps' order. */
fun categoryConstants(kind: CategoryKind): List<String> = when (kind) {
    CategoryKind.ITINERARY, CategoryKind.EXPENSE -> Category.entries.map { it.name }
    CategoryKind.PACKING -> PackingCategory.entries.map { it.name }
}

/** The string key of the bundled label (`expense_category_food`). */
fun categoryLabelKey(kind: CategoryKind, constant: String): String =
    "${kind.name.lowercase()}_category_${constant.lowercase()}"

/** The bundled icon and color (`ui/category/CategoryUi.kt` in the app). */
fun defaultCategoryStyle(kind: CategoryKind, constant: String): Pair<String, String> = when (kind) {
    CategoryKind.ITINERARY, CategoryKind.EXPENSE -> when (constant) {
        "ATTRACTION" -> (if (kind == CategoryKind.EXPENSE) "local_activity" else "attractions") to "blue"
        "FOOD" -> "restaurant" to "orange"
        "TRANSPORT" -> "directions_car" to "teal"
        "LODGING" -> "hotel" to "purple"
        "SHOPPING" -> "shopping_bag" to "yellow"
        else -> "event" to "brown"
    }
    CategoryKind.PACKING -> when (constant) {
        "CLOTHING" -> "apparel" to "purple"
        "DOCUMENTS" -> "badge" to "blue"
        "ELECTRONICS" -> "power" to "orange"
        "TOILETRIES" -> "soap" to "teal"
        "HEALTH" -> "medical_services" to "red"
        else -> "category" to "brown"
    }
}

/**
 * The chips `GET /explore/suggestions` shows when a destination and month are curated: pinned and manual chips first
 * (in their order), then the AI chips that are neither hidden nor already there, at most [limit]. A pinned chip is an
 * AI chip kept even when a new AI answer leaves it out.
 */
fun curatedInterests(ai: List<String>, chips: List<InterestChipDto>, limit: Int = 5): List<String> {
    fun norm(s: String) = s.trim().lowercase()
    val hidden = chips.filter { it.kind == InterestChipKind.HIDDEN }.map { norm(it.label) }.toSet()
    val first = chips.filter { it.kind == InterestChipKind.PINNED || it.kind == InterestChipKind.MANUAL }.map { it.label.trim() }
    val rest = ai.filter { norm(it) !in hidden }
    return (first + rest).distinctBy(::norm).take(limit)
}
