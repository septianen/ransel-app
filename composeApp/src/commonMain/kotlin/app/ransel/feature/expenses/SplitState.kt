package app.ransel.feature.expenses

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ransel.shared.domain.splitDifference
import app.ransel.shared.domain.splitEqually
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.ui.format.amountToInput
import app.ransel.ui.format.parseAmountMinor

/**
 * Who paid and how the amount is shared (design "Dibayar oleh" / "Dibagi ke": equal, or "Atur nominal").
 * The arithmetic lives in `shared/domain` (`splitEqually`, `splitDifference`); this only holds the form state.
 */
class SplitState(
    val members: List<TripMemberDto>,
    myUserId: String?,
    existing: ExpenseDto?,
    currency: String,
) {
    var paidBy by mutableStateOf(existing?.paidByUserId ?: myUserId ?: members.first().userId)
    val participants = mutableStateListOf<String>()
    var custom by mutableStateOf(false)
    /** Typed shares in "Atur nominal" mode, by user id. */
    val shareTexts = mutableStateMapOf<String, String>()

    init {
        val saved = existing?.splits.orEmpty()
        if (saved.isEmpty()) {
            // New expense: everybody takes part. Editing a personal one: only the payer.
            val ids = if (existing == null) members.map { it.userId } else listOf(existing.paidByUserId)
            participants.addAll(ids)
        } else {
            participants.addAll(saved.map { it.userId })
            val equal = splitEqually(existing!!.amountMinor, participants.toList(), paidBy)
            if (saved.any { equal[it.userId] != it.shareMinor }) {
                custom = true
                saved.forEach { shareTexts[it.userId] = amountToInput(it.shareMinor, currency) }
            }
        }
    }

    fun toggle(userId: String) {
        if (userId in participants) participants.remove(userId) else participants.add(userId)
    }

    fun useCustom(amountMinor: Long?, currency: String) {
        if (custom) return
        custom = true
        // Start from the equal split so the numbers are already close to right.
        equalShares(amountMinor).forEach { (id, share) -> shareTexts[id] = amountToInput(share, currency) }
    }

    fun useEqual() {
        custom = false
    }

    /** Equal shares for the ticked members; empty while the amount or the selection is missing. */
    fun equalShares(amountMinor: Long?): Map<String, Long> {
        if (amountMinor == null || amountMinor <= 0 || participants.isEmpty()) return emptyMap()
        return splitEqually(amountMinor, participants.toList(), paidBy)
    }

    private fun typedShares(currency: String): Map<String, Long> =
        participants.associateWith { parseAmountMinor(shareTexts[it].orEmpty(), currency) ?: 0L }

    fun shares(amountMinor: Long?, currency: String): Map<String, Long> =
        if (custom) typedShares(currency) else equalShares(amountMinor)

    /** Positive = still short, negative = over, 0 = balanced (always 0 for an equal split). */
    fun difference(amountMinor: Long, currency: String): Long =
        if (custom) splitDifference(amountMinor, typedShares(currency).values) else 0L

    fun isValid(amountMinor: Long?, currency: String): Boolean =
        participants.isNotEmpty() && amountMinor != null && amountMinor > 0 && difference(amountMinor, currency) == 0L

    /** Splits to send; empty when the payer carries it alone ("pribadi"). */
    fun toSplits(amountMinor: Long, currency: String): List<ExpenseSplitDto> {
        if (participants.toSet() == setOf(paidBy)) return emptyList()
        return shares(amountMinor, currency)
            .filterValues { it > 0 }
            .map { (id, share) -> ExpenseSplitDto(id, share) }
    }
}
