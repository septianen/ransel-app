package app.ransel.feature.polls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.model.PollKind
import app.ransel.ui.components.AdaptiveSheet
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.trip_form_poll_empty
import ransel.composeapp.generated.resources.trip_form_poll_sheet_done
import ransel.composeapp.generated.resources.trip_form_poll_sheet_title

/** PRD "Date poll": a date poll asks about 2–5 ranges, none before today. */
const val DATE_POLL_MIN_OPTIONS = 2
const val DATE_POLL_MAX_OPTIONS = 5

/** True when [options] can start a date poll: 2–5 ranges, each complete and starting today or later. */
fun datePollOptionsValid(options: List<PollOptionInput>, today: LocalDate): Boolean =
    options.size in DATE_POLL_MIN_OPTIONS..DATE_POLL_MAX_OPTIONS && options.all { o ->
        val start = o.startDate
        val end = o.endDate
        start != null && end != null && start >= today && end >= start
    }

/**
 * The date poll of a trip that is still being made (Buat trip, "Tanggal belum pasti" → "Ubah"): when voting closes and
 * the date ranges, with the same parts as the poll form (BuatPolling). Nothing is sent; the form keeps the draft and
 * creates the poll once the trip exists.
 */
@Composable
fun DatePollSetupSheet(
    options: List<PollOptionInput>,
    closingDays: Int,
    currency: String,
    onClosingDays: (Int) -> Unit,
    onSaveOption: (PollOptionInput) -> Unit,
    onRemoveOption: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // null = no option sheet; "" = a new option; otherwise the id being edited
    var editing by remember { mutableStateOf<String?>(null) }
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(Res.string.trip_form_poll_sheet_title), style = MaterialTheme.typography.titleLarge)
            ClosingChoice(closingDays, onClosingDays)
            PollOptionsSection(
                kind = PollKind.DATE,
                options = options,
                currency = currency,
                onAdd = { editing = "" },
                onEdit = { editing = it },
                onRemove = onRemoveOption,
                canAdd = options.size < DATE_POLL_MAX_OPTIONS,
                emptyText = stringResource(Res.string.trip_form_poll_empty),
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.trip_form_poll_sheet_done), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    val current = editing
    if (current != null) {
        PollOptionSheet(
            kind = PollKind.DATE,
            currency = currency,
            initial = options.firstOrNull { it.id == current }?.asOptionDto(),
            tripStart = null,
            tripEnd = null,
            onSave = { onSaveOption(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}
