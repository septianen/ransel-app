package app.ransel.feature.expenses.receipt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ransel.shared.domain.itemsTotal
import app.ransel.shared.domain.splitByItems
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ReceiptDraftDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.feature.expenses.SplitState
import app.ransel.shared.model.Category
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * The "Cek hasil struk" form (design "HasilStruk"): what was read, then the same "Dibayar oleh" / "Dibagi ke" as the
 * manual form ([SplitState]: who paid, who takes part, "Bagi rata" or "Atur nominal"), plus "Per item" when the
 * receipt has lines (owner, 27 Oct 2026: the two forms must not differ). "Bagi rata" is the default, as in the manual form.
 * Tax and service become one line without members, spread in proportion (domain.splitByItems), unless the receipt
 * already counts them in its prices ("Incl. Pb1 10%": the items alone make the total); then they are only a note.
 * Save waits until the lines add up to the total, like the server's SPLIT_TOTAL_MISMATCH.
 */
class ReceiptFormState(
    val draft: ReceiptDraftDto,
    /** Trip members; one or none = a solo trip (no split). */
    members: List<TripMemberDto>,
    private val myMemberId: String?,
    /** Label of the tax-and-service line, in the UI language. */
    extrasLabel: String,
) {
    private val extras = draft.taxMinor + draft.serviceMinor

    /** Who paid and who takes part, as in the manual form; null on a solo trip. */
    val split: SplitState? = if (members.size > 1) SplitState(members, myMemberId, null, draft.currency) else null

    /** Members who can share; empty = a solo trip. */
    val memberIds: List<String> = split?.members?.map { it.memberId }.orEmpty()

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

    /** "Per item" is only offered when the receipt has lines; "Bagi rata" comes first. */
    val canSplitPerItem: Boolean = draft.items.isNotEmpty()

    var perItem by mutableStateOf(false)
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

    /** A line nobody shares cannot be split; tax alone cannot either. Equal and set amounts check like the manual form. */
    val canSave: Boolean get() {
        val s = split
        return when {
            total <= 0 -> false
            s == null -> true
            !perItem -> s.isValid(total, draft.currency)
            else -> s.payersValid(total, draft.currency) && difference == 0L &&
                lines.indices.filter { it != extrasIndex }.all { sharersOf(it).isNotEmpty() || lines[it].amountMinor == 0L }
        }
    }

    fun shares(): Map<String, Long> {
        val s = split ?: return emptyMap()
        return when {
            perItem && difference == 0L -> splitByItems(items(), s.paidBy)
            perItem -> emptyMap()
            else -> s.shares(total, draft.currency)
        }
    }

    fun toInput(id: String, spentAt: Instant, fxRateToBase: String?): ExpenseInput = ExpenseInput(
        id = id,
        amountMinor = total,
        currency = draft.currency,
        fxRateToBase = fxRateToBase,
        category = category,
        note = place.trim().ifEmpty { null },
        spentAt = spentAt,
        paidByMemberId = split?.paidBy ?: myMemberId,
        payers = split?.toPayers(total, draft.currency).orEmpty(),
        // "Bagi rata" with only the payer ticked is a personal expense (no splits), as in the manual form
        splits = when {
            split == null -> emptyList()
            perItem -> shares().filterValues { it > 0 }.map { ExpenseSplitDto(it.key, it.value) }
            else -> split.toSplits(total, draft.currency)
        },
        // "Per item" sends who shares each line; otherwise the lines are kept only to be shown on the expense
        items = if (perItem && split != null) items() else lines.map { it.copy(memberIds = emptyList()) },
        itemsSplit = perItem && split != null,
    )

    /**
     * "Bagian tiap orang" in "Per item": each member's share and the part of it that is tax and service (spread in
     * proportion). Empty until the lines add up to the total.
     */
    fun perPerson(): List<PersonShare> {
        val s = split
        if (!perItem || s == null || difference != 0L) return emptyList()
        val withExtras = shares()
        val itemsOnly = if (extrasIndex < 0) withExtras else {
            val own = items().filterIndexed { i, _ -> i != extrasIndex }
            if (own.none { it.memberIds.isNotEmpty() }) emptyMap() else splitByItems(own, s.paidBy)
        }
        return memberIds.mapNotNull { id ->
            val share = withExtras[id] ?: 0
            if (share == 0L) null else PersonShare(id, share, share - (itemsOnly[id] ?: 0))
        }
    }
}

/** One member's part of a receipt split per item; [extrasMinor] is the tax and service inside [totalMinor]. */
data class PersonShare(val memberId: String, val totalMinor: Long, val extrasMinor: Long)
