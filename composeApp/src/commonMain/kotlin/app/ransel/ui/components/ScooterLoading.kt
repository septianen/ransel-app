package app.ransel.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.ransel.ui.motion.rememberReduceMotion
import app.ransel.ui.preview.IrisPreview
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.capybara_scooter
import ransel.composeapp.generated.resources.explore_loading_description
import ransel.composeapp.generated.resources.scooter_hub_mask
import ransel.composeapp.generated.resources.scooter_tire_mask
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Every number below is in source-image pixels of `capybara_scooter.webp` (523 x 558); the scene box covers
 * x -123..597, y 42..582. One factor scales them to the canvas, so the values stay the ones of the motion prototype.
 */
private const val SRC_W = 523
private const val SRC_H = 558
private const val SCENE_LEFT = -123f
private const val SCENE_TOP = 42f
private const val SCENE_W = 720f
private const val SCENE_H = 540f
private const val ART_H = 582f
private const val TILE_WIDTH = 760f
private const val MESSAGE_EVERY_MS = 2_500L

private val Ink = Color(0xFF1B1B1B)
private val TireStreak = Color(0xFF9AA09E)
private val HubDot = Color(0xFF7D8381)
private val PuffFill = Color(0xFFD6DBDE)
private val PlateOnDark = Color(0xFFE9EFE5)
private val FadeBounds = Rect(SCENE_LEFT, SCENE_TOP, SCENE_LEFT + SCENE_W, SCENE_TOP + SCENE_H)

/**
 * Capybara riding a scooter while Explore AI loads (design `MemuatRekomendasi`): scrolling ground, turning wheels,
 * speed lines, exhaust puffs, a rotating status line and three pulsing dots.
 *
 * [messages] rotate every 2.5 s. [hint] sits under the message. With the system "reduce motion" setting on, the
 * scene is a still frame and the first message stays.
 */
@Composable
fun ScooterLoading(
    messages: List<String>,
    modifier: Modifier = Modifier,
    hint: String? = null,
) = ScooterLoadingContent(messages, modifier, hint, reduceMotion = rememberReduceMotion())

@Composable
internal fun ScooterLoadingContent(
    messages: List<String>,
    modifier: Modifier,
    hint: String?,
    reduceMotion: Boolean,
) {
    val description = stringResource(Res.string.explore_loading_description)
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScooterScene(reduceMotion, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f / 0.82f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RotatingMessage(messages, reduceMotion)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        PulsingDots(reduceMotion)
    }
}

// ── status line and dots ────────────────────────────────────────────────────

@Composable
private fun RotatingMessage(messages: List<String>, reduceMotion: Boolean) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(messages.size, reduceMotion) {
        if (reduceMotion || messages.size < 2) return@LaunchedEffect
        while (true) {
            delay(MESSAGE_EVERY_MS)
            index = (index + 1) % messages.size
        }
    }
    AnimatedContent(
        targetState = index,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
        contentAlignment = Alignment.Center,
        label = "scooter-message",
    ) { shown ->
        Text(
            messages.getOrElse(shown) { "" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PulsingDots(reduceMotion: Boolean) {
    val clock = if (reduceMotion) null else rememberInfiniteTransition(label = "scooter-dots").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "dots",
    )
    val color = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        // Keyframes 0 / 30 / 60 / 100 %: faint, bright and 3 dp up, faint, faint. Dots start 0.2 s apart.
                        val phase = clock?.let { (it.value - index * 0.2f / 1.2f + 1f) % 1f } ?: 0f
                        val lift = when {
                            clock == null -> 0f
                            phase < 0.3f -> phase / 0.3f
                            phase < 0.6f -> 1f - (phase - 0.3f) / 0.3f
                            else -> 0f
                        }
                        alpha = 0.25f + 0.75f * lift
                        translationY = -3.dp.toPx() * lift
                    }
                    .background(color, CircleShape),
            )
        }
    }
}

// ── the scene ───────────────────────────────────────────────────────────────

/** Animated values. Read only inside draw lambdas, so a frame redraws the canvas but never recomposes. */
private class SceneClock(
    val ground: State<Float>,
    val spin: State<Float>,
    val bob: State<Float>,
    val lines: State<Float>,
    val puffs: State<Float>,
)

@Composable
private fun rememberSceneClock(): SceneClock {
    val transition = rememberInfiniteTransition(label = "scooter")
    @Composable
    fun cycle(ms: Int, to: Float = 1f, easing: androidx.compose.animation.core.Easing = LinearEasing, mode: RepeatMode = RepeatMode.Restart, label: String) =
        transition.animateFloat(0f, to, infiniteRepeatable(tween(ms, easing = easing), mode), label)
    return SceneClock(
        ground = cycle(2_600, label = "ground"),
        spin = cycle(700, label = "spin"),
        bob = cycle(340, to = 3f, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f), mode = RepeatMode.Reverse, label = "bob"),
        lines = cycle(910, label = "lines"),
        puffs = cycle(1_300, label = "puffs"),
    )
}

@Composable
private fun ScooterScene(reduceMotion: Boolean, modifier: Modifier) {
    val body = imageResource(Res.drawable.capybara_scooter)
    val tireMask = imageResource(Res.drawable.scooter_tire_mask)
    val hubMask = imageResource(Res.drawable.scooter_hub_mask)
    val clock = if (reduceMotion) null else rememberSceneClock()
    val tile = remember { groundTile() }
    // Black outlines vanish on a dark surface, so the scene sits on a light plate there.
    val plate = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        PlateOnDark
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    }

    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        // Plate: a soft ellipse inset 6 % top, 8 % sides, 4 % bottom of the stage box.
        drawOval(plate, Offset(size.width * 0.08f, size.height * 0.06f), Size(size.width * 0.84f, size.height * 0.90f))

        // Width fits the scene box (x -123..597). Vertically the artwork spans y 0 (top of the helmet) to 582 (ground),
        // taller than the box, so centre that range instead; nothing is clipped, like the prototype's visible overflow.
        val s = size.width / SCENE_W
        val top = (size.height - ART_H * s) / 2f
        withTransform({
            translate(-SCENE_LEFT * s, top)
            scale(s, s, Offset.Zero)
        }) {
            // 1. ground strip, does not bob: two tiles scroll left, one tile width per loop.
            val scroll = -TILE_WIDTH * (clock?.ground?.value ?: 0f)
            fadedAtEdges {
                translate(SCENE_LEFT + scroll, 0f) { tile.draw(this) }
                translate(SCENE_LEFT + scroll + TILE_WIDTH, 0f) { tile.draw(this) }
            }

            // 2. everything that rides together bobs by up to 3 dp.
            val bobUnits = (clock?.bob?.value ?: 0f) * density / s
            translate(0f, bobUnits) {
                if (clock != null) {
                    drawSpeedLines(clock.lines.value)
                    drawExhaustPuffs(clock.puffs.value)
                }
                drawImage(body, dstSize = IntSize(SRC_W, SRC_H), filterQuality = FilterQuality.Medium)
                val turn = (clock?.spin?.value ?: 0f) * 360f
                masked(tireMask) {
                    drawTireStreaks(FRONT_WHEEL, turn)
                    drawTireStreaks(REAR_WHEEL, turn)
                }
                masked(hubMask) { drawHubDots(FRONT_WHEEL, turn) }
            }

            // 3. ground line, drawn last so it covers the tire bottoms; it does not bob.
            fadedAtEdges {
                drawLine(Ink, Offset(-108f, 562f), Offset(582f, 562f), strokeWidth = 9f, cap = StrokeCap.Round)
            }
        }
    }
}

private val FRONT_WHEEL = Offset(453.5f, 499f)
private val REAR_WHEEL = Offset(97f, 497f)

private fun DrawScope.drawSpeedLines(cycle: Float) {
    // x range, y; each line is a third of a cycle behind the one before it.
    val lines = listOf(Triple(-43f to 17f, 350f, 0f), Triple(-61f to 25f, 366f, 0.343f), Triple(-35f to 23f, 382f, 0.686f))
    for ((span, y, offset) in lines) {
        val phase = (cycle + offset) % 1f
        val alpha = if (phase < 0.25f) phase / 0.25f else (1f - phase) / 0.75f
        val shift = 24f + (-56f - 24f) * phase
        drawLine(
            Ink.copy(alpha = alpha),
            Offset(span.first + shift, y),
            Offset(span.second + shift, y),
            strokeWidth = 7f,
            cap = StrokeCap.Round,
        )
    }
}

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private fun DrawScope.drawExhaustPuffs(cycle: Float) {
    // Three circles (x, y, r); the outline is the same circles 3 larger in ink, then the fill 2 smaller on top.
    val circles = listOf(Triple(-10f, 0f, 12f), Triple(6f, -7f, 14f), Triple(18f, 3f, 10f))
    val pivot = Offset(3f, -4f) // centre of the cloud's bounding box
    for (i in 0 until 3) {
        val phase = (cycle + i * 0.33f) % 1f
        val e = EaseOut.transform(phase)
        val cloudAlpha = if (phase < 0.7f) 1f - 0.1f * EaseOut.transform(phase / 0.7f) else 0.9f * (1f - EaseOut.transform((phase - 0.7f) / 0.3f))
        translate(-25f + (-110f) * e, 490f + (-26f) * e) {
            // The cloud fades as one shape, so overlapping circles do not show through each other.
            drawIntoCanvas { it.saveLayer(Rect(-120f, -120f, 120f, 120f), Paint().apply { alpha = cloudAlpha }) }
            scale(0.35f + (1.35f - 0.35f) * e, pivot) {
                circles.forEach { (x, y, r) -> drawCircle(Ink, r + 3f, Offset(x, y)) }
                circles.forEach { (x, y, r) -> drawCircle(PuffFill, r - 2f, Offset(x, y)) }
            }
            drawIntoCanvas { it.restore() }
        }
    }
}

/** Four 0.55 rad arcs around the wheel centre, turning clockwise. */
private fun DrawScope.drawTireStreaks(center: Offset, degrees: Float) {
    withTransform({
        translate(center.x, center.y)
        rotate(degrees, Offset.Zero)
    }) {
        val sweep = (0.55 * 180.0 / PI).toFloat()
        for (i in 0 until 4) {
            drawArc(
                TireStreak, startAngle = i * 90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(-50f, -50f), size = Size(100f, 100f),
                style = Stroke(width = 6f, cap = StrokeCap.Round),
            )
        }
    }
}

/** Three dots on a 19 px circle around the hub. */
private fun DrawScope.drawHubDots(center: Offset, degrees: Float) {
    withTransform({
        translate(center.x, center.y)
        rotate(degrees, Offset.Zero)
    }) {
        for (i in 0 until 3) {
            val angle = i * 2.0 * PI / 3.0
            drawCircle(HubDot, 4.5f, Offset((19 * cos(angle)).toFloat(), (19 * sin(angle)).toFloat()))
        }
    }
}

// ── layers ──────────────────────────────────────────────────────────────────

/** Fades what [block] draws out over the outer 12 % of the scene box on each side. */
private inline fun DrawScope.fadedAtEdges(block: DrawScope.() -> Unit) {
    drawIntoCanvas { it.saveLayer(FadeBounds, Paint()) }
    block()
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent,
            startX = FadeBounds.left, endX = FadeBounds.right,
        ),
        topLeft = FadeBounds.topLeft,
        size = FadeBounds.size,
        blendMode = BlendMode.DstIn,
    )
    drawIntoCanvas { it.restore() }
}

/** Keeps only the part of what [block] draws that lies inside the opaque area of [mask] (aligned with the body image). */
private inline fun DrawScope.masked(mask: ImageBitmap, block: DrawScope.() -> Unit) {
    drawIntoCanvas { it.saveLayer(Rect(0f, 0f, SRC_W.toFloat(), SRC_H.toFloat()), Paint()) }
    block()
    drawImage(mask, dstSize = IntSize(SRC_W, SRC_H), blendMode = BlendMode.DstIn, filterQuality = FilterQuality.Medium)
    drawIntoCanvas { it.restore() }
}

// ── ground tile ─────────────────────────────────────────────────────────────

/** Mounds, stones and grass tufts of one 760 wide tile, built once. Base line y = 559. */
private class GroundTile(private val shapes: List<Pair<Path, Color>>) {
    fun draw(scope: DrawScope) {
        for ((path, fill) in shapes) {
            scope.drawPath(path, fill)
            scope.drawPath(path, Ink, style = Stroke(width = 6f, join = StrokeJoin.Round))
        }
    }
}

private fun groundTile(): GroundTile {
    val base = 559f
    val shapes = mutableListOf<Pair<Path, Color>>()
    fun mound(x: Float, w: Float, h: Float) {
        shapes += Path().apply {
            moveTo(x, base)
            cubicTo(x + w * .12f, base - h * .8f, x + w * .32f, base - h, x + w * .45f, base - h * .78f)
            cubicTo(x + w * .56f, base - h * 1.05f, x + w * .85f, base - h * .95f, x + w, base)
            close()
        } to Color(0xFFB98B5A)
    }
    fun stone(x: Float, rx: Float, ry: Float, color: Color = Color(0xFFA9ADAB)) {
        val cy = base - 1f - ry * .55f
        shapes += Path().apply { addOval(Rect(x - rx, cy - ry, x + rx, cy + ry)) } to color
    }
    fun grass(x: Float, k: Float) {
        shapes += Path().apply {
            moveTo(x, base)
            lineTo(x + 5 * k, 538 - 6 * k)
            lineTo(x + 11 * k, 556f)
            lineTo(x + 18 * k, 522 - 6 * k)
            lineTo(x + 24 * k, 556f)
            lineTo(x + 31 * k, 534 - 4 * k)
            lineTo(x + 36 * k, base)
            close()
        } to Color(0xFF5AA05A)
    }
    mound(30f, 96f, 34f); stone(214f, 18f, 12f); stone(244f, 11f, 8f, Color(0xFFC4C8C6))
    grass(330f, 1f); mound(470f, 70f, 24f); grass(610f, 1.15f); stone(700f, 14f, 10f)
    return GroundTile(shapes)
}

// ── previews ────────────────────────────────────────────────────────────────

private val previewMessages = listOf("Mencari tempat seru…", "Mengecek di peta…", "Memilih yang paling cocok…", "Hampir sampai…")

@Preview
@Composable
private fun ScooterLoadingPreview() = IrisPreview {
    ScooterLoadingContent(previewMessages, Modifier, hint = "Biasanya 5–10 detik", reduceMotion = false)
}

@Preview
@Composable
private fun ScooterLoadingReduceMotionPreview() = IrisPreview {
    ScooterLoadingContent(previewMessages, Modifier, hint = "Biasanya 5–10 detik", reduceMotion = true)
}
