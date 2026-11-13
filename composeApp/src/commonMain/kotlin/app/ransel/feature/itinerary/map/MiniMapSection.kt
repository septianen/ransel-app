package app.ransel.feature.itinerary.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.components.SkeletonCards
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.mini_map_add_location
import ransel.composeapp.generated.resources.mini_map_badge
import ransel.composeapp.generated.resources.mini_map_collapse
import ransel.composeapp.generated.resources.mini_map_collapsed_title
import ransel.composeapp.generated.resources.mini_map_description
import ransel.composeapp.generated.resources.mini_map_empty_title
import ransel.composeapp.generated.resources.mini_map_expand
import ransel.composeapp.generated.resources.mini_map_failed_body
import ransel.composeapp.generated.resources.mini_map_failed_title
import ransel.composeapp.generated.resources.mini_map_open_in_maps
import ransel.composeapp.generated.resources.mini_map_partial
import ransel.composeapp.generated.resources.mini_map_retry

/** Where the strip is in its life: the base map is coming, it is there, or it did not come in time. */
enum class MapPhase { LOADING, LOADED, FAILED }

private val StripHeight = 160.dp

/**
 * The day's mini map above the timeline (spec "Mini peta di Itinerary"): a 160 dp strip with numbered pins, a chip that
 * opens the highlighted place in Google Maps, a fold button, and the "n aktivitas belum punya lokasi" line. A day
 * without any activity shows nothing; a day where none has a place shows the dashed "Isi lokasi" card.
 *
 * [onFillLocation] opens the first activity without a place with its place picker; null (viewers) hides the buttons.
 */
@Composable
fun MiniMapSection(
    pins: List<MapPin>,
    unlocated: Int,
    selectedId: String?,
    collapsed: Boolean,
    dark: Boolean,
    onToggleCollapsed: () -> Unit,
    onPinClick: (itemId: String) -> Unit,
    onFillLocation: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (pins.isEmpty() && unlocated == 0) return
    when {
        pins.isEmpty() -> MiniMapEmptyCard(onFillLocation, modifier)
        collapsed -> MiniMapCollapsedBar(pins.size, onToggleCollapsed, modifier)
        else -> {
            // a retry makes a new map; the clock of 8 s starts with each try
            var attempt by remember { mutableIntStateOf(0) }
            var phase by remember(attempt) { mutableStateOf(MapPhase.LOADING) }
            LaunchedEffect(attempt) {
                delay(MINI_MAP_LOAD_TIMEOUT_MILLIS)
                if (phase == MapPhase.LOADING) phase = MapPhase.FAILED
            }
            val uriHandler = LocalUriHandler.current
            val toOpen = pinToOpen(pins, selectedId)
            Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniMapStrip(
                    phase = phase,
                    canvas = { canvasModifier ->
                        MiniMapCanvas(
                            pins = pins,
                            selectedId = selectedId,
                            dark = dark,
                            ringColor = MaterialTheme.colorScheme.primary.toArgb(),
                            description = stringResource(Res.string.mini_map_description, pins.size),
                            onPinClick = onPinClick,
                            onLoaded = { phase = MapPhase.LOADED },
                            modifier = canvasModifier,
                        )
                    },
                    onOpenInMaps = toOpen?.let { pin -> { uriHandler.openUri(googleMapsUrl(pin)) } },
                    onCollapse = onToggleCollapsed,
                    onRetry = { attempt++ },
                )
                if (unlocated > 0) MiniMapPartialLine(unlocated, onFillLocation)
            }
        }
    }
}

/** The strip: the map (a slot, so previews can show a stand-in) with its two controls, the skeleton, or the failed card. */
@Composable
fun MiniMapStrip(
    phase: MapPhase,
    canvas: @Composable (Modifier) -> Unit,
    onOpenInMaps: (() -> Unit)?,
    onCollapse: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(StripHeight).clip(IrisShape.lg).background(scheme.surfaceContainerHigh)) {
        if (phase == MapPhase.FAILED) {
            MiniMapFailedCard(onRetry, Modifier.fillMaxSize())
            return@Box
        }
        canvas(Modifier.fillMaxSize())
        // the strip keeps its height while it loads, so the list never jumps
        if (phase == MapPhase.LOADING) SkeletonCards(cards = 1, cardHeight = StripHeight, padding = 0.dp)
        // controls sit apart from the Google logo (bottom left), which must stay visible
        if (onOpenInMaps != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(role = Role.Button, onClick = onOpenInMaps),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(scheme.surface)
                        .border(1.dp, scheme.outline, CircleShape)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Symbol(Symbols.openInNew, size = 14.dp, tint = scheme.onSurface)
                    Text(
                        stringResource(Res.string.mini_map_open_in_maps),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(scheme.surface)
                    .border(1.dp, scheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    Symbols.expandMore,
                    size = 20.dp,
                    tint = scheme.onSurface,
                    modifier = Modifier.rotate(180f),
                    contentDescription = stringResource(Res.string.mini_map_collapse),
                )
            }
        }
    }
}

@Composable
fun MiniMapCollapsedBar(count: Int, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val expandLabel = stringResource(Res.string.mini_map_expand)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(IrisShape.lg)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .clickable(role = Role.Button, onClickLabel = expandLabel, onClick = onExpand)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Symbol(Symbols.map, size = 22.dp, tint = scheme.onSurface)
        Text(
            stringResource(Res.string.mini_map_collapsed_title, count),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Symbol(Symbols.expandMore, size = 22.dp, tint = scheme.onSurfaceVariant)
    }
}

/** "n aktivitas belum punya lokasi · Isi lokasi" under the strip. */
@Composable
fun MiniMapPartialLine(unlocated: Int, onFillLocation: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.mini_map_partial, unlocated),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        if (onFillLocation != null) {
            Text(
                stringResource(Res.string.mini_map_add_location),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = scheme.primary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onFillLocation)
                    .padding(vertical = 14.dp),
            )
        }
    }
}

/** No activity of the day has a place yet: a dashed card instead of an empty map. */
@Composable
fun MiniMapEmptyCard(onFillLocation: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dash = scheme.outline
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clip(IrisShape.lg)
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Symbol(Symbols.locationOn, size = 28.dp, tint = scheme.onSurfaceVariant)
        Text(
            stringResource(Res.string.mini_map_empty_title),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        if (onFillLocation != null) {
            OutlinedButton(onClick = onFillLocation, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.mini_map_add_location))
            }
        }
    }
}

@Composable
private fun MiniMapFailedCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.background(scheme.surfaceContainer).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Symbol(Symbols.map, size = 32.dp, tint = scheme.onSurfaceVariant)
        Text(stringResource(Res.string.mini_map_failed_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(Res.string.mini_map_failed_body),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Symbol(Symbols.refresh, size = 18.dp)
            Text(stringResource(Res.string.mini_map_retry), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * The number of a pin on the activity's tile in the list (same number and color as on the map, spec "Badge di daftar").
 * An activity without a place gets an empty dashed circle with "–" while the day has pins; [pin] null means that.
 */
@Composable
fun PinBadge(pin: MapPin?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    if (pin != null) {
        val description = stringResource(Res.string.mini_map_badge, pin.number)
        Box(
            modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(1.5.dp)
                .clip(CircleShape)
                .background(Color(pin.color))
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                pin.number.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                color = Color(pin.labelColor),
            )
        }
    } else {
        val dash = scheme.outline
        Box(
            modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(scheme.surface)
                .drawBehind {
                    drawCircle(dash, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("–", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = scheme.onSurfaceVariant)
        }
    }
}

// A stand-in for the map in previews (the real one needs the platform SDK).
@Composable
private fun PreviewMapStandIn(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

@Preview
@Composable
private fun MiniMapStripPreview() = IrisPreview {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MiniMapStrip(MapPhase.LOADED, { PreviewMapStandIn(it) }, {}, {}, {})
        MiniMapStrip(MapPhase.LOADING, { PreviewMapStandIn(it) }, {}, {}, {})
        MiniMapStrip(MapPhase.FAILED, { PreviewMapStandIn(it) }, {}, {}, {})
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PinBadge(MapPin("a", 1, 0.0, 0.0, app.ransel.shared.model.Category.FOOD, 0xFFFF5722.toInt(), 0xFFFFFFFF.toInt()))
            PinBadge(MapPin("b", 2, 0.0, 0.0, app.ransel.shared.model.Category.SHOPPING, 0xFFFFB800.toInt(), 0xFF1B1B1B.toInt()))
            PinBadge(null)
        }
        MiniMapPartialLine(2, {})
        MiniMapCollapsedBar(4, {})
        MiniMapEmptyCard({})
    }
}
