package app.ransel.feature.tripform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.content.LiveContent
import app.ransel.content.stringResource
import app.ransel.ui.components.calendar.CalendarLegend
import app.ransel.ui.components.calendar.DateSelection
import app.ransel.ui.components.calendar.RanselCalendar
import app.ransel.ui.components.calendar.tapRange
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import kotlinx.datetime.LocalDate
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.date_range_confirm
import ransel.composeapp.generated.resources.date_range_title

/**
 * Full-screen calendar for the trip dates (design "Pilih tanggal trip") that marks tanggal merah and cuti bersama.
 * Days before [earliest] are greyed out and cannot be picked (owner, 27 Oct 2026: a trip does not start in the past);
 * null = any day.
 */
@Composable
fun TripDateRangePicker(
    /** Null = nothing selected yet (a new trip); the calendar then opens on [earliest] or today. */
    start: LocalDate?,
    end: LocalDate?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    earliest: LocalDate? = null,
) {
    var selection by remember { mutableStateOf(DateSelection(start, end?.takeIf { start != null })) }
    val holidays = LiveContent.current.holidays
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(
                        Symbols.arrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    stringResource(Res.string.date_range_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            RanselCalendar(
                selection = selection,
                onDayClick = { selection = tapRange(selection, it) },
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                earliest = earliest,
                holidays = holidays,
            )
            CalendarLegend(
                focused = (selection.end ?: selection.start)?.let { holidays[it] },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val s = selection.start
            val e = selection.end
            Button(
                onClick = { if (s != null) onConfirm(s, e ?: s) },
                enabled = s != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.date_range_confirm), style = MaterialTheme.typography.titleMedium) }
        }
    }
}
