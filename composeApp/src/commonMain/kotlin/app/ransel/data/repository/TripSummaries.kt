package app.ransel.data.repository

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
    val days = trip.startDate.daysUntil(trip.endDate) + 1
    return TripSummaryDto(
        daysTotal = days,
        daysFilled = scheduled.mapNotNull { it.dayDate }.distinct().count {
            it >= trip.startDate && it <= trip.endDate
        },
        activityCount = scheduled.size,
        unscheduledCount = liveItems.size - scheduled.size,
        packingTotal = livePacking.size,
        packingPacked = livePacking.count { it.isPacked },
        expenseCount = liveExpenses.size,
        spentMinor = money.spentMinor,
        remainingMinor = money.remainingMinor,
    )
}
