package app.ransel.ui.components.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** The first day of the month of this date. */
fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)

fun LocalDate.plusMonths(months: Int): LocalDate = plus(DatePeriod(months = months))

/** Number of months from [from] to [to] (both any day of their month); 0 when in the same month. */
fun monthsBetween(from: LocalDate, to: LocalDate): Int = (to.year - from.year) * 12 + (to.month.ordinal - from.month.ordinal)

/**
 * The cells of the month that contains [anyDay], Monday first: null pads the first week before day 1 and the last
 * week after the last day, so the list always holds whole weeks (a multiple of 7).
 */
fun monthCells(anyDay: LocalDate): List<LocalDate?> {
    val first = anyDay.firstOfMonth()
    val length = monthsLength(first)
    val lead = first.dayOfWeek.ordinal // Monday = 0
    val cells = ArrayList<LocalDate?>()
    repeat(lead) { cells.add(null) }
    for (d in 0 until length) cells.add(first.plus(DatePeriod(days = d)))
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

private fun monthsLength(first: LocalDate): Int =
    first.plusMonths(1).toEpochDays().toInt() - first.toEpochDays().toInt()

/** [earliest] and [latest] are inclusive; null = no limit. */
fun isSelectable(date: LocalDate, earliest: LocalDate?, latest: LocalDate?): Boolean =
    (earliest == null || date >= earliest) && (latest == null || date <= latest)

/** A selected range; [end] null = only the start is picked (a single day while the user picks the end). */
data class DateSelection(val start: LocalDate? = null, val end: LocalDate? = null) {
    fun contains(date: LocalDate): Boolean {
        val s = start ?: return false
        val e = end ?: s
        return date >= s && date <= e
    }
}

/**
 * Range picking, as the Material3 range picker did: the first tap sets the start, a later day sets the end, an
 * earlier day (or a tap when a full range is set) starts over.
 */
fun tapRange(current: DateSelection, tapped: LocalDate): DateSelection {
    val s = current.start
    return when {
        s == null || current.end != null -> DateSelection(tapped, null)
        tapped < s -> DateSelection(tapped, null)
        else -> DateSelection(s, tapped)
    }
}
