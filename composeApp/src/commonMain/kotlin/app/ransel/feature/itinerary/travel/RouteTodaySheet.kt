package app.ransel.feature.itinerary.travel

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.domain.MapsPoint
import app.ransel.shared.domain.MapsTravelMode
import app.ransel.shared.domain.dayRouteParts
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.ui.category.tileColor
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.format.formatTime
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.route_today_chip
import ransel.composeapp.generated.resources.route_today_note
import ransel.composeapp.generated.resources.route_today_open
import ransel.composeapp.generated.resources.route_today_part
import ransel.composeapp.generated.resources.route_today_part_open
import ransel.composeapp.generated.resources.route_today_part_times
import ransel.composeapp.generated.resources.route_today_repeat
import ransel.composeapp.generated.resources.route_today_split
import ransel.composeapp.generated.resources.route_today_subtitle
import ransel.composeapp.generated.resources.route_today_title

/**
 * "Buka rute hari ini" (spec "Perjalanan antar tempat V1", section 3): the stops of the day in timeline order with the same
 * numbers as the pins, and the button that opens them in Google Maps. More than nine stops are cut into parts (the last
 * stop of one part starts the next). [allStops] are the day's map stops ([app.ransel.shared.domain.dayStops]).
 */
@Composable
fun RouteTodaySheet(
    dayNumber: Int,
    allStops: List<ItineraryItemDto>,
    mode: MapsTravelMode,
    onDismiss: () -> Unit,
) {
    // two stops in a row at the same place are one stop for Maps, so they are one row here too
    val stops = remember(allStops) {
        allStops.fold(emptyList<ItineraryItemDto>()) { kept, s ->
            if (kept.lastOrNull()?.let { it.lat == s.lat && it.lng == s.lng } == true) kept else kept + s
        }
    }
    val points = remember(stops) { stops.map { MapsPoint(it.lat!!, it.lng!!) } }
    val parts = remember(points, mode) { dayRouteParts(points, mode) }
    val uriHandler = LocalUriHandler.current
    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(Res.string.route_today_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(Res.string.route_today_subtitle, dayNumber, stops.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (parts.size > 1) {
                Row(
                    Modifier.fillMaxWidth().clip(IrisShape.lg).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symbol(Symbols.info, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(Res.string.route_today_split, parts.size),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stops.forEachIndexed { index, item -> StopRow(index + 1, item) }
            }
            if (parts.size <= 1) {
                OpenButton(stringResource(Res.string.route_today_open)) { parts.firstOrNull()?.let { open(it.url) } }
                Text(
                    stringResource(Res.string.route_today_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                parts.forEachIndexed { index, part ->
                    val first = stops[part.from - 1]
                    val last = stops[part.to - 1]
                    val times = listOfNotNull(first.startTime, last.endTime ?: last.startTime)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(IrisShape.lg)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), IrisShape.lg)
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(Res.string.route_today_part, index + 1),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (times.size == 2) {
                                    stringResource(Res.string.route_today_part_times, part.from, part.to, formatTime(times[0]), formatTime(times[1]))
                                } else {
                                    "${part.from}–${part.to}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier
                                .heightIn(min = 40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(role = Role.Button) { open(part.url) }
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(Res.string.route_today_part_open),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                Text(
                    stringResource(Res.string.route_today_repeat, parts.first().to),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OpenButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Symbol(Symbols.map, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimary)
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Symbol(Symbols.openInNew, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** A numbered stop: the number on the category colour (same as its pin), the name and its time. */
@Composable
private fun StopRow(number: Int, item: ItineraryItemDto) {
    val accent = item.category.tileColor()
    val onAccent = if (accent.luminance() > 0.45f) Color.Black else Color.White
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = onAccent)
        }
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        item.startTime?.let {
            Text(formatTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The chip in the day's title row: "Buka rute hari ini" (touch area 48 dp, the chip itself 32). */
@Composable
fun RouteTodayChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Symbol(Symbols.route, size = 16.dp, tint = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(Res.string.route_today_chip),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
