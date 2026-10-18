package app.ransel.shared.dto

import app.ransel.shared.model.Category
import app.ransel.shared.model.PaymentMethod
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseDto(
    val id: String,
    val tripId: String,
    /** In the minor unit of [currency]. Always > 0. */
    val amountMinor: Long,
    val currency: String,
    /**
     * 1 major unit of [currency] = x major units of the trip baseCurrency, fixed when recorded.
     * Decimal as a string (DB NUMERIC(20,10)), e.g. "16250.5". null when currency == baseCurrency.
     */
    val fxRateToBase: String? = null,
    val category: Category = Category.OTHER,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val note: String? = null,
    /** May fall outside the trip dates. */
    val spentAt: Timestamp,
    /** Who paid. With several [payers] this is the first of them, so older clients still show a name. */
    val paidByUserId: String,
    /**
     * Empty = [paidByUserId] paid all of it. Otherwise two or more people paid parts of it (the bill was
     * paid by several): amounts are in [currency] and sum to amountMinor.
     */
    val payers: List<ExpensePayerDto> = emptyList(),
    /** Empty = paid and carried fully by paidByUserId (solo). Shares are in [currency] and sum to amountMinor. */
    val splits: List<ExpenseSplitDto> = emptyList(),
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)

/** One person's part of what was paid for an expense, in the expense's currency. */
@Serializable
data class ExpensePayerDto(
    val userId: String,
    val amountMinor: Long,
)

@Serializable
data class ExpenseSplitDto(
    val userId: String,
    val shareMinor: Long,
)

/**
 * POST /trips/{id}/expenses (create) and PUT /trips/{id}/expenses/{expenseId} (replace; splits replaced whole).
 * Validation: amountMinor > 0; currency ^[A-Z]{3}$; fxRateToBase > 0 when set;
 * splits empty or sum(shareMinor) == amountMinor (else 422 SPLIT_TOTAL_MISMATCH).
 * paidByUserId null = the calling user. payers empty = paidByUserId paid all; otherwise at least two different
 * members with amount > 0 summing to amountMinor (else 422 PAYER_TOTAL_MISMATCH), and paidByUserId becomes the first.
 */
@Serializable
data class ExpenseInput(
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val fxRateToBase: String? = null,
    val category: Category = Category.OTHER,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val note: String? = null,
    val spentAt: Timestamp,
    val paidByUserId: String? = null,
    val payers: List<ExpensePayerDto> = emptyList(),
    val splits: List<ExpenseSplitDto> = emptyList(),
)

/** GET /trips/{id}/expenses/summary. Everything in baseCurrency. */
@Serializable
data class ExpenseSummaryDto(
    val baseCurrency: String,
    val budgetMinor: Long? = null,
    val spentMinor: Long,
    val remainingMinor: Long? = null,
    /** Every Category, zero included, so the donut legend is stable. */
    val byCategory: List<CategoryTotalDto>,
    /** Only days that have expenses, ascending. Day = spentAt in the trip's local date (client time zone on mobile). */
    val byDay: List<DayTotalDto>,
)

@Serializable
data class CategoryTotalDto(val category: Category, val amountMinor: Long)

@Serializable
data class DayTotalDto(val date: LocalDate, val amountMinor: Long)
