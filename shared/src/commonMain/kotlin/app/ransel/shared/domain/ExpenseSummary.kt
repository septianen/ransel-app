package app.ransel.shared.domain

import app.ransel.shared.dto.CategoryTotalDto
import app.ransel.shared.dto.DayTotalDto
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseSummaryDto
import app.ransel.shared.model.Category
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Totals for the Expenses screen / `GET /trips/{id}/expenses/summary`, in the trip's base currency.
 * Each expense is converted with its own stored `fxRateToBase` ([convertMinor]), so old totals do not
 * move when rates change. Deleted expenses are ignored; estimates only count in `estimatedMinor`.
 *
 * [timeZone] decides which calendar day an expense belongs to (`spentAt` is UTC): the device zone on
 * mobile, the trip owner's zone on the server.
 */
fun summarizeExpenses(
    baseCurrency: String,
    budgetMinor: Long?,
    expenses: List<ExpenseDto>,
    timeZone: TimeZone,
): ExpenseSummaryDto {
    val live = expenses.filter { it.deletedAt == null }
    val estimated = live.filter { it.isEstimate }.sumOf { convertMinor(it.amountMinor, it.currency, baseCurrency, it.fxRateToBase) }
    val converted = live.filter { !it.isEstimate }.map { it to convertMinor(it.amountMinor, it.currency, baseCurrency, it.fxRateToBase) }
    val spent = converted.sumOf { it.second }

    val byCategory = Category.entries.map { category ->
        CategoryTotalDto(category, converted.filter { it.first.category == category }.sumOf { it.second })
    }
    val byDay = converted
        .groupBy { it.first.spentAt.toLocalDateTime(timeZone).date }
        .map { (date, rows) -> DayTotalDto(date, rows.sumOf { it.second }) }
        .sortedBy { it.date }

    return ExpenseSummaryDto(
        baseCurrency = baseCurrency,
        budgetMinor = budgetMinor,
        spentMinor = spent,
        remainingMinor = budgetMinor?.let { it - spent },
        estimatedMinor = estimated,
        byCategory = byCategory,
        byDay = byDay,
    )
}
