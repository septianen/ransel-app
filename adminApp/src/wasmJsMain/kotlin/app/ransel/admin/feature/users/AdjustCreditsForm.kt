package app.ransel.admin.feature.users

import app.ransel.shared.dto.AdjustCreditsRequest
import app.ransel.shared.model.CreditDirection

/** What blocks saving; the dialog shows the matching copy. */
enum class AdjustProblem {
    /** Subtracting more than the wallet holds ("Saldo tidak bisa minus. Paling banyak <n> kredit."). */
    EXCEEDS_BALANCE,
    AMOUNT_RANGE,
    REASON_LENGTH,
}

/**
 * The "Atur kredit" form (admin-spec "Adjust credits"). [requestId] is made once per dialog opening, so a retry
 * after a timeout sends the same id and the server changes the balance at most once.
 */
data class AdjustCreditsForm(
    val requestId: String,
    val balance: Int,
    val direction: CreditDirection = CreditDirection.ADD,
    val amountText: String = "",
    val reason: String = "",
) {
    val amount: Int? get() = amountText.trim().toIntOrNull()

    /** Balance after saving, or null while the amount is missing or would make it negative. */
    val balanceAfter: Int?
        get() {
            val n = amount ?: return null
            val after = if (direction == CreditDirection.ADD) balance + n else balance - n
            return after.takeIf { it >= 0 }
        }

    val exceedsBalance: Boolean
        get() = direction == CreditDirection.SUBTRACT && (amount ?: 0) > balance

    /** The first problem to show next to the fields; null when the form can be sent. */
    val problem: AdjustProblem?
        get() {
            val n = amount
            return when {
                exceedsBalance -> AdjustProblem.EXCEEDS_BALANCE
                n == null || n !in AMOUNT_RANGE -> AdjustProblem.AMOUNT_RANGE
                reason.trim().length !in REASON_LENGTH -> AdjustProblem.REASON_LENGTH
                else -> null
            }
        }

    val canSave: Boolean get() = problem == null

    fun request(): AdjustCreditsRequest = AdjustCreditsRequest(
        requestId = requestId,
        direction = direction,
        amount = requireNotNull(amount),
        reason = reason.trim(),
    )

    companion object {
        val PRESETS = listOf(1, 5, 10, 30)
        val AMOUNT_RANGE = 1..10_000
        val REASON_LENGTH = 3..200
    }
}
