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
    /** Who paid (a trip_members.id). With several [payers] this is the first of them, so older clients still show a name. */
    val paidByMemberId: String,
    /**
     * Empty = [paidByMemberId] paid all of it. Otherwise two or more people paid parts of it (the bill was
     * paid by several): amounts are in [currency] and sum to amountMinor.
     */
    val payers: List<ExpensePayerDto> = emptyList(),
    /** Empty = paid and carried fully by paidByMemberId (solo). Shares are in [currency] and sum to amountMinor. */
    val splits: List<ExpenseSplitDto> = emptyList(),
    /** "Ambil dari kas": paid with the kitty's money. [paidByMemberId] is then the treasurer who holds it. */
    val paidFromKitty: Boolean = false,
    /** A planned cost, not a payment yet (e.g. the stay chosen in a poll). Left out of balances and of spentMinor. */
    val isEstimate: Boolean = false,
    /** "Per item" split from a receipt, in list order. Empty = no items; the splits are then the whole truth. */
    val items: List<ExpenseItemDto> = emptyList(),
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)

/** One person's part of what was paid for an expense, in the expense's currency. */
@Serializable
data class ExpensePayerDto(
    val memberId: String,
    val amountMinor: Long,
)

@Serializable
data class ExpenseSplitDto(
    val memberId: String,
    val shareMinor: Long,
)

/**
 * One line of a receipt, in the expense's currency. [memberIds] = who shares it; empty = tax, service or another
 * extra that is spread over everyone in proportion to their items (domain.splitByItems).
 */
@Serializable
data class ExpenseItemDto(
    val name: String,
    val amountMinor: Long,
    val memberIds: List<String> = emptyList(),
)

/**
 * POST /trips/{id}/expenses (create) and PUT /trips/{id}/expenses/{expenseId} (replace; splits replaced whole).
 * Validation: amountMinor > 0; currency ^[A-Z]{3}$; fxRateToBase > 0 when set;
 * splits empty or sum(shareMinor) == amountMinor (else 422 SPLIT_TOTAL_MISMATCH).
 * paidByMemberId null = the caller's own member (domain.memberIdFor). payers empty = paidByMemberId paid all; otherwise at least two different
 * members with amount > 0 summing to amountMinor (else 422 PAYER_TOTAL_MISMATCH), and paidByMemberId becomes the first.
 * paidFromKitty: the trip must have a kitty (else 422 paidFromKitty = NO_KITTY), payers must be empty, and the server sets
 * paidByMemberId to the kitty's treasurer. items: they must add up to amountMinor (else 422 SPLIT_TOTAL_MISMATCH) and the
 * server replaces the splits with domain.splitByItems. Saving an estimate with isEstimate = false turns it into the real payment.
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
    val paidByMemberId: String? = null,
    val payers: List<ExpensePayerDto> = emptyList(),
    val splits: List<ExpenseSplitDto> = emptyList(),
    val paidFromKitty: Boolean = false,
    val isEstimate: Boolean = false,
    val items: List<ExpenseItemDto> = emptyList(),
)

/** GET /trips/{id}/expenses/summary. Everything in baseCurrency. */
@Serializable
data class ExpenseSummaryDto(
    val baseCurrency: String,
    val budgetMinor: Long? = null,
    val spentMinor: Long,
    val remainingMinor: Long? = null,
    /** Estimates (ExpenseDto.isEstimate), not part of spentMinor, byCategory or byDay. */
    val estimatedMinor: Long = 0,
    /** Every Category, zero included, so the donut legend is stable. */
    val byCategory: List<CategoryTotalDto>,
    /** Only days that have expenses, ascending. Day = spentAt in the trip's local date (client time zone on mobile). */
    val byDay: List<DayTotalDto>,
)

@Serializable
data class CategoryTotalDto(val category: Category, val amountMinor: Long)

@Serializable
data class DayTotalDto(val date: LocalDate, val amountMinor: Long)
