package app.ransel.ui.format

import androidx.compose.runtime.Composable
import app.ransel.content.stringResource
import app.ransel.shared.domain.activityDurationMinutes
import kotlinx.datetime.LocalTime
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.duration_approx
import ransel.composeapp.generated.resources.duration_hours
import ransel.composeapp.generated.resources.duration_hours_decimal
import ransel.composeapp.generated.resources.duration_hours_minutes
import ransel.composeapp.generated.resources.duration_minutes
import ransel.composeapp.generated.resources.time_separator

/** "09.00" (Indonesian) / "09:00" (English): separator comes from `time_separator`. */
@Composable
fun formatTime(time: LocalTime): String {
    val separator = stringResource(Res.string.time_separator)
    return time.hour.toString().padStart(2, '0') + separator + time.minute.toString().padStart(2, '0')
}

/** "2 jam 30 mnt", "2 jam", "45 mnt". */
@Composable
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> stringResource(Res.string.duration_hours_minutes, h, m)
        h > 0 -> stringResource(Res.string.duration_hours, h)
        else -> stringResource(Res.string.duration_minutes, m)
    }
}

/** Compact form used while dragging: "1,5 jam", "2 jam", "45 mnt". */
@Composable
fun formatDurationShort(minutes: Int): String {
    if (minutes < 60) return stringResource(Res.string.duration_minutes, minutes)
    val tenths = (minutes * 10 / 60.0).toInt() // truncate to one decimal
    val value = if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10},${tenths % 10}"
    return stringResource(Res.string.duration_hours_decimal, value)
}

/** "± 1 jam 30 mnt" for AI/estimated durations. */
@Composable
fun formatDurationApprox(minutes: Int): String = stringResource(Res.string.duration_approx, formatDuration(minutes))

fun durationMinutes(start: LocalTime?, end: LocalTime?): Int? =
    activityDurationMinutes(start, end)?.takeIf { it > 0 }
