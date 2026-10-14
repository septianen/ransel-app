package app.ransel.shared.domain

import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.model.Category
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ExpenseSummaryTest {
    private val now = Instant.parse("2026-11-20T01:00:00Z")

    private fun expense(
        id: String,
        amount: Long,
        category: Category = Category.FOOD,
        currency: String = "IDR",
        fx: String? = null,
        spentAt: Instant = now,
        deleted: Boolean = false,
    ) = ExpenseDto(
        id = id, tripId = "t", amountMinor = amount, currency = currency, fxRateToBase = fx,
        category = category, spentAt = spentAt, paidByUserId = "u",
        createdAt = now, updatedAt = now, deletedAt = if (deleted) now else null,
    )

    @Test
    fun totalsRemainingAndEveryCategoryPresent() {
        val s = summarizeExpenses(
            "IDR", 5_000_000,
            listOf(expense("1", 100_000), expense("2", 50_000, Category.TRANSPORT)),
            TimeZone.UTC,
        )
        assertEquals(150_000, s.spentMinor)
        assertEquals(4_850_000, s.remainingMinor)
        assertEquals(Category.entries.size, s.byCategory.size)
        assertEquals(100_000, s.byCategory.first { it.category == Category.FOOD }.amountMinor)
        assertEquals(0, s.byCategory.first { it.category == Category.LODGING }.amountMinor)
    }

    @Test
    fun deletedExpensesAreIgnored() {
        val s = summarizeExpenses("IDR", null, listOf(expense("1", 100_000), expense("2", 999, deleted = true)), TimeZone.UTC)
        assertEquals(100_000, s.spentMinor)
        assertEquals(null, s.remainingMinor)
    }

    @Test
    fun foreignCurrencyUsesStoredRate() {
        // USD 10,00 at 16.000 IDR per USD
        val s = summarizeExpenses("IDR", null, listOf(expense("1", 1_000, currency = "USD", fx = "16000")), TimeZone.UTC)
        assertEquals(160_000, s.spentMinor)
    }

    @Test
    fun daysFollowTheGivenTimeZone() {
        val lateUtc = Instant.parse("2026-11-20T20:00:00Z") // 21 Nov 03:00 in WIB
        val s = summarizeExpenses("IDR", null, listOf(expense("1", 10, spentAt = lateUtc)), TimeZone.of("Asia/Jakarta"))
        assertEquals(LocalDate(2026, 11, 21), s.byDay.single().date)
    }
}
