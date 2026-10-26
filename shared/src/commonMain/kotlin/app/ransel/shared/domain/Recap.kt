package app.ransel.shared.domain

import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.model.ItemSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

/** "Rekap trip" (PRD P2 "Trip recap"): the numbers on the recap card. Money in the trip's base currency. */
data class TripRecap(
    /** Scheduled activities with a place, each place once. */
    val placesVisited: Int,
    val days: Int,
    val totalMinor: Long,
    /** [totalMinor] over the people who shared costs (at least 1). */
    val perPersonMinor: Long,
    val people: Int,
    /** Up to three: places picked in Jelajah first, then the others, in the order of the trip. */
    val favourites: List<String>,
)

const val RECAP_FAVOURITES = 3

/**
 * The recap of a trip. Estimates are left out (as in the expense summary). The people are everyone who paid or had a
 * share in an expense; a trip without shared costs counts one person.
 */
fun tripRecap(
    startDate: LocalDate,
    endDate: LocalDate,
    baseCurrency: String,
    items: List<ItineraryItemDto>,
    expenses: List<ExpenseDto>,
    timeZone: TimeZone,
): TripRecap {
    val visited = items
        .filter { it.deletedAt == null && it.dayDate != null && it.dayDate in startDate..endDate && !it.placeName.isNullOrBlank() }
        .sortedWith(compareBy({ it.dayDate }, { it.startTime }, { it.sortOrder }))
    val places = visited.distinctBy { it.placeId ?: it.placeName!!.trim().lowercase() }
    val total = summarizeExpenses(baseCurrency, null, expenses, timeZone).spentMinor
    val people = expenses.filter { it.deletedAt == null && !it.isEstimate }
        .flatMap { e -> listOf(e.paidByMemberId) + e.splits.map { it.memberId } + e.payers.map { it.memberId } }
        .toSet().size.coerceAtLeast(1)
    val favourites = (places.filter { it.source == ItemSource.AI } + places.filter { it.source != ItemSource.AI })
        .map { it.placeName!!.trim() }
        .take(RECAP_FAVOURITES)
    return TripRecap(
        placesVisited = places.size,
        days = startDate.daysUntil(endDate) + 1,
        totalMinor = total,
        perPersonMinor = total / people,
        people = people,
        favourites = favourites,
    )
}

/** The recap shows from the day after the last day of the trip. */
fun recapAvailable(endDate: LocalDate, today: LocalDate): Boolean = today > endDate
