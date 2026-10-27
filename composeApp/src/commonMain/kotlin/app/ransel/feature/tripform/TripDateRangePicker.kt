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
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.util.fromPickerMillis
import app.ransel.util.toPickerMillis
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.date_range_confirm
import ransel.composeapp.generated.resources.date_range_title
import ransel.composeapp.generated.resources.trip_close

/**
 * Full-screen M3 DateRangePicker for the trip dates (design "Pilih tanggal trip"). Days before [earliest] are greyed
 * out and cannot be picked (owner, 27 Oct 2026: a trip does not start in the past); null = any day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDateRangePicker(
    start: LocalDate,
    end: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    earliest: LocalDate? = null,
) {
    val selectable = remember(earliest) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = earliest == null || utcTimeMillis.fromPickerMillis() >= earliest
            override fun isSelectableYear(year: Int) = earliest == null || year >= earliest.year
        }
    }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start.toPickerMillis(),
        initialSelectedEndDateMillis = end.toPickerMillis(),
        initialDisplayedMonthMillis = start.toPickerMillis(),
        selectableDates = selectable,
    )
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
                        Symbols.close,
                        contentDescription = stringResource(Res.string.trip_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    stringResource(Res.string.date_range_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            DateRangePicker(
                state = state,
                modifier = Modifier.weight(1f),
                showModeToggle = false,
                title = null,
            )
            val s = state.selectedStartDateMillis
            val e = state.selectedEndDateMillis
            Button(
                onClick = {
                    if (s != null) onConfirm(s.fromPickerMillis(), (e ?: s).fromPickerMillis())
                },
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
