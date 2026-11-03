package app.ransel.content

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ransel.shared.domain.categoryLabelKey
import app.ransel.shared.dto.ContentCategoryDto
import app.ransel.shared.dto.ContentDto
import app.ransel.shared.model.CategoryKind

/**
 * The categories Karung restyled (`GET /content`, admin-spec stage E), indexed for lookups. Only categories are remote
 * content since 3 Nov 2026 (owner): screen texts are the app's own, and error copy comes with each error response
 * ([ServerErrorCopy]). A copy saved by an older build may still hold `strings` and `errors`; both are ignored.
 * A category that is not in it keeps the app's own label and look.
 */
class RemoteContent(val dto: ContentDto) {
    /** Locale ("id", "en") → category label key (`expense_category_food`) → label. */
    private val strings: Map<String, Map<String, String>> = run {
        val out = mutableMapOf<String, MutableMap<String, String>>()
        dto.categories.forEach { c ->
            val key = categoryLabelKey(c.kind, c.constant)
            c.labelId?.takeIf { it.isNotBlank() }?.let { out.getOrPut("id", ::mutableMapOf)[key] = it }
            c.labelEn?.takeIf { it.isNotBlank() }?.let { out.getOrPut("en", ::mutableMapOf)[key] = it }
        }
        out
    }
    private val categories: Map<CategoryKind, Map<String, ContentCategoryDto>> =
        dto.categories.groupBy { it.kind }.mapValues { (_, list) -> list.associateBy { it.constant } }

    val version: Int get() = dto.version
    val isEmpty: Boolean get() = strings.isEmpty() && categories.isEmpty()

    /** A category label Karung set for [key] in [locale]; any other key is never remote. */
    fun text(locale: String, key: String): String? = strings[locale]?.get(key)

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
 * The content in use. A snapshot state, so composables that read it (category labels, icons, colors, order) update
 * when a newer version arrives; plain functions (`getString`, category icons) read it too. Set by
 * `RemoteContentStore`; `initKoin` creates it on the main thread before the first composition.
 */
object LiveContent {
    var current: RemoteContent by mutableStateOf(RemoteContent.EMPTY)
}
