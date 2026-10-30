package app.ransel.content

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ransel.shared.domain.categoryLabelKey
import app.ransel.shared.dto.ContentCategoryDto
import app.ransel.shared.dto.ContentDto
import app.ransel.shared.dto.ErrorTextDto
import app.ransel.shared.model.CategoryKind

/**
 * What Karung published (`GET /content`, admin-spec stage E), indexed for lookups. Everything here only replaces the
 * bundled copy; a key, code or category that is not in it keeps the app's own text and look.
 */
class RemoteContent(val dto: ContentDto) {
    /** Locale ("id", "en") → string key → text. Category labels count as strings (`expense_category_food`). */
    private val strings: Map<String, Map<String, String>> = run {
        val out = dto.strings.mapValues { it.value.toMutableMap() }.toMutableMap()
        // categories are saved live, so their labels win over a string of the same key
        dto.categories.forEach { c ->
            val key = categoryLabelKey(c.kind, c.constant)
            c.labelId?.takeIf { it.isNotBlank() }?.let { out.getOrPut("id", ::mutableMapOf)[key] = it }
            c.labelEn?.takeIf { it.isNotBlank() }?.let { out.getOrPut("en", ::mutableMapOf)[key] = it }
        }
        out
    }
    private val errors: Map<String, ErrorTextDto> = dto.errors.associate { it.code to it.text }
    private val categories: Map<CategoryKind, Map<String, ContentCategoryDto>> =
        dto.categories.groupBy { it.kind }.mapValues { (_, list) -> list.associateBy { it.constant } }

    val version: Int get() = dto.version
    val isEmpty: Boolean get() = strings.isEmpty() && errors.isEmpty() && categories.isEmpty()

    fun text(locale: String, key: String): String? = strings[locale]?.get(key)

    fun error(code: String): ErrorTextDto? = errors[code]?.takeUnless { it.isEmpty }

    fun category(kind: CategoryKind, constant: String): ContentCategoryDto? = categories[kind]?.get(constant)

    /**
     * [entries] in the published order. With [hideHidden] (pickers) hidden categories are left out, except [keep]
     * (the current choice); lists of existing items keep them.
     */
    fun <E : Enum<E>> ordered(kind: CategoryKind, entries: List<E>, hideHidden: Boolean = true, keep: E? = null): List<E> {
        val styled = categories[kind] ?: return entries
        return entries
            .filter { !hideHidden || it == keep || styled[it.name]?.hidden != true }
            .sortedBy { styled[it.name]?.sortOrder ?: entries.indexOf(it) }
    }

    companion object {
        val EMPTY = RemoteContent(ContentDto(version = 0))
    }
}

/**
 * The content in use. A snapshot state, so composables that read it (strings, categories, error screens) update when
 * a newer version arrives; plain functions (`getString`, category icons) read it too. Set by `RemoteContentStore`.
 */
object LiveContent {
    var current: RemoteContent by mutableStateOf(RemoteContent.EMPTY)
}
