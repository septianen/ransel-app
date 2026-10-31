package app.ransel.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Parts shared by the capybara loading scenes (CaveCartLoading, ReceiptLoading, SurfUploadProgress): the status line,
 * the dots, the frame clock and the drawing helpers that animate parts of an illustration. Scene numbers are in the
 * 1024 x 1024 space of the illustrations. Prototype: the "Animasi Loading Capybara" artifact.
 */
internal const val UNITS = 1024f
internal const val TAU = (2 * PI).toFloat()
private const val MESSAGE_EVERY_MS = 2_500L
internal val Ink = Color(0xFF3A2418)
internal val Fur = Color(0xFFC4641A)
private val PlateOnDark = Color(0xFFE9EFE5)
private val SparkleGold = Color(0xFFFFB800)

/** Dark outlines vanish on a dark surface, so the scene sits on a light plate there. */
@Composable
internal fun scenePlate(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) PlateOnDark else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
}

/** Seconds since the scene started, updated every frame. Read only inside the draw lambda: a frame redraws, never recomposes. */
@Composable
internal fun rememberSceneTime(running: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = ((it - start) / 1_000_000_000.0 % 3_600.0).toFloat() }
    }
    return time
}

// ── status line and dots ────────────────────────────────────────────────────

/** [messages] rotate every 2.5 s with a cross-fade; with reduced motion the first one stays. */
@Composable
internal fun RotatingMessage(messages: List<String>, reduceMotion: Boolean) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(messages.size, reduceMotion) {
        if (reduceMotion || messages.size < 2) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(MESSAGE_EVERY_MS)
            index = (index + 1) % messages.size
        }
    }
    AnimatedContent(
        targetState = index,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
        contentAlignment = Alignment.Center,
        label = "loading-message",
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
internal fun PulsingDots(reduceMotion: Boolean) {
    val clock = if (reduceMotion) null else rememberInfiniteTransition(label = "loading-dots").animateFloat(
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

// ── timing ──────────────────────────────────────────────────────────────────

internal fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p
internal fun ease(p: Float) = p * p * (3 - 2 * p)

/** Where [u] is between [a] and [b], clamped to 0..1. */
internal fun span(u: Float, a: Float, b: Float) = ((u - a) / (b - a)).coerceIn(0f, 1f)

// ── drawing parts of the artwork ────────────────────────────────────────────

internal fun polygon(points: List<Offset>) = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}

internal fun pointsOf(vararg xy: Int): List<Offset> = xy.toList().chunked(2) { (x, y) -> Offset(x.toFloat(), y.toFloat()) }

internal fun DrawScope.drawArtwork(image: ImageBitmap) =
    drawImage(image, dstSize = IntSize(UNITS.toInt(), UNITS.toInt()), filterQuality = FilterQuality.Medium)

/** Everything above [anchorY] breathes by [k]; the seam at the anchor stays continuous so it never shows. */
internal fun DrawScope.drawBreathing(image: ImageBitmap, anchorY: Float, k: Float) {
    clipRect(0f, anchorY, UNITS, UNITS) { drawArtwork(image) }
    clipRect(0f, 0f, UNITS, anchorY + 1f) {
        scale(1f, k, Offset(0f, anchorY)) { drawArtwork(image) }
    }
}

/** A clipped copy of part of the artwork, scaled from its anchor. It only grows, so it always covers the original. */
internal fun DrawScope.drawPiece(image: ImageBitmap, shape: Path, anchor: Offset, scale: Float, radians: Float = 0f) {
    withTransform({
        rotate(radians * 180f / PI.toFloat(), anchor)
        scale(scale, scale, anchor)
    }) {
        clipPath(shape) { drawArtwork(image) }
    }
}

/** A blink around [u] = 0.42 of the loop: the eyes close to a curved line and open again. */
internal fun DrawScope.drawBlink(u: Float, eyes: List<Offset>, rx: Float, ry: Float) {
    val b = max(1f - abs(u - 0.42f) / 0.018f, 0f)
    if (b <= 0f) return
    for (eye in eyes) {
        val h = (ry + 3f) * b
        drawOval(Fur, Offset(eye.x - rx - 3f, eye.y - h), Size((rx + 3f) * 2, h * 2))
        val lid = Path().apply {
            moveTo(eye.x - rx, eye.y)
            quadraticTo(eye.x, eye.y + 8f, eye.x + rx, eye.y)
        }
        drawPath(lid, Ink, style = Stroke(width = 6f, cap = StrokeCap.Round))
    }
}

/** Six gold rays bursting out from [center] while [p] runs 0 → 1. */
internal fun DrawScope.drawSparkle(center: Offset, p: Float) {
    if (p <= 0f || p >= 1f) return
    for (i in 0 until 6) {
        val a = i * TAU / 6 - 0.3f
        val r0 = 14f + 34f * p
        val r1 = r0 + 20f * (1 - p)
        drawLine(
            SparkleGold.copy(alpha = 1 - p),
            Offset(center.x + r0 * cos(a), center.y + r0 * sin(a)),
            Offset(center.x + r1 * cos(a), center.y + r1 * sin(a)),
            strokeWidth = 8f,
            cap = StrokeCap.Round,
        )
    }
}
