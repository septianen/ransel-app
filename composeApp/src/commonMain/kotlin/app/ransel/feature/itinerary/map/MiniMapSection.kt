package app.ransel.feature.itinerary.map

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.navigation.SheetBackHandler
import app.ransel.ui.pulledUp
import app.ransel.content.stringResource
import app.ransel.ui.motion.IrisMotion
import app.ransel.ui.motion.rememberReduceMotion
import ransel.composeapp.generated.resources.map_travel_caption
import ransel.composeapp.generated.resources.map_city_chip
import ransel.composeapp.generated.resources.map_cities_today
import ransel.composeapp.generated.resources.map_cities_group
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import app.ransel.ui.preview.IrisPreview
import app.ransel.shared.domain.DayCity
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.mini_map_add_location
import ransel.composeapp.generated.resources.mini_map_badge
import ransel.composeapp.generated.resources.mini_map_collapse
import ransel.composeapp.generated.resources.mini_map_collapsed_open_maps
import ransel.composeapp.generated.resources.mini_map_collapsed_title
import ransel.composeapp.generated.resources.mini_map_description
import ransel.composeapp.generated.resources.mini_map_empty_title
import ransel.composeapp.generated.resources.mini_map_expand
import ransel.composeapp.generated.resources.mini_map_failed_title
import ransel.composeapp.generated.resources.mini_map_fullscreen
import ransel.composeapp.generated.resources.mini_map_open_in_maps
import ransel.composeapp.generated.resources.mini_map_partial
import ransel.composeapp.generated.resources.mini_map_recenter
import ransel.composeapp.generated.resources.mini_map_retry
import ransel.composeapp.generated.resources.mini_map_two_finger_hint

/** Where the strip is in its life: the base map is coming, it is there, or it did not come in time. */
enum class MapPhase { LOADING, LOADED, FAILED }

private val StripHeight = 160.dp
/** Compass and collapse: 40 dp circles, 8 dp apart, 12 dp from the strip's edge (spec 19 Nov 2026). */
private val MapControlSize = 40.dp

/**
 * The day's mini map above the timeline (spec "Mini peta di Itinerary"): a 160 dp strip with numbered pins, a chip that
 * opens the day's route in Google Maps (same as the day heading chip), compass then collapse at the top right, and the
 * "n aktivitas belum punya lokasi" line. A day without any activity shows nothing; a day where none has a place shows
 * the dashed "Isi lokasi" card.
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
    /** The straight lines between pins (spec "Perjalanan antar tempat V1") and the colours they are drawn with. */
    segments: List<MapSegment> = emptyList(),
    lineStyle: MapLineStyle? = null,
    /** A day in two or more cities: one chip each, [selectedCity] on; pins and lines are the selected city's. */
    cities: List<DayCity> = emptyList(),
    selectedCity: String? = null,
    onSelectCity: (String) -> Unit = {},
    /** "Jakarta → Bandung · OTW 09.00–12.00 · 3 jam" rows under the strip; a tap goes to the Perjalanan activity. */
    transfers: List<TransferRow> = emptyList(),
    onOpenTransfer: (itemId: String) -> Unit = {},
    /** True after the two-finger hint has been stored on this device. */
    twoFingerHintShown: Boolean = true,
    onTwoFingerHintShown: () -> Unit = {},
    /** Same as the day's "Buka di Google Maps" chip: the day's route, or null when there are fewer than two stops. */
    onOpenInMaps: (() -> Unit)? = null,
) {
    if (pins.isEmpty() && unlocated == 0) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (cities.size >= 2 && selectedCity != null) MiniMapCityChips(cities, selectedCity, onSelectCity)
        MiniMapBody(
            pins, unlocated, selectedId, collapsed, dark, onToggleCollapsed, onPinClick, onFillLocation, segments, lineStyle,
            transfers, onOpenTransfer, twoFingerHintShown, onTwoFingerHintShown, onOpenInMaps,
        )
    }
}

@Composable
private fun MiniMapBody(
    pins: List<MapPin>,
    unlocated: Int,
    selectedId: String?,
    collapsed: Boolean,
    dark: Boolean,
    onToggleCollapsed: () -> Unit,
    onPinClick: (itemId: String) -> Unit,
    onFillLocation: (() -> Unit)?,
    segments: List<MapSegment>,
    lineStyle: MapLineStyle?,
    transfers: List<TransferRow>,
    onOpenTransfer: (itemId: String) -> Unit,
    twoFingerHintShown: Boolean,
    onTwoFingerHintShown: () -> Unit,
    onOpenInMaps: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when {
        pins.isEmpty() -> MiniMapEmptyCard(onFillLocation, modifier)
        else -> MiniMapOpenable(
            pins, unlocated, selectedId, collapsed, dark, onToggleCollapsed, onPinClick, onFillLocation,
            segments, lineStyle, transfers, onOpenTransfer, twoFingerHintShown, onTwoFingerHintShown, onOpenInMaps, modifier,
        )
    }
}

@Composable
private fun MiniMapOpenable(
    pins: List<MapPin>,
    unlocated: Int,
    selectedId: String?,
    collapsed: Boolean,
    dark: Boolean,
    onToggleCollapsed: () -> Unit,
    onPinClick: (itemId: String) -> Unit,
    onFillLocation: (() -> Unit)?,
    segments: List<MapSegment>,
    lineStyle: MapLineStyle?,
    transfers: List<TransferRow>,
    onOpenTransfer: (itemId: String) -> Unit,
    twoFingerHintShown: Boolean,
    onTwoFingerHintShown: () -> Unit,
    onOpenInMaps: (() -> Unit)?,
    modifier: Modifier,
) {
    var mounted by remember { mutableStateOf(!collapsed) }
    if (!collapsed) mounted = true
    var collapseSettled by remember { mutableStateOf(collapsed) }
    if (!collapsed) collapseSettled = false
    var attempt by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(MapPhase.LOADING) }
    var fullscreen by remember { mutableStateOf(false) }
    var recenterTick by remember { mutableIntStateOf(0) }
    var mapReveal by remember { mutableFloatStateOf(0f) }
    var showHint by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val collapsedBarHeight = 48.dp
    val height by animateDpAsState(
        if (collapsed) collapsedBarHeight else StripHeight,
        animationSpec = if (reduceMotion) snap() else IrisMotion.defaultSpatial(),
        finishedListener = { value ->
            collapseSettled = collapsed && value == collapsedBarHeight
        },
        label = "mini-map-height",
    )
    val mapDrawn = isMiniMapDrawn(collapsed, collapseSettled)
    val mapAlpha by animateFloatAsState(
        if (mapReveal >= 1f) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else IrisMotion.short4Decelerate(),
        label = "mini-map-fade",
    )
    val skeletonAlpha by animateFloatAsState(
        if (phase == MapPhase.LOADING && mapReveal < 1f) 1f else 0f,
        animationSpec = IrisMotion.short3(),
        label = "mini-map-skeleton",
    )
    LaunchedEffect(attempt) {
        if (phase == MapPhase.LOADED) return@LaunchedEffect
        delay(MINI_MAP_LOAD_TIMEOUT_MILLIS)
        if (phase == MapPhase.LOADING) phase = MapPhase.FAILED
    }
    LaunchedEffect(phase, twoFingerHintShown, collapsed) {
        if (phase != MapPhase.LOADED || twoFingerHintShown || collapsed) return@LaunchedEffect
        showHint = true
        delay(4_000)
        showHint = false
        onTwoFingerHintShown()
    }
    val ring = MaterialTheme.colorScheme.primary.toArgb()
    val description = stringResource(Res.string.mini_map_description, pins.size)
    val mapCanvas: @Composable (Modifier, Int, Int, Boolean, Int, Boolean) -> Unit = { canvasModifier, widthPx, heightPx, expanded, tick, drawn ->
        key(attempt) {
            MiniMapCanvas(
                pins = pins,
                selectedId = selectedId,
                dark = dark,
                ringColor = ring,
                description = description,
                onPinClick = onPinClick,
                onLoaded = { phase = MapPhase.LOADED },
                onLoadFailed = { phase = MapPhase.FAILED },
                modifier = canvasModifier,
                segments = segments,
                lineStyle = lineStyle,
                viewportWidthPx = widthPx,
                viewportHeightPx = heightPx,
                gestures = if (expanded) MiniMapGestureMode.FREE else MiniMapGestureMode.TWO_FINGER,
                recenterTick = tick,
                expanded = expanded,
                onRevealProgress = { mapReveal = it },
                drawn = drawn,
            )
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
            if (mounted) {
                MiniMapStrip(
                    phase = phase,
                    mapAlpha = mapAlpha,
                    skeletonAlpha = skeletonAlpha,
                    showHint = showHint && !collapsed,
                    canvas = { canvasModifier, w, h -> mapCanvas(canvasModifier, w, h, false, 0, mapDrawn) },
                    onOpenInMaps = onOpenInMaps,
                    onCollapse = onToggleCollapsed,
                    onFullscreen = { fullscreen = true },
                    onRetry = {
                        phase = MapPhase.LOADING
                        mapReveal = 0f
                        attempt++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(StripHeight)
                        .graphicsLayer { alpha = if (mapDrawn) 1f else 0f },
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = collapsed,
                enter = fadeIn(IrisMotion.short3()),
                exit = fadeOut(IrisMotion.short3()),
            ) {
                MiniMapCollapsedBar(pins.size, onToggleCollapsed, onOpenInMaps, Modifier.fillMaxWidth())
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !collapsed && segments.isNotEmpty() && phase != MapPhase.FAILED,
            enter = fadeIn(IrisMotion.short3()),
            exit = fadeOut(IrisMotion.short3()),
        ) {
            Text(
                stringResource(Res.string.map_travel_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.pulledUp(6.dp),
            )
        }
        if (!collapsed) {
            transfers.forEach { row -> IntercityRow(row, onClick = { onOpenTransfer(row.itemId) }) }
            if (unlocated > 0) MiniMapPartialLine(unlocated, onFillLocation)
        }
    }
    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SheetBackHandler { fullscreen = false }
            MiniMapFullscreen(
                phase = phase,
                canvas = { canvasModifier, w, h -> mapCanvas(canvasModifier, w, h, true, recenterTick, true) },
                onOpenInMaps = onOpenInMaps,
                onClose = { fullscreen = false },
                onRecenter = { recenterTick++ },
                onRetry = {
                    phase = MapPhase.LOADING
                    mapReveal = 0f
                    attempt++
                },
            )
        }
    }
}

/** The strip: the map (a slot, so previews can show a stand-in) with its controls, the skeleton, or the failed card. */
@Composable
fun MiniMapStrip(
    phase: MapPhase,
    canvas: @Composable (Modifier, widthPx: Int, heightPx: Int) -> Unit,
    onOpenInMaps: (() -> Unit)?,
    onCollapse: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    mapAlpha: Float = 1f,
    skeletonAlpha: Float = 0f,
    showHint: Boolean = false,
    onFullscreen: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val edge = IrisTheme.spacing.s12
    val gap = IrisTheme.spacing.s8
    Box(modifier.fillMaxWidth().height(StripHeight)) {
        Box(Modifier.fillMaxSize().clip(IrisShape.lg).background(scheme.surfaceContainerHigh)) {
            if (phase != MapPhase.FAILED) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                    val heightPx = with(LocalDensity.current) { maxHeight.roundToPx() }
                    if (widthPx > 0 && heightPx > 0) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = mapAlpha }) {
                            canvas(Modifier.fillMaxSize(), widthPx, heightPx)
                        }
                    }
                }
                if (skeletonAlpha > 0f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = skeletonAlpha }
                            .background(scheme.surfaceContainerHigh),
                    )
                }
            } else {
                MiniMapFailedCard(onRetry, Modifier.fillMaxSize())
            }
        }
        if (onOpenInMaps != null) {
            // 12 dp inset: the pill sat flush on the rounded clip and lost its right edge (looked like a cut-off control)
            OpenInMapsChip(onOpenInMaps, Modifier.align(Alignment.BottomEnd).padding(edge))
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(edge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (phase != MapPhase.FAILED) {
                MapIconButton(
                    symbol = Symbols.explore,
                    label = stringResource(Res.string.mini_map_fullscreen),
                    onClick = onFullscreen,
                )
            }
            MapIconButton(
                symbol = Symbols.expandMore,
                label = stringResource(Res.string.mini_map_collapse),
                onClick = onCollapse,
                symbolModifier = Modifier.rotate(180f),
            )
        }
        if (showHint && phase != MapPhase.FAILED) {
            Text(
                stringResource(Res.string.mini_map_two_finger_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp, start = 12.dp, end = 12.dp)
                    .clip(IrisShape.md)
                    .background(scheme.surface.copy(alpha = 0.92f))
                    .border(1.dp, scheme.outlineVariant, IrisShape.md)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun MiniMapFullscreen(
    phase: MapPhase,
    canvas: @Composable (Modifier, widthPx: Int, heightPx: Int) -> Unit,
    onOpenInMaps: (() -> Unit)?,
    onClose: () -> Unit,
    onRecenter: () -> Unit,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val enter = fadeIn(IrisMotion.short4Decelerate()) + scaleIn(IrisMotion.defaultSpatial(), initialScale = 0.92f)
    val exit = fadeOut(IrisMotion.short3()) + scaleOut(IrisMotion.defaultSpatial(), targetScale = 0.92f)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    androidx.compose.animation.AnimatedVisibility(visible = shown, enter = enter, exit = exit) {
        Box(Modifier.fillMaxSize().background(scheme.surfaceContainerHigh)) {
            if (phase == MapPhase.FAILED) {
                MiniMapFailedCard(onRetry, Modifier.fillMaxSize().statusBarsPadding())
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                    val heightPx = with(LocalDensity.current) { maxHeight.roundToPx() }
                    if (widthPx > 0 && heightPx > 0) {
                        canvas(Modifier.fillMaxSize(), widthPx, heightPx)
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(IrisTheme.spacing.s12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IrisTheme.spacing.s8),
            ) {
                if (phase != MapPhase.FAILED) {
                    MapIconButton(
                        symbol = Symbols.locationOn,
                        label = stringResource(Res.string.mini_map_recenter),
                        onClick = onRecenter,
                    )
                }
                MapIconButton(
                    symbol = Symbols.close,
                    label = stringResource(Res.string.mini_map_collapse),
                    onClick = onClose,
                )
            }
            if (onOpenInMaps != null) {
                OpenInMapsChip(
                    onOpenInMaps,
                    Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(IrisTheme.spacing.s12),
                )
            }
        }
    }
}

@Composable
private fun OpenInMapsChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onClick),
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

@Composable
private fun MapIconButton(
    symbol: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbolModifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(MapControlSize)
            .shadow(
                elevation = 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            )
            .clip(CircleShape)
            .background(scheme.surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(symbol, size = 20.dp, tint = scheme.onSurface, modifier = symbolModifier, contentDescription = label)
    }
}

@Composable
fun MiniMapCollapsedBar(
    count: Int,
    onExpand: () -> Unit,
    onOpenInMaps: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val expandLabel = stringResource(Res.string.mini_map_expand)
    val openMapsLabel = stringResource(Res.string.mini_map_collapsed_open_maps)
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
        if (onOpenInMaps != null) {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onOpenInMaps),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Symbol(Symbols.map, size = 18.dp, tint = scheme.onSurface)
                Text(
                    openMapsLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
        }
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
        modifier.background(scheme.surfaceContainerHigh).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Symbol(Symbols.error, size = 32.dp, tint = scheme.onSurfaceVariant)
        Text(stringResource(Res.string.mini_map_failed_title), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.mini_map_retry))
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
        MiniMapStrip(MapPhase.LOADED, { modifier, _, _ -> PreviewMapStandIn(modifier) }, {}, {}, {})
        MiniMapStrip(MapPhase.LOADING, { modifier, _, _ -> PreviewMapStandIn(modifier) }, {}, {}, {}, skeletonAlpha = 1f, mapAlpha = 0f)
        MiniMapStrip(MapPhase.FAILED, { modifier, _, _ -> PreviewMapStandIn(modifier) }, {}, {}, {})
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

/** What an intercity row says; the text is built by the caller (it needs the strings and the time format). */
data class TransferRow(val itemId: String, val text: String)

/** "Jakarta → Bandung · OTW 09.00–12.00 · 3 jam": a 40 dp row under the strip, a tap scrolls to the Perjalanan activity. */
@Composable
fun IntercityRow(row: TransferRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(IrisShape.lg)
            .background(scheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(Symbols.directionsCar, size = 16.dp, tint = scheme.onSurfaceVariant)
        Text(
            row.text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
            maxLines = 2,
        )
    }
}

/**
 * The cities of a day with places in several ("Jakarta · 2"), one picked: the chip look of the day chips (selected = the
 * primary container with a check). A group of radios for a screen reader.
 */
@Composable
fun MiniMapCityChips(cities: List<DayCity>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val group = stringResource(Res.string.map_cities_group)
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = group },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cities.forEach { city ->
                val on = city.name == selected
                val label = stringResource(Res.string.map_city_chip, city.name, city.count)
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .selectable(selected = on, role = Role.RadioButton) { onSelect(city.name) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(if (on) scheme.primaryContainer else scheme.surface)
                            .border(if (on) 1.5.dp else 1.dp, if (on) scheme.primary else scheme.outlineVariant, CircleShape)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (on) Symbol(Symbols.check, size = 16.dp, tint = scheme.onPrimaryContainer)
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                            color = if (on) scheme.onPrimaryContainer else scheme.onSurface,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(Res.string.map_cities_today, cities.size),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurfaceVariant,
        )
    }
}
