package app.ransel.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.content.LiveContent
import app.ransel.shared.dto.HolidayDto
import app.ransel.shared.model.HolidayType
import app.ransel.ui.format.DateFormatter
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.util.today
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * IRIS calendar that shows tanggal merah and cuti bersama (the Material3 pickers cannot style single days).
 * Months scroll vertically, Monday first. A Sunday and a [HolidayType.NATIONAL] day have a red number and a filled
 * dot, a [HolidayType.CUTI_BERSAMA] day a red number and a ring, so the two kinds differ by shape, not only by colour.
 * The range between [selection]'s start and end is filled; with no end only the start is marked.
 * Days outside [earliest]..[latest] are dimmed and cannot be tapped; the months shown are limited the same way
 * (without a limit: five years either side of [initialMonth]).
 */
@Composable
fun RanselCalendar(
    selection: DateSelection,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
    initialMonth: LocalDate = selection.start ?: earliest ?: today(),
    holidays: Map<LocalDate, HolidayDto> = LiveContent.current.holidays,
) {
    val dates = rememberDateFormatter()
    val today = remember { today() }
    val firstMonth = remember(earliest, initialMonth) { (earliest ?: LocalDate(initialMonth.year - 5, 1, 1)).firstOfMonth() }
    val lastMonth = remember(latest, initialMonth) { (latest ?: LocalDate(initialMonth.year + 5, 12, 1)).firstOfMonth() }
    val months = remember(firstMonth, lastMonth) {
        List((monthsBetween(firstMonth, lastMonth) + 1).coerceAtLeast(1)) { firstMonth.plusMonths(it) }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = monthsBetween(firstMonth, initialMonth.firstOfMonth()).coerceIn(0, months.lastIndex),
    )
    Column(modifier) {
        WeekdayHeader(dates)
        LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
            items(months, key = { it.toString() }) { month ->
                MonthBlock(month, selection, today, earliest, latest, holidays, dates, onDayClick)
            }
        }
    }
}

@Composable
private fun WeekdayHeader(dates: DateFormatter) {
    val monday = LocalDate(2026, 11, 2) // any Monday: the formatter only needs the weekday
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        repeat(7) { i ->
            val day = LocalDate.fromEpochDays(monday.toEpochDays() + i)
            Text(
                dates.weekdayShort(day),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = if (day.dayOfWeek == DayOfWeek.SUNDAY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthBlock(
    month: LocalDate,
    selection: DateSelection,
    today: LocalDate,
    earliest: LocalDate?,
    latest: LocalDate?,
    holidays: Map<LocalDate, HolidayDto>,
    dates: DateFormatter,
    onDayClick: (LocalDate) -> Unit,
) {
    val cells = remember(month) { monthCells(month) }
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            dates.monthYear(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
        )
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(date, selection, date == today, isSelectable(date, earliest, latest), holidays[date], dates, onDayClick)
                        } else {
                            Box(Modifier.height(DayHeight))
                        }
                    }
                }
            }
        }
    }
}

private val DayHeight = 48.dp

@Composable
private fun DayCell(
    date: LocalDate,
    selection: DateSelection,
    isToday: Boolean,
    enabled: Boolean,
    holiday: HolidayDto?,
    dates: DateFormatter,
    onClick: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val start = selection.start
    val end = selection.end
    val isStart = date == start
    val isEnd = date == (end ?: start)
    val inside = selection.contains(date)
    val edge = isStart || isEnd
    val redDay = holiday != null || date.dayOfWeek == DayOfWeek.SUNDAY
    val numberColor = when {
        edge -> scheme.onPrimary
        redDay -> scheme.error
        else -> scheme.onSurface
    }
    val band = scheme.primaryContainer
    // the range band: full width inside, half width on the first and last day of a longer range
    val bandStartsHere = isStart && end != null && end != start
    val bandEndsHere = isEnd && start != null && end != null && end != start
    val description = buildString {
        append(dates.fullWeekdayDayMonth(date)).append(' ').append(date.year)
        if (holiday != null) append(", ").append(holiday.name)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(DayHeight)
            .drawBehind {
                if (inside && !(isStart && isEnd)) {
                    val w = size.width
                    val left = if (bandStartsHere) w / 2 else 0f
                    val right = if (bandEndsHere) w / 2 else w
                    drawRect(band, Offset(left, 4.dp.toPx()), Size(right - left, size.height - 8.dp.toPx()))
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = inside
            }
            .then(if (enabled) Modifier.clickable(role = Role.Button) { onClick(date) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .then(if (edge) Modifier.background(scheme.primary, CircleShape) else Modifier)
                .then(if (isToday && !edge) Modifier.border(1.dp, scheme.primary, CircleShape) else Modifier)
                .alpha(if (enabled) 1f else 0.38f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.day.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (edge || isToday) FontWeight.Bold else FontWeight.Normal,
                color = numberColor,
            )
            if (holiday != null) {
                HolidayMark(holiday.type, if (edge) scheme.onPrimary else scheme.error, Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp))
            }
        }
    }
}

/** A filled dot = tanggal merah, a ring = cuti bersama. */
@Composable
internal fun HolidayMark(type: HolidayType, color: Color, modifier: Modifier = Modifier) {
    val mark = modifier.size(5.dp)
    when (type) {
        HolidayType.NATIONAL -> Box(mark.background(color, CircleShape))
        HolidayType.CUTI_BERSAMA -> Box(mark.border(1.dp, color, CircleShape))
    }
}
