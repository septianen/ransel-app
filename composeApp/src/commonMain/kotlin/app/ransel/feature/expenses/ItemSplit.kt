package app.ransel.feature.expenses

import androidx.compose.runtime.mutableStateMapOf
import app.ransel.shared.domain.itemsTotal
import app.ransel.shared.domain.splitByItems
import app.ransel.shared.dto.ExpenseItemDto

/**
 * "Per item": who shares each receipt line. Used after a scan (`ReceiptFormState`) and when editing an expense that
 * was split per item (`ExpenseSheet`), so both look and count the same.
 *
 * [extrasIndex] is the tax-and-service line (no members, spread in proportion by domain.splitByItems), or -1.
 * [locked] are people who left the trip but are on a line: they stay on the lines they had, cannot be taken off
 * (what they had stays their debt) and cannot be put on other lines (owner, 30 Oct 2026).
 */
class ItemSplit(
    val lines: List<ExpenseItemDto>,
    val extrasIndex: Int,
    /** Current members, in the order their avatars show. */
    val memberIds: List<String>,
    /** Who shares each line at first, by index; null = everyone. */
    initial: Map<Int, Set<String>>? = null,
    val locked: Set<String> = emptySet(),
) {
    private val sharers = mutableStateMapOf<Int, Set<String>>().apply {
        lines.indices.filter { it != extrasIndex }.forEach { put(it, initial?.get(it) ?: memberIds.toSet()) }
    }

    fun sharersOf(index: Int): Set<String> = sharers[index].orEmpty()

    /** The avatars of a line: the current members, then anyone who left but is on it. */
    fun peopleOf(index: Int): List<String> = memberIds + sharersOf(index).filter { it in locked && it !in memberIds }

    fun toggle(index: Int, memberId: String) {
        if (memberId in locked) return
        val now = sharersOf(index)
        sharers[index] = if (memberId in now) now - memberId else now + memberId
    }

    /** Who shares what, to tell whether the person changed anything ("Buang perubahan?"). */
    fun snapshot(): Map<Int, Set<String>> = sharers.toMap()

    /** Lines minus [total]: 0 when they match, as "Per item" needs. */
    fun difference(total: Long): Long = itemsTotal(lines) - total

    /** The lines to send, each with who shares it (the tax-and-service line stays without members). */
    fun items(): List<ExpenseItemDto> =
        lines.mapIndexed { i, line -> if (i == extrasIndex) line else line.copy(memberIds = peopleOf(i).filter { it in sharersOf(i) }) }

    /** Every line with an amount has someone on it, and the lines add up to [total]. */
    fun isValid(total: Long): Boolean =
        difference(total) == 0L && lines.indices.filter { it != extrasIndex }.all { sharersOf(it).isNotEmpty() || lines[it].amountMinor == 0L }

    fun shares(payerId: String): Map<String, Long> = splitByItems(items(), payerId)

    /**
     * "Bagian tiap orang": each person's share and the part of it that is tax and service. Empty until the lines add
     * up to [total]; [people] = whose rows to show, in order.
     */
    fun perPerson(payerId: String, total: Long, people: List<String>): List<PersonShare> {
        if (difference(total) != 0L || items().none { it.memberIds.isNotEmpty() }) return emptyList()
        val withExtras = shares(payerId)
        val itemsOnly = if (extrasIndex < 0) withExtras else {
            val own = items().filterIndexed { i, _ -> i != extrasIndex }
            if (own.none { it.memberIds.isNotEmpty() }) emptyMap() else splitByItems(own, payerId)
        }
        return (people + withExtras.keys).distinct().mapNotNull { id ->
            val share = withExtras[id] ?: 0
            if (share == 0L) null else PersonShare(id, share, share - (itemsOnly[id] ?: 0))
        }
    }

    companion object {
        /**
         * The split of a saved "Per item" expense: its lines and who shares each; people on a line who are no longer
         * members are [locked].
         */
        fun of(items: List<ExpenseItemDto>, memberIds: List<String>): ItemSplit {
            // the tax-and-service line comes last, without members (a free line elsewhere may have none too)
            val extras = if (items.lastOrNull()?.memberIds?.isEmpty() == true) items.lastIndex else -1
            val initial = items.withIndex().filter { it.index != extras }.associate { it.index to it.value.memberIds.toSet() }
            val gone = items.flatMap { it.memberIds }.filter { it !in memberIds }.toSet()
            return ItemSplit(items.map { it.copy(memberIds = emptyList()) }, extras, memberIds, initial, gone)
        }
    }
}

/** One member's part of a split per item; [extrasMinor] is the tax and service inside [totalMinor]. */
data class PersonShare(val memberId: String, val totalMinor: Long, val extrasMinor: Long)
