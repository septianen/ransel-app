package app.ransel.data.repository

import app.ransel.shared.domain.dayFitsTrip
import app.ransel.shared.domain.summarizeExpenses
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

/**
 * Numbers for Home "Persiapan trip", My trips cards and the delete dialog. Computed, never stored.
 * `activityCount` = scheduled activities; unscheduled ones are counted in `unscheduledCount`.
 */
fun buildTripSummary(
    trip: TripDto,
    items: List<ItineraryItemDto>,
    packing: List<PackingItemDto>,
    expenses: List<ExpenseDto>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): TripSummaryDto {
    val liveItems = items.filter { it.deletedAt == null }
    val scheduled = liveItems.filter { it.dayDate != null }
    val livePacking = packing.filter { it.deletedAt == null }
    val liveExpenses = expenses.filter { it.deletedAt == null }
    val money = summarizeExpenses(trip.baseCurrency, trip.budgetMinor, liveExpenses, timeZone)
    // A trip without dates has no days yet (every item is unscheduled): 0 of 0.
    val days = trip.startDate?.let { start -> trip.endDate?.let { end -> start.daysUntil(end) + 1 } } ?: 0
    return TripSummaryDto(
        daysTotal = days,
        daysFilled = scheduled.mapNotNull { it.dayDate }.distinct().count { dayFitsTrip(it, trip.startDate, trip.endDate) },
        activityCount = scheduled.size,
        unscheduledCount = liveItems.size - scheduled.size,
        packingTotal = livePacking.size,
        packingPacked = livePacking.count { it.isPacked },
        expenseCount = liveExpenses.size,
        spentMinor = money.spentMinor,
        remainingMinor = money.remainingMinor,
    )
}
