package app.ransel.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.motion.rememberReduceMotion
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_loading_description
import ransel.composeapp.generated.resources.ic_exploring_cave_cart
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * All scene numbers are in the 1024 x 1024 space of `ic_exploring_cave_cart`: the IRIS asset `ic_exploring_cave` cut above
 * the rails (wheels removed too). Ground, ties, rails, pebbles and wheels are drawn here so the track can scroll.
 * One factor scales the space to the canvas. Prototype: the "Animasi Kereta Tambang" artifact.
 */
private val Lamp = Offset(665f, 118f)
private val LampLight = Color(0xFFFFE08A)
private val Dust = Color(0xFFC9A77C)

/**
 * Capybara riding a mine cart through a cave while Explore AI loads: ties and pebbles scroll under it while the
 * wheels turn, the body rattles on its springs, the headlamp pulses, rock specks and speed lines stream past and dust
 * kicks up behind. Under it sit a rotating status line and
 * three pulsing dots.
 *
 * [messages] rotate every 2.5 s. [hint] sits under the message. With the system "reduce motion" setting on, the
 * scene is a still frame and the first message stays.
 */
@Composable
fun CaveCartLoading(
    messages: List<String>,
    modifier: Modifier = Modifier,
    hint: String? = null,
) = CaveCartLoadingContent(messages, modifier, hint, reduceMotion = rememberReduceMotion())

@Composable
internal fun CaveCartLoadingContent(
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
        CartScene(reduceMotion, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f))
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

// ── the scene ───────────────────────────────────────────────────────────────

@Composable
private fun CartScene(reduceMotion: Boolean, modifier: Modifier) {
    val body = imageResource(Res.drawable.ic_exploring_cave_cart)
    val time = rememberSceneTime(running = !reduceMotion)
    val plate = scenePlate()
    val speck = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Ink else MaterialTheme.colorScheme.onSurface

    Canvas(modifier) {
        val s = size.width / UNITS
        drawOval(plate, Offset(size.width * 0.04f, size.height * 0.10f), Size(size.width * 0.92f, size.height * 0.84f))
        val moving = !reduceMotion
        val t = if (moving) time.value else 0f
        withTransform({ scale(s, s, Offset.Zero) }) {
            if (moving) drawSpecks((t / 2.6f) % 1f, speck.copy(alpha = 0.16f))

            // Track: the ground stays, ties and pebbles scroll left; the wheels turn at the same speed.
            val scroll = t * TRACK_SPEED
            drawGround()
            drawTies(scroll)
            drawRail(160f, 950f, 806f)
            drawPebbles(scroll)
            drawRail(140f, 905f, FRONT_RAIL_Y)

            // Body, rider and light rattle on springs above the wheels: a small shake plus a slight pitch.
            val r = (t / 1.8f) % 1f
            val shake = if (!moving) 0f else (4 * sin(2 * PI * 3 * r) + 2 * sin(2 * PI * 7 * r + 1.3)).toFloat() * 0.5f * density / s
            val pitch = if (!moving) 0f else (0.6 * sin(2 * PI * 2 * r + 0.8)).toFloat()
            val glow = if (!moving) 0.55f else 0.35f + 0.5f * (0.5f - 0.5f * cos(PI * ((t / 1.1f) % 2f)).toFloat())
            translate(0f, shake) {
                rotate(pitch, Offset(480f, 850f)) {
                    drawLampCone(glow)
                    drawImage(body, dstSize = IntSize(UNITS.toInt(), UNITS.toInt()), filterQuality = FilterQuality.Medium)
                    drawLampGlow(glow)
                }
            }
            drawWheels(if (moving) scroll / WHEEL_R else 0.4f)

            if (moving) {
                drawSpeedLines((t / 0.9f) % 1f, Ink)
                drawDust((t / 1.3f) % 1f)
            }
        }
    }
}

// ── track and wheels ────────────────────────────────────────────────────────

private const val TIE_GAP = 170f
private const val TRACK_SPEED = 340f // units per second: one tie every 0.5 s
private const val GROUND_LEFT = 140f
private const val GROUND_RIGHT = 975f
private const val WHEEL_R = 55f
private val Wheels = listOf(Offset(368f, 818f), Offset(602f, 818f))
private val FRONT_RAIL_Y = Wheels[0].y + WHEEL_R // the wheels' lowest point rests on the rail's top edge
private val GroundFill = Color(0xFFB8936A)
private val Grain = Color(0x59785434)
private val TieFill = Color(0xFF8A5638)
private val TieGrain = Color(0x733A2418)
private val PebbleFill = Color(0xFFA9ADAB)
private val RailFill = Color(0xFF5E4034)
private val RailTop = Color(0xFF9A7461)
private val WheelTread = Color(0xFF6E4A3A)
private val WheelDisc = Color(0xFF4E3127)
private val WheelHub = Color(0xFF8A6150)
private val WheelShine = Color(0x99D6A078)
private val Pebbles = listOf(
    floatArrayOf(60f, 938f, 14f, 9f), floatArrayOf(150f, 905f, 9f, 6f), floatArrayOf(250f, 948f, 12f, 8f),
    floatArrayOf(330f, 930f, 8f, 6f), floatArrayOf(420f, 955f, 15f, 9f), floatArrayOf(510f, 925f, 9f, 6f),
    floatArrayOf(600f, 945f, 11f, 7f), floatArrayOf(700f, 935f, 13f, 8f), floatArrayOf(780f, 958f, 9f, 6f),
    floatArrayOf(880f, 940f, 12f, 8f), floatArrayOf(960f, 928f, 8f, 5f),
)
private const val PEBBLE_SPAN = 1040f

/** Fades track parts out over the last 80 units at each end of the ground. */
private fun edgeFade(x: Float): Float =
    ((x - GROUND_LEFT) / 80f).coerceIn(0f, 1f) * ((GROUND_RIGHT - x) / 80f).coerceIn(0f, 1f)

private val GroundShape = Path().apply {
    moveTo(150f, 905f)
    cubicTo(140f, 860f, 230f, 840f, 330f, 838f)
    cubicTo(520f, 832f, 760f, 834f, 900f, 842f)
    cubicTo(975f, 846f, 1000f, 880f, 975f, 912f)
    cubicTo(950f, 952f, 820f, 968f, 640f, 972f)
    cubicTo(450f, 976f, 260f, 968f, 190f, 950f)
    cubicTo(160f, 940f, 152f, 925f, 150f, 905f)
    close()
}

/** Sandy speckle on the ground; fixed texture, the pebbles carry the motion. */
private val Grains = List(90) { i ->
    val r = abs((sin(i * 12.9898) * 43758.5453) % 1).toFloat()
    val q = abs((sin(i * 78.233) * 12543.123) % 1).toFloat()
    Triple(170f + r * 790f, 845f + q * 120f, 1.5f + r * q * 3f)
}

private fun DrawScope.drawGround() {
    clipPath(GroundShape) {
        drawPath(GroundShape, GroundFill)
        for ((x, y, radius) in Grains) drawCircle(Grain, radius, Offset(x, y))
    }
}

/** Ties are slanted planks under both rails, one every [TIE_GAP] units. */
private fun DrawScope.drawTies(scroll: Float) {
    val offset = ((scroll % TIE_GAP) + TIE_GAP) % TIE_GAP
    var x = GROUND_LEFT - TIE_GAP + 60f - offset
    while (x < GROUND_RIGHT + TIE_GAP) {
        val alpha = edgeFade(x)
        if (alpha > 0f) {
            val tie = Path().apply {
                moveTo(x + 10f, 822f); lineTo(x + 84f, 822f); lineTo(x + 42f, 928f); lineTo(x - 32f, 928f); close()
            }
            drawPath(tie, TieFill, alpha = alpha)
            drawPath(tie, Ink, alpha = alpha, style = Stroke(width = 6f, join = StrokeJoin.Round))
            drawLine(TieGrain, Offset(x + 28f, 845f), Offset(x + 12f, 885f), strokeWidth = 3f, alpha = alpha)
        }
        x += TIE_GAP
    }
}

private fun DrawScope.drawPebbles(scroll: Float) {
    for ((x0, y, rx, ry) in Pebbles) {
        val x = (((x0 - scroll) % PEBBLE_SPAN) + PEBBLE_SPAN) % PEBBLE_SPAN
        val alpha = edgeFade(x)
        if (alpha <= 0f) continue
        val topLeft = Offset(x - rx, y - ry)
        drawOval(PebbleFill, topLeft, Size(rx * 2, ry * 2), alpha = alpha)
        drawOval(Ink, topLeft, Size(rx * 2, ry * 2), alpha = alpha, style = Stroke(width = 4f))
    }
}

private fun DrawScope.drawRail(x1: Float, x2: Float, y: Float) {
    val height = 24f
    drawRoundRect(RailFill, Offset(x1, y), Size(x2 - x1, height), CornerRadius(4f))
    drawRect(RailTop, Offset(x1 + 4f, y + 3f), Size(x2 - x1 - 8f, 5f))
    drawRoundRect(Ink, Offset(x1, y), Size(x2 - x1, height), CornerRadius(4f), style = Stroke(width = 6f))
}

/**
 * Wheels are drawn here, not taken from the artwork (where the front rail hid their bottoms), so they rest on the
 * rail. [angle] (radians) turns the three bolts; the highlight stays fixed to the light.
 */
private fun DrawScope.drawWheels(angle: Float) {
    fun ring(center: Offset, radius: Float, fill: Color?, stroke: Float) {
        if (fill != null) drawCircle(fill, radius, center)
        if (stroke > 0f) drawCircle(Ink, radius, center, style = Stroke(width = stroke))
    }
    for (center in Wheels) {
        ring(center, WHEEL_R, WheelTread, 6f)
        ring(center, WHEEL_R - 11f, WheelDisc, 5f)
        drawCircle(Ink.copy(alpha = 0.7f), 30f, center, style = Stroke(width = 4f))
        ring(center, 16f, WheelHub, 5f)
        drawCircle(Ink, 5f, center)
        val r = WHEEL_R - 5f
        drawArc(
            WheelShine, startAngle = 198f, sweepAngle = 63f, useCenter = false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
            style = Stroke(width = 4f, cap = StrokeCap.Round),
        )
        for (i in 0 until 3) {
            val a = angle + i * 2f * PI.toFloat() / 3f
            drawCircle(Ink, 5.5f, Offset(center.x + 30f * cos(a), center.y + 30f * sin(a)))
        }
    }
}

/** Seven rock specks streaming left behind the cart; speeds are whole multiples so the loop is seamless. */
private fun DrawScope.drawSpecks(cycle: Float, color: Color) {
    val specks = listOf(
        Triple(120f, 190f, 1), Triple(520f, 120f, 2), Triple(840f, 250f, 1), Triple(300f, 330f, 2),
        Triple(700f, 600f, 1), Triple(160f, 700f, 2), Triple(900f, 760f, 1),
    )
    for ((x0, y, speed) in specks) {
        val x = ((x0 - cycle * UNITS * speed) % UNITS + UNITS) % UNITS
        drawOval(color, Offset(x - 11f, y - 6f), Size(22f, 12f))
    }
}

/** Soft cone of the headlamp, ahead and slightly up; drawn under the artwork so the face stays clear. */
private fun DrawScope.drawLampCone(strength: Float) {
    val cone = Path().apply {
        moveTo(Lamp.x, Lamp.y)
        lineTo(UNITS, Lamp.y - 130f)
        lineTo(UNITS, Lamp.y + 120f)
        close()
    }
    drawPath(
        cone,
        Brush.linearGradient(
            0f to LampLight.copy(alpha = 0.45f * strength),
            1f to Color.Transparent,
            start = Lamp,
            end = Offset(UNITS, Lamp.y),
        ),
    )
}

private fun DrawScope.drawLampGlow(strength: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to LampLight.copy(alpha = 0.85f * strength),
            1f to Color.Transparent,
            center = Lamp,
            radius = 70f,
        ),
        radius = 70f,
        center = Lamp,
    )
}

private fun DrawScope.drawSpeedLines(cycle: Float, color: Color) {
    // x range, y; each line is a third of a cycle behind the one before it.
    val lines = listOf(Triple(30f to 130f, 470f, 0f), Triple(10f to 150f, 560f, 0.343f), Triple(50f to 120f, 650f, 0.686f))
    for ((span, y, offset) in lines) {
        val phase = (cycle + offset) % 1f
        val alpha = if (phase < 0.25f) phase / 0.25f else (1f - phase) / 0.75f
        val shift = 40f - 170f * phase
        drawLine(
            color.copy(alpha = color.alpha * alpha),
            Offset(span.first + shift, y),
            Offset(span.second + shift, y),
            strokeWidth = 12f,
            cap = StrokeCap.Round,
        )
    }
}

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private fun DrawScope.drawDust(cycle: Float) {
    // Three puffs kicked up behind the rear wheel, drifting left and up while they grow and fade.
    for (i in 0 until 3) {
        val phase = (cycle + i * 0.33f) % 1f
        val e = EaseOut.transform(phase)
        val alpha = 0.75f * (1f - e)
        translate(250f - 200f * e, 870f - 70f * e) {
            scale(0.4f + 1.1f * e, Offset.Zero) {
                drawCircle(Dust.copy(alpha = alpha), 22f, Offset(-8f, 2f))
                drawCircle(Dust.copy(alpha = alpha), 26f, Offset(14f, -10f))
                drawCircle(Dust.copy(alpha = alpha), 18f, Offset(30f, 6f))
            }
        }
    }
}

// ── previews ────────────────────────────────────────────────────────────────

private val previewMessages = listOf("Mencari tempat seru…", "Mengecek di peta…", "Memilih yang paling cocok…", "Hampir sampai…")

@Preview
@Composable
private fun CaveCartLoadingPreview() = IrisPreview {
    CaveCartLoadingContent(previewMessages, Modifier, hint = "Biasanya 5–10 detik", reduceMotion = false)
}

@Preview
@Composable
private fun CaveCartLoadingReduceMotionPreview() = IrisPreview {
    CaveCartLoadingContent(previewMessages, Modifier, hint = "Biasanya 5–10 detik", reduceMotion = true)
}
