package app.ransel.ui.components.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.dto.HolidayDto
import app.ransel.shared.model.HolidayType
import app.ransel.ui.format.rememberDateFormatter
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.calendar_legend_collective_leave
import ransel.composeapp.generated.resources.calendar_legend_holiday

/**
 * Under the calendar: the name of the holiday on [focused] (the day the user tapped last) and what the two marks
 * mean. Always shown, so the marks are explained before the user meets one.
 */
@Composable
fun CalendarLegend(focused: HolidayDto?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (focused != null) {
            Text(
                "${dates.dayMonthYear(focused.date)} · ${focused.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(HolidayType.NATIONAL, stringResource(Res.string.calendar_legend_holiday))
            LegendItem(HolidayType.CUTI_BERSAMA, stringResource(Res.string.calendar_legend_collective_leave))
        }
    }
}

@Composable
private fun LegendItem(type: HolidayType, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        HolidayMark(type, MaterialTheme.colorScheme.error)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
