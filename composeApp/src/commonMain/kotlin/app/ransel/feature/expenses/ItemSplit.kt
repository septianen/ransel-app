package app.ransel.feature.expenses

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ransel.shared.domain.hasChargeableReceiptItem
import app.ransel.shared.domain.itemsTotal
import app.ransel.shared.domain.splitByItems
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.util.newId

/**
 * "Per item": who shares each receipt line. Used after a scan (`ReceiptFormState`) and when editing an expense that
 * has receipt lines (`ExpenseSheet`), so both look and count the same.
 *
 * Lines are editable: name, amount, delete, and a new line (shared by everyone still on the trip). Who shares a line
 * is stored by a client-only key, so a rename, a new amount, or deleting another line does not move the ticks.
 * One line is open for editing at a time ([beginEdit] / [commitEdit]). A new line that is still empty is dropped
 * on commit and is not part of the amount.
 *
 * [extrasIndex] is the tax-and-service line (no members, spread in proportion by domain.splitByItems), or -1.
 * [locked] are people who left the trip but are on a line: they stay on the lines they had, cannot be taken off
 * (what they had stays their debt) and cannot be put on other lines (owner, 30 Oct 2026).
 */
class ItemSplit(
    lines: List<ExpenseItemDto>,
    extrasIndex: Int,
    /** Current members, in the order their avatars show. */
    val memberIds: List<String>,
    /** Who shares each line at first, by index; null = everyone. An empty set is everyone too (a "Bagi rata" line). */
    initial: Map<Int, Set<String>>? = null,
    val locked: Set<String> = emptySet(),
) {
    private data class Row(
        val key: String,
        val name: String,
        val amountMinor: Long,
        val extras: Boolean,
        /** Added with "Tambah item" and not committed yet. An empty one is dropped, not saved. */
        val fresh: Boolean = false,
    )

    private val rows = mutableStateListOf<Row>()
    private val sharers = mutableStateMapOf<String, Set<String>>()

    /** The line open in the editor, or null when every line is showing. */
    var editingKey by mutableStateOf<String?>(null)
        private set

    /** [FOCUS_NAME] or [FOCUS_AMOUNT] for [editingKey]. */
    var focusPart by mutableStateOf(FOCUS_NAME)
        private set

    /** Bumped when the open line should take the keyboard again (a failed commit, or a tap on the other part). */
    var focusNonce by mutableIntStateOf(0)
        private set

    /** The open line was committed with no name. */
    var nameError by mutableStateOf(false)
        private set

    init {
        lines.forEachIndexed { index, line ->
            val key = newId()
            val extras = index == extrasIndex
            rows.add(Row(key, line.name, line.amountMinor, extras))
            if (!extras) sharers[key] = initial?.get(index)?.takeIf { it.isNotEmpty() } ?: memberIds.toSet()
        }
    }

    val lines: List<ExpenseItemDto> get() = rows.map { ExpenseItemDto(it.name, it.amountMinor) }

    /** Index of the tax-and-service line, or -1. */
    val extrasIndex: Int get() = rows.indexOfFirst { it.extras }

    /** Stable id for Compose, so a field keeps its text when another line is deleted. */
    fun keyAt(index: Int): String = rows[index].key

    fun setName(index: Int, name: String) {
        val row = rows.getOrNull(index) ?: return
        val next = name.take(MAX_NAME)
        rows[index] = row.copy(name = next)
        if (row.key == editingKey && next.isNotBlank()) nameError = false
    }

    fun setAmount(index: Int, amountMinor: Long) {
        val row = rows.getOrNull(index) ?: return
        rows[index] = row.copy(amountMinor = amountMinor.coerceAtLeast(0))
    }

    /** Removes the line and returns it so the snackbar can put it back. */
    fun delete(index: Int): DeletedLine? {
        val row = rows.getOrNull(index) ?: return null
        val saved = DeletedLine(index, row.key, row.name, row.amountMinor, row.extras, sharers[row.key].orEmpty(), row.fresh)
        if (row.key == editingKey) {
            editingKey = null
            nameError = false
        }
        rows.removeAt(index)
        sharers.remove(row.key)
        return saved
    }

    /** Puts a deleted line back at the index it had, with the same amount and the same people. */
    fun restore(line: DeletedLine) {
        val at = line.index.coerceIn(0, rows.size)
        rows.add(at, Row(line.key, line.name, line.amountMinor, line.extras, line.fresh))
        if (!line.extras) sharers[line.key] = line.sharers
    }

    /** A new item line, before the tax line when there is one. Shared by every current member. Not opened for editing. */
    fun addLine(): Boolean {
        if (rows.size >= MAX_LINES) return false
        val key = newId()
        val at = extrasIndex.let { if (it < 0) rows.size else it }
        rows.add(at, Row(key, "", 0, extras = false, fresh = true))
        sharers[key] = memberIds.toSet()
        return true
    }

    /**
     * "Tambah item": if a new line is still empty, focus that one; otherwise commit the open line and add one,
     * focused on its name. Does nothing when the open line has no name.
     */
    fun addOrFocusBlank(): Boolean {
        val blank = rows.indexOfFirst { ignored(it) }
        if (blank >= 0) {
            beginEdit(blank, FOCUS_NAME)
            return true
        }
        if (!commitEdit()) return false
        if (!addLine()) return false
        val at = extrasIndex.let { if (it < 0) rows.lastIndex else it - 1 }
        beginEdit(at, FOCUS_NAME)
        return true
    }

    fun canAddLine(): Boolean = rows.size < MAX_LINES || rows.any { ignored(it) }

    /** True when this line is the one open in the editor. */
    fun isEditing(index: Int): Boolean = rows.getOrNull(index)?.key == editingKey

    /**
     * Opens [index] on [part]. The line that was open is committed first; if that commit fails, it stays open.
     */
    fun beginEdit(index: Int, part: Int) {
        val targetKey = rows.getOrNull(index)?.key ?: return
        if (editingKey == targetKey) {
            focusPart = part
            if (part == FOCUS_NAME && rows[index].name.isNotBlank()) nameError = false
            focusNonce++
            return
        }
        if (editingKey != null && !commitEdit()) return
        if (rows.none { it.key == targetKey }) return
        editingKey = targetKey
        focusPart = part
        nameError = false
        focusNonce++
    }

    /**
     * Closes the open line. An empty new line is removed. A line with no name stays open and [nameError] is set.
     * Returns false when the open line cannot be left.
     */
    fun commitEdit(): Boolean {
        val key = editingKey ?: return true
        val index = rows.indexOfFirst { it.key == key }
        if (index < 0) {
            editingKey = null
            nameError = false
            return true
        }
        val trimmed = rows[index].copy(name = rows[index].name.trim())
        rows[index] = trimmed
        if (ignored(trimmed)) {
            delete(index)
            editingKey = null
            nameError = false
            return true
        }
        if (trimmed.name.isBlank()) {
            nameError = true
            focusPart = FOCUS_NAME
            focusNonce++
            return false
        }
        rows[index] = trimmed.copy(fresh = false)
        editingKey = null
        nameError = false
        return true
    }

    fun sharersOf(index: Int): Set<String> = rows.getOrNull(index)?.let { sharers[it.key] }.orEmpty()

    /** The avatars of a line: the current members, then anyone who left but is on it. */
    fun peopleOf(index: Int): List<String> = memberIds + sharersOf(index).filter { it in locked && it !in memberIds }

    fun toggle(index: Int, memberId: String) {
        val row = rows.getOrNull(index) ?: return
        if (row.extras || memberId in locked) return
        val now = sharersOf(index)
        sharers[row.key] = if (memberId in now) now - memberId else now + memberId
    }

    /** Names, amounts and who shares what, to tell whether the person changed anything ("Buang perubahan?"). */
    fun snapshot(): List<Any?> = rows.map { listOf(it.name, it.amountMinor, it.extras, it.fresh, sharers[it.key]?.sorted()) }

    /** Lines minus [total]: 0 when they match, as "Per item" needs before the server's SPLIT_TOTAL_MISMATCH. */
    fun difference(total: Long): Long = itemsTotal(lines) - total

    /** A line that is not the tax line. */
    fun hasItemLine(): Boolean = rows.any { !it.extras }

    /**
     * Ready to save: at least one non-tax line above 0, and every line that counts has a name.
     * An empty new line does not count and does not block. Tax is [Row.extras], never the label.
     */
    fun linesReady(): Boolean =
        hasChargeableReceiptItem(items()) && rows.none { !ignored(it) && it.name.isBlank() }

    /** The lines to send. An empty new line is left out; the tax line stays without members and keeps [ExpenseItemDto.isTax]. */
    fun items(): List<ExpenseItemDto> = rows.mapIndexedNotNull { i, row ->
        if (ignored(row)) null
        else if (row.extras) ExpenseItemDto(row.name.trim(), row.amountMinor, isTax = true)
        else ExpenseItemDto(row.name.trim(), row.amountMinor, peopleOf(i).filter { it in sharersOf(i) })
    }

    /**
     * The tax line was created before its label loaded (web strings start empty). Fill it once, and only while it
     * is still blank, so a name the user already typed is left alone.
     */
    fun applyExtrasLabelIfBlank(label: String) {
        if (label.isBlank()) return
        val index = extrasIndex
        if (index < 0) return
        if (rows[index].name.isBlank()) setName(index, label)
    }

    /**
     * Every priced line has someone on it, every line that counts has a name, and the lines add up to [total].
     * Tax alone cannot be split (there is then no proportion to spread it by).
     */
    fun isValid(total: Long): Boolean {
        if (!linesReady() || difference(total) != 0L) return false
        val priced = rows.indices.filter { !rows[it].extras && !ignored(rows[it]) && rows[it].amountMinor > 0 }
        return priced.all { sharersOf(it).isNotEmpty() }
    }

    fun shares(payerId: String): Map<String, Long> = splitByItems(items(), payerId)

    /**
     * "Bagian tiap orang": each person's share and the part of it that is tax and service. Empty until the lines add
     * up to [total]; [people] = whose rows to show, in order.
     */
    fun perPerson(payerId: String, total: Long, people: List<String>): List<PersonShare> {
        val sent = items()
        if (difference(total) != 0L || sent.none { it.memberIds.isNotEmpty() }) return emptyList()
        val extrasAt = rows.filter { !ignored(it) }.indexOfFirst { it.extras }
        val withExtras = shares(payerId)
        val itemsOnly = if (extrasAt < 0) withExtras else {
            val own = sent.filterIndexed { i, _ -> i != extrasAt }
            if (own.none { it.memberIds.isNotEmpty() }) emptyMap() else splitByItems(own, payerId)
        }
        return (people + withExtras.keys).distinct().mapNotNull { id ->
            val share = withExtras[id] ?: 0
            if (share == 0L) null else PersonShare(id, share, share - (itemsOnly[id] ?: 0))
        }
    }

    private fun ignored(row: Row): Boolean = row.fresh && row.name.isBlank() && row.amountMinor == 0L

    companion object {
        const val MAX_NAME = 120
        const val MAX_LINES = 100
        const val FOCUS_NAME = 0
        const val FOCUS_AMOUNT = 1

        /**
         * The open line commits only once focus has left both of its fields. Moving from the name to the amount
         * keeps one of them focused, so the row stays open and the amount is not dropped.
         */
        fun shouldCommitLineEdit(armed: Boolean, nameFocused: Boolean, amountFocused: Boolean): Boolean =
            armed && !nameFocused && !amountFocused

        /**
         * The lines of a saved expense. The tax line is the one with [ExpenseItemDto.isTax], not a matching name
         * and not "the last line nobody was on". A line with nobody on it starts as everyone still on the trip.
         * People on a line who are no longer members are [locked].
         */
        fun of(items: List<ExpenseItemDto>, memberIds: List<String>): ItemSplit {
            val extras = items.indexOfLast { it.isTax }
            val initial = items.withIndex()
                .filter { it.index != extras && it.value.memberIds.isNotEmpty() }
                .associate { it.index to it.value.memberIds.toSet() }
            val gone = items.flatMap { it.memberIds }.filter { it !in memberIds }.toSet()
            return ItemSplit(items.map { it.copy(memberIds = emptyList()) }, extras, memberIds, initial, gone)
        }
    }
}

/**
 * Amount stored with receipt lines. [typedAmount] is what the amount field held; [perItem] is the split mode.
 * Neither changes the result: "Bagi rata" and "Atur nominal" save the line sum too, so a price edit cannot be
 * stored as a different total.
 */
fun amountSavedWithReceiptLines(
    lineSum: Long,
    @Suppress("UNUSED_PARAMETER") typedAmount: Long?,
    @Suppress("UNUSED_PARAMETER") perItem: Boolean,
): Long = lineSum

/** A line [ItemSplit.delete] removed, so it can be put back with the same people. */
data class DeletedLine(
    val index: Int,
    val key: String,
    val name: String,
    val amountMinor: Long,
    val extras: Boolean,
    val sharers: Set<String>,
    val fresh: Boolean,
)

/** One member's part of a split per item; [extrasMinor] is the tax and service inside [totalMinor]. */
data class PersonShare(val memberId: String, val totalMinor: Long, val extrasMinor: Long)
