package app.ransel.ui.tour

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.navigation.SheetBackHandler
import org.koin.compose.koinInject
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.tour_done
import ransel.composeapp.generated.resources.tour_next
import ransel.composeapp.generated.resources.tour_ok
import ransel.composeapp.generated.resources.tour_pane
import ransel.composeapp.generated.resources.tour_skip
import ransel.composeapp.generated.resources.tour_step
import ransel.composeapp.generated.resources.tour_step_spoken
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Marks an element a tour can point at, while it is on screen. [cornerRadius] = the element's own corner (the
 * spotlight adds 4 dp); [onClick] = what a tap on the spotlight does (the element's own action), null = only closes.
 */
fun Modifier.tourTarget(target: TourTarget, cornerRadius: androidx.compose.ui.unit.Dp = 16.dp, onClick: (() -> Unit)? = null): Modifier = composed {
    val controller = koinInject<TourController>()
    androidx.compose.runtime.DisposableEffect(target) { onDispose { controller.targets.remove(target) } }
    val latest = androidx.compose.runtime.rememberUpdatedState(onClick)
    onGloballyPositioned { c ->
        if (!c.isAttached) return@onGloballyPositioned
        val p = c.positionInRoot()
        controller.targets[target] = TargetInfo(
            Rect(p, Size(c.size.width.toFloat(), c.size.height.toFloat())),
            cornerRadius.value,
            latest.value?.let { { latest.value?.invoke() } },
        )
    }
}

/** The scrim, the spotlight and the tip card. Put it last in the app's root Box so it covers every screen. */
@Composable
fun TourOverlay(controller: TourController = koinInject()) {
    LaunchedEffect(controller.run) { controller.persistFlags() }
    val run = controller.run
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val target = run?.let { controller.targets[it.step.target] }
    // the target left the screen (navigated away): the tour ends and is recorded
    LaunchedEffect(run, target == null) {
        if (run != null && target == null) {
            kotlinx.coroutines.delay(300)
            if (controller.run == run && controller.targets[run.step.target] == null) controller.finish()
        }
    }
    AnimatedVisibility(
        visible = run != null && target != null && !keyboardOpen && !controller.blocked,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
    ) {
        if (run != null && target != null) TourLayer(controller, run, target)
    }
}

@Composable
private fun TourLayer(controller: TourController, run: TourRun, target: TargetInfo) {
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var layer by remember { mutableStateOf(IntSize.Zero) }
    var card by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(density) { 6.dp.toPx() }
    val spot = target.bounds.translate(-origin.x, -origin.y).inflate(gap)
    val radius = min(with(density) { (target.radius.dp + 4.dp).toPx() }, min(spot.width, spot.height) / 2)
    val ring = if (dark) MaterialTheme.colorScheme.onSurface else Color.White
    val scrim = Color.Black.copy(alpha = if (dark) 0.72f else 0.62f)
    val focus = remember { FocusRequester() }
    SheetBackHandler(onBack = controller::finish)
    LaunchedEffect(run.index) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot(); layer = it.size }
            .onPreviewKeyEvent { if (it.key == Key.Escape) { controller.finish(); true } else false }
            // a tap on the scrim does nothing; a tap inside the spotlight runs the element and closes the tour
            .pointerInput(spot) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    if (up != null && spot.contains(up.position)) controller.tapSpotlight()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(scrim)
            drawRoundRect(Color.Transparent, spot.topLeft, spot.size, CornerRadius(radius), blendMode = BlendMode.Clear)
            drawRoundRect(ring, spot.topLeft, spot.size, CornerRadius(radius), style = Stroke(width = 2.dp.toPx()))
        }
        val margin = with(density) { 16.dp.toPx() }
        val spacing = with(density) { 12.dp.toPx() }
        val maxCardWidth = with(density) { 328.dp.toPx() }
        val cardWidth = min(maxCardWidth, layer.width - 2 * margin)
        // a target on the rail (left edge, wide window): the card on its right; else below when it fits, else above
        val onRail = spot.left < with(density) { 120.dp.toPx() } && layer.width >= with(density) { 1240.dp.toPx() } && spot.width < with(density) { 120.dp.toPx() }
        val placement = when {
            onRail -> Placement.RIGHT
            spot.bottom + spacing + card.height + margin <= layer.height -> Placement.BELOW
            else -> Placement.ABOVE
        }
        val x = when (placement) {
            Placement.RIGHT -> spot.right + spacing
            else -> (spot.center.x - cardWidth / 2).coerceIn(margin, max(margin, layer.width - margin - cardWidth))
        }
        val y = when (placement) {
            Placement.BELOW -> spot.bottom + spacing
            Placement.ABOVE -> spot.top - spacing - card.height
            Placement.RIGHT -> spot.center.y - with(density) { 44.dp.toPx() }
            // a very tall target (the swipe deck): the card stays on screen, over the edge of the spotlight
        }.coerceIn(margin, max(margin, layer.height - margin - card.height))
        val maxCardHeight = (layer.height * 0.6f).roundToInt()
        TourCard(
            run = run,
            placement = placement,
            arrowAt = when (placement) {
                Placement.RIGHT -> spot.center.y - y
                else -> spot.center.x - x
            },
            onNext = controller::next,
            onSkip = controller::finish,
            focus = focus,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(with(density) { cardWidth.toDp() })
                .heightIn(max = with(density) { maxCardHeight.toDp() })
                .onSizeChanged { card = it }
                // invisible until measured, so it never flashes in the wrong place
                .graphicsLayer { alpha = if (card == IntSize.Zero) 0f else 1f },
        )
    }
}

private enum class Placement { BELOW, ABOVE, RIGHT }

@Composable
private fun TourCard(
    run: TourRun,
    placement: Placement,
    arrowAt: Float,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val container = if (dark) scheme.surfaceContainerHigh else scheme.surface
    val stacked = density.fontScale >= 1.3f
    val step = run.step
    val number = run.index + 1
    val total = run.steps.size
    val pane = stringResource(Res.string.tour_pane)
    val spoken = if (run.isSingle) pane else stringResource(Res.string.tour_step_spoken, number, total)
    Box(modifier) {
        // the arrow: a 14 dp square turned 45°, pointing at the element's centre, 24 dp from the card's corners
        val arrow = 14.dp
        val arrowPx = with(density) { arrow.toPx() }
        val edge = with(density) { 24.dp.toPx() }
        Box(
            Modifier
                .offset {
                    when (placement) {
                        Placement.BELOW -> IntOffset((arrowAt - arrowPx / 2).coerceAtLeast(edge).roundToInt(), (-arrowPx / 2).roundToInt())
                        Placement.ABOVE -> IntOffset((arrowAt - arrowPx / 2).coerceAtLeast(edge).roundToInt(), 0)
                        Placement.RIGHT -> IntOffset((-arrowPx / 2).roundToInt(), (arrowAt - arrowPx / 2).coerceAtLeast(edge).roundToInt())
                    }
                }
                .then(if (placement == Placement.ABOVE) Modifier.align(Alignment.BottomStart).offset(y = arrow / 2) else Modifier)
                .size(arrow)
                .rotate(45f)
                .background(container, RoundedCornerShape(2.dp)),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = container,
            shadowElevation = 6.dp,
            border = if (dark) androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant) else null,
            modifier = Modifier
                .semantics { paneTitle = spoken }
                .focusRequester(focus)
                .focusable(),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!run.isSingle) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.tour_step, number, total),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                            modifier = Modifier.semantics { contentDescription = spoken },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics { contentDescription = "" }) {
                            repeat(total) { i ->
                                Box(
                                    Modifier
                                        .size(width = if (i == run.index) 16.dp else 6.dp, height = 6.dp)
                                        .background(
                                            when {
                                                i == run.index -> scheme.primary
                                                i < run.index -> scheme.primaryContainer
                                                else -> scheme.outlineVariant
                                            },
                                            CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(step.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (step.bodyArgs.isEmpty()) stringResource(step.body) else stringResource(step.body, *step.bodyArgs.toTypedArray()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                val primary = stringResource(
                    when {
                        run.isSingle -> Res.string.tour_ok
                        run.isLast -> Res.string.tour_done
                        else -> Res.string.tour_next
                    },
                )
                val showSkip = !run.isSingle && !run.isLast
                if (stacked) {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(primary, fontWeight = FontWeight.Bold) }
                        if (showSkip) TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(stringResource(Res.string.tour_skip), fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (showSkip) TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(Res.string.tour_skip), fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant) }
                        else Box(Modifier)
                        Button(onClick = onNext, modifier = Modifier.heightIn(min = 44.dp)) { Text(primary, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
