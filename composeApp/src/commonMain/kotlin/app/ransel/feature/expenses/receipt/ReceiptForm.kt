package app.ransel.feature.expenses.receipt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ransel.shared.domain.itemsTotal
import app.ransel.shared.domain.splitByItems
import app.ransel.shared.domain.splitEqually
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ReceiptDraftDto
import app.ransel.shared.model.Category
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * The "Cek hasil struk" form (design "HasilStruk"): what was read, who paid, and "Bagi rata" or "Per item".
 * Tax and service become one line without members, spread in proportion (domain.splitByItems), unless the receipt
 * already counts them in its prices ("Incl. Pb1 10%": the items alone make the total); then they are only a note.
 * Save waits until the lines add up to the total, like the server's SPLIT_TOTAL_MISMATCH.
 */
class ReceiptFormState(
    val draft: ReceiptDraftDto,
    /** Members who can share; empty = a solo trip (no split). */
    val memberIds: List<String>,
    myMemberId: String?,
    /** Label of the tax-and-service line, in the UI language. */
    extrasLabel: String,
) {
    private val extras = draft.taxMinor + draft.serviceMinor

    /**
     * Tax and service already inside the item prices: the items alone make the total, adding the extras would not.
     * Shown as "Termasuk pajak …", never as a line to split.
     */
    val includedExtrasMinor: Long =
        if (extras > 0 && draft.items.sumOf { it.amountMinor } == draft.totalMinor) extras else 0

    val lines: List<ExpenseItemDto> = buildList {
        draft.items.forEach { add(ExpenseItemDto(it.name, it.amountMinor)) }
        if (extras > 0 && includedExtrasMinor == 0L) add(ExpenseItemDto(extrasLabel, extras))
    }

    /** Index of the tax-and-service line (added on top of the items), or -1. */
    val extrasIndex: Int = if (extras > 0 && includedExtrasMinor == 0L) lines.lastIndex else -1

    var perItem by mutableStateOf(false)
    var payer by mutableStateOf(myMemberId ?: memberIds.firstOrNull())
    var category by mutableStateOf(Category.FOOD)
    var place by mutableStateOf(draft.placeName.orEmpty())

    /** The day read from the receipt; editable, since the reading can be wrong (and a ticket may be bought earlier). */
    var date by mutableStateOf(draft.spentOn)

    /** Who shares each line (by index); everyone at first. */
    private val sharers = mutableStateMapOf<Int, Set<String>>().apply {
        lines.indices.filter { it != extrasIndex }.forEach { put(it, memberIds.toSet()) }
    }

    fun sharersOf(index: Int): Set<String> = sharers[index].orEmpty()

    fun toggle(index: Int, memberId: String) {
        val now = sharersOf(index)
        sharers[index] = if (memberId in now) now - memberId else now + memberId
    }

    val total: Long get() = draft.totalMinor

    /** Lines minus the total: 0 when they match, as required for "Per item". */
    val difference: Long get() = itemsTotal(lines) - total

    private fun items(): List<ExpenseItemDto> =
        lines.mapIndexed { i, line -> if (i == extrasIndex) line else line.copy(memberIds = memberIds.filter { it in sharersOf(i) }) }

    /** A line nobody shares cannot be split; tax alone cannot either. */
    val canSave: Boolean get() = when {
        total <= 0 -> false
        memberIds.isEmpty() || !perItem -> true
        else -> difference == 0L && lines.indices.filter { it != extrasIndex }.all { sharersOf(it).isNotEmpty() || lines[it].amountMinor == 0L }
    }

    fun shares(): Map<String, Long> = when {
        memberIds.size < 2 || payer == null -> emptyMap()
        perItem && difference == 0L -> splitByItems(items(), payer!!)
        perItem -> emptyMap()
        else -> splitEqually(total, memberIds, payer!!)
    }

    fun toInput(id: String, spentAt: Instant, fxRateToBase: String?): ExpenseInput = ExpenseInput(
        id = id,
        amountMinor = total,
        currency = draft.currency,
        fxRateToBase = fxRateToBase,
        category = category,
        note = place.trim().ifEmpty { null },
        spentAt = spentAt,
        paidByMemberId = payer,
        splits = shares().filterValues { it > 0 }.map { ExpenseSplitDto(it.key, it.value) },
        items = if (perItem && memberIds.size > 1) items() else emptyList(),
    )

    /**
     * "Bagian tiap orang" in "Per item": each member's share and the part of it that is tax and service (spread in
     * proportion). Empty until the lines add up to the total.
     */
    fun perPerson(): List<PersonShare> {
        if (!perItem || memberIds.size < 2 || difference != 0L || payer == null) return emptyList()
        val withExtras = shares()
        val itemsOnly = if (extrasIndex < 0) withExtras else {
            val own = items().filterIndexed { i, _ -> i != extrasIndex }
            if (own.none { it.memberIds.isNotEmpty() }) emptyMap() else splitByItems(own, payer!!)
        }
        return memberIds.mapNotNull { id ->
            val share = withExtras[id] ?: 0
            if (share == 0L) null else PersonShare(id, share, share - (itemsOnly[id] ?: 0))
        }
    }
}

/** One member's part of a receipt split per item; [extrasMinor] is the tax and service inside [totalMinor]. */
data class PersonShare(val memberId: String, val totalMinor: Long, val extrasMinor: Long)
