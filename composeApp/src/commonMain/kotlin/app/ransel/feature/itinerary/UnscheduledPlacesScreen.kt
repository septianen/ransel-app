package app.ransel.feature.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.ui.components.AdaptiveFormScreen
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.itinerary_auto_button
import ransel.composeapp.generated.resources.itinerary_unscheduled
import ransel.composeapp.generated.resources.itinerary_unscheduled_places

/**
 * All unscheduled places of the trip as a screen of their own (owner, 27 Oct 2026; a dialog on wide web): tap a place
 * to open its form, the calendar button schedules it, and "Jadwalkan otomatis" sits at the bottom. Closes itself once
 * the last place has left the box.
 */
@Composable
fun UnscheduledPlacesScreen(
    items: List<ItineraryItemDto>,
    onDismiss: () -> Unit,
    onOpen: (ItineraryItemDto) -> Unit,
    onSchedule: (ItineraryItemDto) -> Unit,
    onAutoSchedule: (() -> Unit)?,
    canEdit: Boolean = true,
) {
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onDismiss() }
    AdaptiveFormScreen(
        title = stringResource(Res.string.itinerary_unscheduled),
        subtitle = stringResource(Res.string.itinerary_unscheduled_places, items.size, "").trimEnd(' ', '·'),
        onDismiss = onDismiss,
        footer = {
            if (onAutoSchedule != null) {
                Button(onClick = onAutoSchedule, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Symbol(Symbols.schedule, size = 20.dp)
                    Text(stringResource(Res.string.itinerary_auto_button), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { item ->
                UnscheduledRow(
                    item = item,
                    onOpen = if (canEdit) ({ onOpen(item) }) else null,
                    onSchedule = if (canEdit) ({ onSchedule(item) }) else null,
                )
            }
        }
    }
}
