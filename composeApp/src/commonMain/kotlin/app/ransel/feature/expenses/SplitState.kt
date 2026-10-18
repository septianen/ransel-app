package app.ransel.feature.expenses

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ransel.shared.domain.splitDifference
import app.ransel.shared.domain.splitEqually
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpensePayerDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.ui.format.amountToInput
import app.ransel.ui.format.parseAmountMinor

/**
 * Who paid and how the amount is shared (design "Dibayar oleh" / "Dibagi ke": equal, or "Atur nominal").
 * One or more people can have paid: with two or more, "who paid how much" is split the same two ways.
 * The arithmetic lives in `shared/domain` (`splitEqually`, `splitDifference`); this only holds the form state.
 */
class SplitState(
    val members: List<TripMemberDto>,
    myUserId: String?,
    existing: ExpenseDto?,
    currency: String,
) {
    /** Who paid, in the order they were ticked; the first one is "who paid" for older clients. Never empty. */
    val payers = mutableStateListOf<String>()
    val paidBy: String get() = payers.first()
    var payersCustom by mutableStateOf(false)
    /** Typed amounts in "Atur nominal" mode for the payers, by user id. */
    val payerTexts = mutableStateMapOf<String, String>()

    val participants = mutableStateListOf<String>()
    var custom by mutableStateOf(false)
    /** Typed shares in "Atur nominal" mode, by user id. */
    val shareTexts = mutableStateMapOf<String, String>()

    init {
        val savedPayers = existing?.payers.orEmpty()
        if (savedPayers.isEmpty()) {
            payers.add(existing?.paidByUserId ?: myUserId ?: members.first().userId)
        } else {
            payers.addAll(savedPayers.map { it.userId })
            val equal = splitEqually(existing!!.amountMinor, payers.toList(), payers.first())
            if (savedPayers.any { equal[it.userId] != it.amountMinor }) {
                payersCustom = true
                savedPayers.forEach { payerTexts[it.userId] = amountToInput(it.amountMinor, currency) }
            }
        }

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

    // ── who paid ────────────────────────────────────────────────────────────

    /** Ticks or unticks a payer; at least one stays ticked. */
    fun togglePayer(userId: String) {
        if (userId in payers) {
            if (payers.size > 1) payers.remove(userId)
        } else {
            payers.add(userId)
        }
    }

    fun usePayersCustom(amountMinor: Long?, currency: String) {
        if (payersCustom) return
        payersCustom = true
        // Start from the equal amounts so the numbers are already close to right.
        equalPayerAmounts(amountMinor).forEach { (id, amount) -> payerTexts[id] = amountToInput(amount, currency) }
    }

    fun usePayersEqual() {
        payersCustom = false
    }

    private fun equalPayerAmounts(amountMinor: Long?): Map<String, Long> {
        if (amountMinor == null || amountMinor <= 0 || payers.isEmpty()) return emptyMap()
        return splitEqually(amountMinor, payers.toList(), payers.first())
    }

    private fun typedPayerAmounts(currency: String): Map<String, Long> =
        payers.associateWith { parseAmountMinor(payerTexts[it].orEmpty(), currency) ?: 0L }

    /** What each ticked payer paid; with one payer that is the whole amount. */
    fun payerAmounts(amountMinor: Long?, currency: String): Map<String, Long> = when {
        payers.size == 1 -> amountMinor?.let { mapOf(payers.first() to it) }.orEmpty()
        payersCustom -> typedPayerAmounts(currency)
        else -> equalPayerAmounts(amountMinor)
    }

    /** Positive = payers still short, negative = over, 0 = balanced (always 0 for one payer or equal amounts). */
    fun payerDifference(amountMinor: Long, currency: String): Long =
        if (payers.size > 1 && payersCustom) splitDifference(amountMinor, typedPayerAmounts(currency).values) else 0L

    private fun payersValid(amountMinor: Long, currency: String): Boolean =
        payerDifference(amountMinor, currency) == 0L && payerAmounts(amountMinor, currency).values.all { it > 0 }

    // ── who owes ────────────────────────────────────────────────────────────

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
        participants.isNotEmpty() && amountMinor != null && amountMinor > 0 &&
            difference(amountMinor, currency) == 0L && payersValid(amountMinor, currency)

    /** Splits to send; empty when the payer carries it alone ("pribadi"). */
    fun toSplits(amountMinor: Long, currency: String): List<ExpenseSplitDto> {
        if (participants.toSet() == setOf(paidBy)) return emptyList()
        return shares(amountMinor, currency)
            .filterValues { it > 0 }
            .map { (id, share) -> ExpenseSplitDto(id, share) }
    }

    /** Payers to send: empty for a single payer (then `paidByUserId` is the only one), else who paid how much. */
    fun toPayers(amountMinor: Long, currency: String): List<ExpensePayerDto> {
        if (payers.size < 2) return emptyList()
        val amounts = payerAmounts(amountMinor, currency)
        return payers.map { ExpensePayerDto(it, amounts[it] ?: 0L) }
    }
}
