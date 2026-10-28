package app.ransel.feature.tripform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.shared.dto.PollOptionInput
import app.ransel.ui.components.dashedBorder
import app.ransel.ui.format.DateFormatter
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.date_poll_closes_days
import ransel.composeapp.generated.resources.trip_form_poll_add
import ransel.composeapp.generated.resources.trip_form_poll_edit
import ransel.composeapp.generated.resources.trip_form_poll_empty
import ransel.composeapp.generated.resources.trip_form_poll_error
import ransel.composeapp.generated.resources.trip_form_poll_options
import ransel.composeapp.generated.resources.trip_form_undated
import ransel.composeapp.generated.resources.trip_form_undated_hint
import ransel.composeapp.generated.resources.trip_form_undated_info

/** "Tanggal belum pasti" / "Tanya teman lewat polling tanggal" with its switch (design BuatTripTanggalBelumPasti). */
@Composable
internal fun UndatedSwitchRow(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.trip_form_undated), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(Res.string.trip_form_undated_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * With the switch on, the date picker becomes the DATE poll draft: "3 pilihan tanggal · tutup 3 hari lagi" + "Ubah",
 * one line per range, and the note that ideas go to "Belum dijadwalkan" until the dates are set.
 */
@Composable
internal fun DatePollDraftCard(
    options: List<PollOptionInput>,
    closingDays: Int,
    showError: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(IrisShape.lg)
                .background(scheme.surfaceContainerLow)
                .dashedBorder(scheme.outline.copy(alpha = 0.6f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (options.isEmpty()) stringResource(Res.string.trip_form_poll_empty)
                    else pluralStringResource(Res.plurals.trip_form_poll_options, options.size, options.size) + " · " +
                        pluralStringResource(Res.plurals.date_poll_closes_days, closingDays, closingDays),
                    style = if (options.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge,
                    color = if (options.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (options.isEmpty()) Res.string.trip_form_poll_add else Res.string.trip_form_poll_edit))
                }
            }
            options.forEach { option ->
                val start = option.startDate
                val end = option.endDate
                if (start != null && end != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Symbol(Symbols.event, size = 18.dp, tint = scheme.onSurfaceVariant)
                        Text(optionText(dates, start, end), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (showError) {
            Text(stringResource(Res.string.trip_form_poll_error), style = MaterialTheme.typography.bodySmall, color = scheme.error)
        }
        Row(
            Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(Symbols.info, size = 18.dp, filled = true, tint = scheme.onSurfaceVariant)
            Text(stringResource(Res.string.trip_form_undated_info), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

/** "Jum–Sen, 20–23 Nov 2026"; one day: "Jum, 20 Nov". */
private fun optionText(dates: DateFormatter, start: LocalDate, end: LocalDate): String =
    if (start == end) dates.weekdayDayMonth(start) else "${dates.weekdayShort(start)}–${dates.weekdayShort(end)}, ${dates.range(start, end)}"

@Preview
@Composable
private fun DatePollDraftPreview() = IrisPreview {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UndatedSwitchRow(checked = true, onChange = {})
        DatePollDraftCard(
            options = listOf(
                PollOptionInput("1", "", startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23)),
                PollOptionInput("2", "", startDate = LocalDate(2026, 11, 27), endDate = LocalDate(2026, 11, 30)),
                PollOptionInput("3", "", startDate = LocalDate(2026, 12, 4), endDate = LocalDate(2026, 12, 7)),
            ),
            closingDays = 3,
            showError = false,
            onEdit = {},
        )
    }
}

@Preview
@Composable
private fun DatePollDraftEmptyPreview() = IrisPreview {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UndatedSwitchRow(checked = true, onChange = {})
        DatePollDraftCard(options = emptyList(), closingDays = 3, showError = true, onEdit = {})
    }
}
