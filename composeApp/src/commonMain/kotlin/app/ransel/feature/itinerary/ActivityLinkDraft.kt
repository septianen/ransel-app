package app.ransel.feature.itinerary

import app.ransel.shared.domain.MAX_ACTIVITY_LINKS
import app.ransel.shared.domain.activityLinkLabel
import app.ransel.shared.domain.cleanActivityLinks
import app.ransel.shared.domain.normalizeActivityLink

/** One row of the "Link" section; [key] is stable while rows are added or removed, so a row keeps its own focus state. */
data class LinkRow(val key: Int, val text: String)

/** What is wrong with a row (spec "Banyak link di aktivitas"): not a web address, or the same address as a row above. */
enum class LinkProblem { INVALID, DUPLICATE }

/**
 * The rows of the "Link" section of the activity form. Pure state: the spec's rules (at most [MAX_ACTIVITY_LINKS] rows,
 * "Tambah link" only after a filled last row, the X of the last remaining row empties it, empty rows are dropped on save)
 * live here so a test can check them without a screen.
 */
data class LinkDraft(
    val rows: List<LinkRow> = listOf(LinkRow(0, "")),
    private val nextKey: Int = 1,
) {
    fun change(key: Int, text: String) = copy(rows = rows.map { if (it.key == key) it.copy(text = text) else it })

    /** The X: removes the row; the only row left is emptied instead of removed. */
    fun remove(key: Int): LinkDraft =
        if (rows.size <= 1) copy(rows = listOf(LinkRow(rows.first().key, "")))
        else copy(rows = rows.filterNot { it.key == key })

    fun add(): LinkDraft = if (canAdd) copy(rows = rows + LinkRow(nextKey, ""), nextKey = nextKey + 1) else this

    /** "Tambah link" is off while the last row is empty, and gone at the maximum. */
    val canAdd: Boolean get() = rows.size < MAX_ACTIVITY_LINKS && rows.last().text.isNotBlank()
    val atMax: Boolean get() = rows.size >= MAX_ACTIVITY_LINKS

    /** The problem of each row by key. A duplicate is the second one: the first keeps its place. */
    val problems: Map<Int, LinkProblem>
        get() {
            val seen = mutableSetOf<String>()
            return buildMap {
                rows.forEach { row ->
                    if (row.text.isBlank()) return@forEach
                    val normalized = normalizeActivityLink(row.text)
                    when {
                        normalized == null -> put(row.key, LinkProblem.INVALID)
                        !seen.add(normalized) -> put(row.key, LinkProblem.DUPLICATE)
                    }
                }
            }
        }

    /** Save stays off while a row is not a web address; a duplicate only gets a note (the server keeps one). */
    val canSave: Boolean get() = problems.values.none { it == LinkProblem.INVALID }

    /** The label under a valid row ("Label: Booking.com"); null for an empty or invalid one. */
    fun labelOf(row: LinkRow): String? = normalizeActivityLink(row.text)?.let(::activityLinkLabel)

    /** What is saved: normalized, empty rows and duplicates dropped. Empty = the activity has no link. */
    fun links(): List<String> = cleanActivityLinks(rows.map { it.text })

    companion object {
        /** The form of an item: its links, or one empty row. [legacyText] keeps an old free-text link visible (as an invalid row) instead of dropping it unseen. */
        fun of(links: List<String>, legacyText: String? = null): LinkDraft {
            val texts = links.ifEmpty { listOfNotNull(legacyText?.trim()?.takeIf { it.isNotEmpty() }) }
            if (texts.isEmpty()) return LinkDraft()
            return LinkDraft(rows = texts.mapIndexed { i, t -> LinkRow(i, t) }, nextKey = texts.size)
        }
    }
}
