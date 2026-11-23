package app.ransel.ui.components.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.LiveContent
import app.ransel.content.stringResource
import app.ransel.ui.components.AdaptiveSheet
import kotlinx.datetime.LocalDate
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.action_ok
import ransel.composeapp.generated.resources.calendar_date_title

/**
 * Pick one day, with tanggal merah and cuti bersama marked. [earliest] and [latest] are inclusive limits (null = none).
 * [onConfirm] is called with the chosen day when the user taps OK; [initial] is selected at the start.
 */
@Composable
fun RanselDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
) {
    var picked by remember { mutableStateOf(initial) }
    val holidays = LiveContent.current.holidays
    AdaptiveSheet(onDismiss = onDismiss) {
        Text(stringResource(Res.string.calendar_date_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        RanselCalendar(
            selection = DateSelection(picked),
            onDayClick = { picked = it },
            // a bounded height: the calendar scrolls inside it (a sheet gives its content no height limit)
            modifier = Modifier.fillMaxWidth().height(CalendarHeight),
            earliest = earliest,
            latest = latest,
            initialMonth = initial,
            holidays = holidays,
        )
        CalendarLegend(holidays[picked])
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            TextButton(onClick = { onConfirm(picked) }, enabled = isSelectable(picked, earliest, latest)) { Text(stringResource(Res.string.action_ok)) }
        }
    }
}

private val CalendarHeight = 380.dp
