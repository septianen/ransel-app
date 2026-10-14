package app.ransel.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.months_full
import ransel.composeapp.generated.resources.months_short
import ransel.composeapp.generated.resources.weekdays_full
import ransel.composeapp.generated.resources.weekdays_short

/** Calendar text in the app language: "20–23 Nov 2026", "Jum, 20 Nov". Names come from string resources. */
@Immutable
class DateFormatter(
    private val monthsShort: List<String>,
    private val monthsFull: List<String>,
    private val weekdaysShort: List<String>,
    private val weekdaysFull: List<String>,
) {
    private fun month(d: LocalDate) = monthsShort[d.month.ordinal]

    /** "20 Nov" */
    fun dayMonth(d: LocalDate) = "${d.day} ${month(d)}"

    /** "Jum, 20 Nov" */
    fun weekdayDayMonth(d: LocalDate) = "${weekdaysShort[d.dayOfWeek.ordinal]}, ${dayMonth(d)}"

    /** "Jumat, 20 Nov" */
    fun fullWeekdayDayMonth(d: LocalDate) = "${weekdaysFull[d.dayOfWeek.ordinal]}, ${dayMonth(d)}"

    /** "20 Nov 2026" */
    fun dayMonthYear(d: LocalDate) = "${dayMonth(d)} ${d.year}"

    /** "November 2026" */
    fun monthYear(d: LocalDate) = "${monthsFull[d.month.ordinal]} ${d.year}"

    /** "20–23 Nov 2026", "27 Mar – 2 Apr 2027", "30 Des 2026 – 2 Jan 2027". */
    fun range(start: LocalDate, end: LocalDate): String = when {
        start == end -> dayMonthYear(start)
        start.year != end.year -> "${dayMonthYear(start)} – ${dayMonthYear(end)}"
        start.month != end.month -> "${dayMonth(start)} – ${dayMonthYear(end)}"
        else -> "${start.day}–${end.day} ${month(end)} ${end.year}"
    }
}

@Composable
fun rememberDateFormatter(): DateFormatter {
    val monthsShort = stringArrayResource(Res.array.months_short)
    val monthsFull = stringArrayResource(Res.array.months_full)
    val weekdaysShort = stringArrayResource(Res.array.weekdays_short)
    val weekdaysFull = stringArrayResource(Res.array.weekdays_full)
    return remember(monthsShort, monthsFull, weekdaysShort, weekdaysFull) {
        DateFormatter(monthsShort, monthsFull, weekdaysShort, weekdaysFull)
    }
}
