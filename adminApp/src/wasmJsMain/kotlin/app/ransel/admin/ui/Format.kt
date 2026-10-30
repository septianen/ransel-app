package app.ransel.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.months_short
import app.ransel.admin.generated.resources.number_group_separator
import app.ransel.admin.generated.resources.time_days_ago
import app.ransel.admin.generated.resources.time_hours_ago
import app.ransel.admin.generated.resources.time_just_now
import app.ransel.admin.generated.resources.time_minutes_ago
import app.ransel.admin.generated.resources.time_yesterday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/** Karung shows every time in WIB (admin-spec "Screens"). Fixed UTC+7: no tz database needed in the browser. */
val Wib: TimeZone = UtcOffset(hours = 7).asTimeZone()

fun todayWib(): LocalDate = Clock.System.now().toLocalDateTime(Wib).date

/**
 * Dates, times and numbers in the page language. Month names and the digit separator come from the strings
 * (resources load asynchronously on the web, so an empty month list falls back to numbers).
 */
@Immutable
class AdminFormat(
    private val months: List<String>,
    private val groupSeparator: String,
    private val justNow: String,
    private val minutesAgo: String,
    private val hoursAgo: String,
    private val yesterday: String,
    private val daysAgo: String,
) {
    fun number(value: Long): String {
        val digits = kotlin.math.abs(value).toString()
        val grouped = digits.reversed().chunked(3).joinToString(groupSeparator.ifEmpty { "." }).reversed()
        return if (value < 0) "−$grouped" else grouped
    }

    fun number(value: Int): String = number(value.toLong())

    private val decimalSeparator: String get() = if (groupSeparator == ".") "," else "."

    /** [value] with [decimals] digits after the separator, grouped: "1.236", "8,4". */
    fun decimal(value: Double, decimals: Int): String {
        var factor = 1L
        repeat(decimals) { factor *= 10 }
        val scaled = kotlin.math.round(kotlin.math.abs(value) * factor).toLong()
        val whole = number((scaled / factor).toInt()).removePrefix("−")
        val text = if (decimals == 0) whole else whole + decimalSeparator + (scaled % factor).toString().padStart(decimals, '0')
        return if (value < 0 && scaled != 0L) "−$text" else text
    }

    /** Provider money (micro-USD) as Karung shows it: "US$139" for big amounts, "US$88,60", "US$0,04" for one search. */
    fun usd(microUsd: Long, decimals: Int = 2): String = "US$" + decimal(microUsd / 1_000_000.0, decimals)

    /** "41%", rounded; 0 when [whole] is 0. */
    fun percent(part: Number, whole: Number): String {
        val w = whole.toDouble()
        return if (w <= 0.0) "0%" else "${kotlin.math.round(part.toDouble() * 100 / w).toInt()}%"
    }

    /** "+8%" / "−4%" change of [now] against [before]; null when there is nothing to compare with. */
    fun change(now: Int, before: Int): String? {
        if (before <= 0) return null
        val pct = kotlin.math.round((now - before) * 100.0 / before).toInt()
        return if (pct > 0) "+$pct%" else if (pct < 0) "−${-pct}%" else "0%"
    }

    /** "212 ms" under a second, else "8,4 dtk" with [secondsUnit]. */
    fun duration(millis: Int, secondsUnit: String): String =
        if (millis < 1_000) "$millis ms" else "${decimal(millis / 1_000.0, 1)} $secondsUnit"

    /** "1,8 GB", "512 MB". */
    fun bytes(value: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return "${decimal(size, if (unit >= 3) 1 else 0)} ${units[unit]}"
    }

    /** "16 Okt" without the year (chart axes). */
    fun dayMonth(date: LocalDate): String = "${date.day} ${month(date)}"

    /** "Okt 2026" for a `YYYY-MM` month. */
    fun monthYear(yearMonth: String): String {
        val (year, monthNumber) = yearMonth.split('-').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        return "${months.getOrNull(monthNumber - 1) ?: monthNumber.toString().padStart(2, '0')} $year"
    }

    /** "20.25" (WIB) for the time of a row of today. */
    fun time(instant: Instant): String {
        val t = instant.toLocalDateTime(Wib)
        val sep = if (groupSeparator == ".") "." else ":"
        return "${t.hour.toString().padStart(2, '0')}$sep${t.minute.toString().padStart(2, '0')}"
    }

    /** "Nov" for a month of the year, 1–12. */
    fun monthName(month: Int): String = months.getOrNull(month - 1) ?: month.toString()

    /** +3 / −10 with a real minus sign. */
    fun signed(value: Int): String = if (value > 0) "+${number(value)}" else number(value)

    private fun month(date: LocalDate): String =
        months.getOrNull(date.month.ordinal) ?: date.month.ordinal.plus(1).toString().padStart(2, '0')

    /** "14 Sep 2026". */
    fun date(date: LocalDate): String = "${date.day} ${month(date)} ${date.year}"

    fun date(instant: Instant): String = date(instant.toLocalDateTime(Wib).date)

    /** "16 Okt, 20.25" (Indonesian uses a dot between hours and minutes). */
    fun dateTime(instant: Instant): String {
        val t: LocalDateTime = instant.toLocalDateTime(Wib)
        val sep = if (groupSeparator == ".") "." else ":"
        return "${t.day} ${month(t.date)}, ${t.hour.toString().padStart(2, '0')}$sep${t.minute.toString().padStart(2, '0')}"
    }

    /** "20–23 Nov 2026", "28 Sep – 2 Okt 2026", across years in full. */
    fun dateRange(start: LocalDate, end: LocalDate): String = when {
        start.year != end.year -> "${date(start)} – ${date(end)}"
        start.month != end.month -> "${start.day} ${month(start)} – ${date(end)}"
        else -> "${start.day}–${date(end)}"
    }

    /** "Baru saja", "12 mnt lalu", "3 jam lalu", "Kemarin", "4 hari lalu", then the date. */
    fun relative(instant: Instant, now: Instant = Clock.System.now()): String {
        val minutes = (now - instant).inWholeMinutes
        return when {
            minutes < 1 -> justNow
            minutes < 60 -> minutesAgo.replace("%1\$d", minutes.toString())
            minutes < 24 * 60 -> hoursAgo.replace("%1\$d", (minutes / 60).toString())
            minutes < 48 * 60 -> yesterday
            minutes < 7 * 24 * 60 -> daysAgo.replace("%1\$d", (minutes / (24 * 60)).toString())
            else -> date(instant)
        }
    }
}

val LocalAdminFormat = staticCompositionLocalOf {
    AdminFormat(emptyList(), ".", "", "%1\$d min", "%1\$d h", "1 d", "%1\$d d")
}

@Composable
fun rememberAdminFormat(): AdminFormat {
    val months = stringResource(Res.string.months_short)
    val separator = stringResource(Res.string.number_group_separator)
    val justNow = stringResource(Res.string.time_just_now)
    val minutes = stringResource(Res.string.time_minutes_ago)
    val hours = stringResource(Res.string.time_hours_ago)
    val yesterday = stringResource(Res.string.time_yesterday)
    val days = stringResource(Res.string.time_days_ago)
    return AdminFormat(
        months = months.split(' ').filter { it.isNotBlank() }.takeIf { it.size == 12 } ?: emptyList(),
        groupSeparator = separator,
        justNow = justNow,
        minutesAgo = minutes,
        hoursAgo = hours,
        yesterday = yesterday,
        daysAgo = days,
    )
}
