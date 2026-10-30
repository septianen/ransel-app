package app.ransel.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.deadline_time
import ransel.composeapp.generated.resources.months_full
import ransel.composeapp.generated.resources.months_short
import ransel.composeapp.generated.resources.trip_dates_not_set
import ransel.composeapp.generated.resources.weekdays_full
import ransel.composeapp.generated.resources.weekdays_short

/**
 * Calendar text in the app language: "20–23 Nov 2026", "Jum, 20 Nov". Names come from string resources.
 *
 * On the web string resources load asynchronously, so the name lists are empty for the first frame(s);
 * lookups fall back to numbers instead of throwing, and the screen recomposes when the names arrive.
 */
@Immutable
class DateFormatter(
    private val monthsShort: List<String>,
    private val monthsFull: List<String>,
    private val weekdaysShort: List<String>,
    private val weekdaysFull: List<String>,
    /** "Tanggal belum ditetapkan": the dates of a trip that has none yet. */
    private val noDates: String = "",
) {
    private fun month(d: LocalDate) = monthsShort.getOrNull(d.month.ordinal) ?: (d.month.ordinal + 1).toString()
    fun weekdayShort(d: LocalDate) = weekdaysShort.getOrNull(d.dayOfWeek.ordinal) ?: ""
    private fun weekdayFull(d: LocalDate) = weekdaysFull.getOrNull(d.dayOfWeek.ordinal) ?: ""

    /** "20 Nov" */
    fun dayMonth(d: LocalDate) = "${d.day} ${month(d)}"

    /** "Jum, 20 Nov" */
    fun weekdayDayMonth(d: LocalDate) = "${weekdayShort(d)}, ${dayMonth(d)}"

    /** "Jumat, 20 Nov" */
    fun fullWeekdayDayMonth(d: LocalDate) = "${weekdayFull(d)}, ${dayMonth(d)}"

    /** "20 Nov 2026" */
    fun dayMonthYear(d: LocalDate) = "${dayMonth(d)} ${d.year}"

    /** "November 2026" */
    fun monthYear(d: LocalDate) = "${monthsFull.getOrNull(d.month.ordinal) ?: month(d)} ${d.year}"

    /** "20–23 Nov 2026", "27 Mar – 2 Apr 2027", "30 Des 2026 – 2 Jan 2027". */
    fun range(start: LocalDate, end: LocalDate): String = when {
        start == end -> dayMonthYear(start)
        start.year != end.year -> "${dayMonthYear(start)} – ${dayMonthYear(end)}"
        start.month != end.month -> "${dayMonth(start)} – ${dayMonthYear(end)}"
        else -> "${start.day}–${end.day} ${month(end)} ${end.year}"
    }

    /** A trip's dates: the [range], or "Tanggal belum ditetapkan" for a trip without dates. */
    fun tripRange(start: LocalDate?, end: LocalDate?): String =
        if (start != null && end != null) range(start, end) else noDates
}

@Composable
fun rememberDateFormatter(): DateFormatter {
    val monthsShort = stringArrayResource(Res.array.months_short)
    val monthsFull = stringArrayResource(Res.array.months_full)
    val weekdaysShort = stringArrayResource(Res.array.weekdays_short)
    val weekdaysFull = stringArrayResource(Res.array.weekdays_full)
    val noDates = app.ransel.content.stringResource(Res.string.trip_dates_not_set)
    return remember(monthsShort, monthsFull, weekdaysShort, weekdaysFull, noDates) {
        DateFormatter(monthsShort, monthsFull, weekdaysShort, weekdaysFull, noDates)
    }
}

/** "Sen 21.00" / "Mon 21:00": a deadline in [zone]. */
@androidx.compose.runtime.Composable
fun deadlineText(at: kotlin.time.Instant, zone: kotlinx.datetime.TimeZone): String {
    val dates = rememberDateFormatter()
    val local = at.toLocalDateTime(zone)
    return app.ransel.content.stringResource(
        Res.string.deadline_time,
        dates.weekdayShort(local.date), local.hour.toString().padStart(2, '0'), local.minute.toString().padStart(2, '0'),
    )
}
