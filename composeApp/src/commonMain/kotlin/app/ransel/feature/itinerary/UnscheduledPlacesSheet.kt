package app.ransel.feature.itinerary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.itinerary_close
import ransel.composeapp.generated.resources.itinerary_more
import ransel.composeapp.generated.resources.itinerary_remove_from_list
import ransel.composeapp.generated.resources.itinerary_unscheduled
import ransel.composeapp.generated.resources.itinerary_unscheduled_places

/** All unscheduled places of the trip (design "Belum dijadwalkan: semua"). The AI filter arrives with Explore. */
@Composable
fun UnscheduledPlacesSheet(
    items: List<ItineraryItemDto>,
    onDismiss: () -> Unit,
    onSchedule: (ItineraryItemDto) -> Unit,
    onDelete: (ItineraryItemDto) -> Unit,
    canEdit: Boolean = true,
) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.itinerary_unscheduled), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(Res.string.itinerary_unscheduled_places, items.size, ""),
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
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item ->
                var menu by remember { mutableStateOf(false) }
                UnscheduledRow(
                    item = item,
                    onSchedule = if (canEdit) ({ onSchedule(item) }) else null,
                    trailing = if (!canEdit) null else {{
                        Box {
                            Box(
                                Modifier.size(40.dp).clickable { menu = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Symbol(
                                    Symbols.moreVert,
                                    contentDescription = stringResource(Res.string.itinerary_more),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.itinerary_remove_from_list)) },
                                    leadingIcon = { Symbol(Symbols.delete, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menu = false
                                        onDelete(item)
                                    },
                                )
                            }
                        }
                    }},
                )
            }
        }
    }
}
