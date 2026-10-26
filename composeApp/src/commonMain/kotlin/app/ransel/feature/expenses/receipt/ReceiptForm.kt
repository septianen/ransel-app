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
 * Tax and service become one line without members, spread in proportion (domain.splitByItems). Save waits until
 * the lines add up to the total, like the server's SPLIT_TOTAL_MISMATCH.
 */
class ReceiptFormState(
    val draft: ReceiptDraftDto,
    /** Members who can share; empty = a solo trip (no split). */
    val memberIds: List<String>,
    myMemberId: String?,
    /** Label of the tax-and-service line, in the UI language. */
    extrasLabel: String,
) {
    val lines: List<ExpenseItemDto> = buildList {
        draft.items.forEach { add(ExpenseItemDto(it.name, it.amountMinor)) }
        val extras = draft.taxMinor + draft.serviceMinor
        if (extras > 0) add(ExpenseItemDto(extrasLabel, extras))
    }

    /** Index of the tax-and-service line, or -1. */
    val extrasIndex: Int = if (draft.taxMinor + draft.serviceMinor > 0) lines.lastIndex else -1

    var perItem by mutableStateOf(false)
    var payer by mutableStateOf(myMemberId ?: memberIds.firstOrNull())
    var category by mutableStateOf(Category.FOOD)
    var place by mutableStateOf(draft.placeName.orEmpty())

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

    val date: LocalDate? get() = draft.spentOn
}
