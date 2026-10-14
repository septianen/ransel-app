package app.ransel.feature.itinerary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.shared.domain.ScheduledTime
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.ui.category.itineraryLabel
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.CategoryIcon
import app.ransel.ui.components.IrisTimePickerDialog
import app.ransel.ui.format.durationMinutes
import app.ransel.ui.format.formatDurationApprox
import app.ransel.ui.format.formatTime
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.itinerary_close
import ransel.composeapp.generated.resources.itinerary_day_chip
import ransel.composeapp.generated.resources.itinerary_day_empty_summary
import ransel.composeapp.generated.resources.itinerary_day_summary
import ransel.composeapp.generated.resources.itinerary_duration_label
import ransel.composeapp.generated.resources.itinerary_remove_from_list
import ransel.composeapp.generated.resources.itinerary_schedule_change
import ransel.composeapp.generated.resources.itinerary_schedule_note
import ransel.composeapp.generated.resources.itinerary_schedule_pick_day
import ransel.composeapp.generated.resources.itinerary_schedule_time
import ransel.composeapp.generated.resources.itinerary_schedule_to_day

/**
 * Schedule an unscheduled place (design "Jadwalkan tempat"): pick a day, the time is filled in at the
 * end of that day (09:00 on an empty day) with the place's duration, "Ubah" opens the time picker.
 */
@Composable
fun SchedulePlaceSheet(
    item: ItineraryItemDto,
    state: ItineraryUiState,
    preview: (LocalDate) -> ScheduledTime,
    onSchedule: (LocalDate, LocalTime, LocalTime) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dates = rememberDateFormatter()
    var day by rememberSaveable { mutableStateOf(state.selectedDay ?: state.days.first()) }
    var customStart by remember(day) { mutableStateOf<LocalTime?>(null) }
    var picking by remember { mutableStateOf(false) }
    val auto = preview(day)
    val duration = item.durationMin ?: durationMinutes(auto.start, auto.end) ?: 60
    val start = customStart ?: auto.start
    val end = if (customStart == null) auto.end else addMinutes(start, duration)
    val dayNumber = state.days.indexOf(day) + 1

    AdaptiveSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryIcon(item.category)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
                val label = stringResource(item.category.itineraryLabel)
                Text(
                    label + " · " + stringResource(
                        Res.string.itinerary_duration_label, formatDurationApprox(duration),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.size(48.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Symbol(
                    Symbols.close,
                    contentDescription = stringResource(Res.string.itinerary_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Text(
            stringResource(Res.string.itinerary_schedule_pick_day),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        // 2 × n grid of day cards
        state.days.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    val selected = d == day
                    val items = state.itemsByDay[d].orEmpty()
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(IrisShape.lg)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            )
                            .border(
                                BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                IrisShape.lg,
                            )
                            .clickable(role = Role.RadioButton) { day = d }
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(Res.string.itinerary_day_chip, state.days.indexOf(d) + 1),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (selected) {
                                Symbol(Symbols.checkCircle, size = 16.dp, filled = true, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(dates.weekdayDayMonth(d), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (items.isEmpty()) {
                                stringResource(Res.string.itinerary_day_empty_summary)
                            } else {
                                val last = items.mapNotNull { it.endTime ?: it.startTime }.maxOrNull()
                                stringResource(
                                    Res.string.itinerary_day_summary, items.size, last?.let { formatTime(it) } ?: "–",
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }

        Text(
            stringResource(Res.string.itinerary_schedule_time),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(IrisShape.md)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Symbol(Symbols.schedule, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatTime(start) + " – " + formatTime(end),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { picking = true }) {
                Text(stringResource(Res.string.itinerary_schedule_change), style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            stringResource(Res.string.itinerary_schedule_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Button(
            onClick = { onSchedule(day, start, end) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.itinerary_schedule_to_day, dayNumber), style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Symbol(Symbols.delete, tint = MaterialTheme.colorScheme.error)
            Text(
                stringResource(Res.string.itinerary_remove_from_list),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    if (picking) {
        IrisTimePickerDialog(
            initial = start,
            onConfirm = {
                customStart = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private fun addMinutes(time: LocalTime, minutes: Int): LocalTime {
    val total = (time.hour * 60 + time.minute + minutes).coerceAtMost(23 * 60 + 59)
    return LocalTime(total / 60, total % 60)
}
