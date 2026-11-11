package app.ransel.feature.expenses.receipt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ReceiptDraftDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.feature.expenses.ItemSplit
import app.ransel.feature.expenses.PersonShare
import app.ransel.feature.expenses.SplitState
import app.ransel.shared.domain.itemsTotal
import app.ransel.shared.model.Category
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * The "Cek hasil struk" form (design "HasilStruk"): what was read, then the same "Dibayar oleh" / "Dibagi ke" as the
 * manual form ([SplitState]: who paid, who takes part, "Bagi rata" or "Atur nominal"), plus "Per item" when the
 * receipt has lines (owner, 27 Oct 2026: the two forms must not differ). "Bagi rata" is the default, as in the manual form.
 * Tax and service become one line without members, spread in proportion (domain.splitByItems), unless the receipt
 * already counts them in its prices ("Incl. Pb1 10%": the items alone make the total); then they are only a note.
 *
 * Each line's name and amount can be changed, deleted, or added ("Tambah item"). The amount saved is always the sum
 * of those lines, including when there are none (then it is 0 and Save stays off). Tax alone cannot be saved: there
 * must be one non-tax line above 0. [scannedTotal] is only a reference on this screen: a difference is a warning and
 * never blocks Save, and it is not stored on the expense. Fixing a line does not read the photo again and does not
 * spend a credit.
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
     * Tax and service already inside the item prices: the items alone make the printed total, adding the extras would not.
     * Shown as "Termasuk pajak …", never as a line to split. Stays a note even after a line is edited.
     */
    val includedExtrasMinor: Long =
        if (extras > 0 && draft.items.sumOf { it.amountMinor } == draft.totalMinor) extras else 0

    /** The rate in the Pb1 note: the phrase the receipt printed, or "10%" when it did not. Never an amount. */
    val includedTaxPhrase: String = includedPb1Phrase(draft.includedTaxRate)

    /** What the receipt printed. Not the amount that is saved. */
    val scannedTotal: Long = draft.totalMinor

    /**
     * Tax added on top becomes a line only when the receipt also has items. A photo that only yielded a total has no
     * lines: the amount stays 0 until the user adds one, and Save stays off.
     */
    private val extrasOnTop = extras > 0 && includedExtrasMinor == 0L && draft.items.isNotEmpty()

    /** Who shares each line; everyone at first. The tax line, when it is added on top, has no members. */
    val itemSplit = ItemSplit(
        lines = buildList {
            draft.items.forEach { add(ExpenseItemDto(it.name, it.amountMinor)) }
            if (extrasOnTop) add(ExpenseItemDto(extrasLabel, extras))
        },
        extrasIndex = if (extrasOnTop) draft.items.size else -1,
        memberIds = memberIds,
    )

    val lines: List<ExpenseItemDto> get() = itemSplit.lines

    /** Index of the tax-and-service line (added on top of the items), or -1. */
    val extrasIndex: Int get() = itemSplit.extrasIndex

    /** "Per item" is only offered when there is an item line; "Bagi rata" comes first. */
    val canSplitPerItem: Boolean get() = lines.indices.any { it != extrasIndex }

    var perItem by mutableStateOf(false)
    var category by mutableStateOf(Category.FOOD)
    var place by mutableStateOf(draft.placeName.orEmpty())

    /** The day read from the receipt; editable, since the reading can be wrong (and a ticket may be bought earlier). */
    var date by mutableStateOf(draft.spentOn)

    fun sharersOf(index: Int): Set<String> = itemSplit.sharersOf(index)

    fun toggle(index: Int, memberId: String) = itemSplit.toggle(index, memberId)

    /** Sum of the lines. 0 when there are none: Save stays off, and the printed total is not used instead. */
    val total: Long get() = itemsTotal(lines)

    /** Line sum minus the printed total. Never blocks Save, and only the scan screen shows it. */
    val scannedGap: Long get() = total - scannedTotal

    /**
     * Save needs one non-tax line above 0, a name on every line that counts, and a valid split.
     * The printed total is not checked. Tax alone cannot be saved.
     */
    val canSave: Boolean get() {
        if (!itemSplit.linesReady()) return false
        val s = split ?: return true
        return if (!perItem) s.isValid(total, draft.currency) else s.payersValid(total, draft.currency) && itemSplit.isValid(total)
    }

    fun shares(): Map<String, Long> {
        val s = split ?: return emptyMap()
        return when {
            perItem && itemSplit.isValid(total) -> itemSplit.shares(s.paidBy)
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
        // "Per item" sends who shares each line; otherwise the lines are kept only to be shown on the expense.
        // An empty new line is left out. The amount is this sum, so the server's SPLIT_TOTAL_MISMATCH passes.
        items = itemSplit.items().map { line -> if (perItem && split != null) line else line.copy(memberIds = emptyList()) },
        itemsSplit = perItem && split != null,
    )

    /**
     * "Bagian tiap orang" in "Per item": each member's share and the part of it that is tax and service (spread in
     * proportion). Follows the lines being saved, not the printed total.
     */
    fun perPerson(): List<PersonShare> {
        val s = split
        if (!perItem || s == null) return emptyList()
        return itemSplit.perPerson(s.paidBy, total, memberIds)
    }
}

private val PB1_RATE = Regex("""\d{1,2}(?:[.,]\d+)?%""")

/**
 * The "%1$s" of "Incl. Pb1 %1$s". A stored phrase that contains a rate ("10%", "Incl. Pb1 10%") wins.
 * Anything else, including a rupiah amount, becomes "10%".
 */
fun includedPb1Phrase(stored: String?): String {
    val phrase = stored?.trim().orEmpty()
    return PB1_RATE.find(phrase)?.value?.replace(" ", "") ?: "10%"
}
