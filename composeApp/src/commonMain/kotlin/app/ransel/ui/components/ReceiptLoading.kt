package app.ransel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ransel.ui.motion.rememberReduceMotion
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.ic_expenses_empty
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Scene numbers are in the 1024 x 1024 space of `ic_expenses_empty` (capybara counting coins next to a piggy bank).
 * The receipt, the bills and the coin are drawn here. Prototype: "Hitung struk" in the "Animasi Loading Capybara" artifact.
 */
private const val LOOP_SECONDS = 3.2f
private const val STILL_AT = 1.45f
private val Magenta = Color(0xFFD92D7B)
private val Tick = Color(0xFF008808)
private val Paper = Color.White
private val Faint = Color(0xFFBDBDBD)
private val Price = Color(0xFF777777)
private val BillFill = Color(0xFFC4E3A6)
private val BillMark = Color(0xFF6FA356)
private val CoinFill = Color(0xFFFFB800)
private val CoinRim = Color(0xFFA56F00)
private val Pig = polygon(pointsOf(668, 775, 678, 690, 700, 676, 760, 652, 840, 658, 900, 700, 942, 712, 932, 738, 906, 742, 896, 800, 888, 868, 800, 874, 716, 866, 688, 812))
private val Rows = listOf(168f, 208f, 248f, 288f, 328f)
private val Eyes = listOf(Offset(418f, 293f), Offset(606f, 293f))

/**
 * Capybara counting money while the AI reads a receipt: a magenta beam scans the receipt and ticks its rows one by
 * one, bills pass from hand to hand and a coin hops into the piggy bank. Under it sit a rotating status line and
 * three pulsing dots.
 *
 * [description] is read out for the whole block. With the system "reduce motion" setting on, the scene is a still
 * frame and the first message stays.
 */
@Composable
fun ReceiptLoading(
    messages: List<String>,
    description: String,
    modifier: Modifier = Modifier,
) = ReceiptLoadingContent(messages, description, modifier, reduceMotion = rememberReduceMotion())

@Composable
internal fun ReceiptLoadingContent(messages: List<String>, description: String, modifier: Modifier, reduceMotion: Boolean) {
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ReceiptScene(reduceMotion, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f))
        RotatingMessage(messages, reduceMotion)
        PulsingDots(reduceMotion)
    }
}

@Composable
private fun ReceiptScene(reduceMotion: Boolean, modifier: Modifier) {
    val art = imageResource(Res.drawable.ic_expenses_empty)
    val time = rememberSceneTime(running = !reduceMotion)
    val plate = scenePlate()
    Canvas(modifier) {
        val s = size.width / UNITS
        val moving = !reduceMotion
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawOval(plate, Offset(40f, 92f), Size(944f, 860f))
            drawReceiptScene(art, if (moving) time.value else STILL_AT, moving)
        }
    }
}

private fun DrawScope.drawReceiptScene(art: ImageBitmap, t: Float, moving: Boolean) {
    val u = (t % LOOP_SECONDS) / LOOP_SECONDS
    val coin = span(u, 0.58f, 0.9f)
    val landed = span(u, 0.9f, 1f)
    val breath = if (moving) 1f + 0.012f * sin(t * TAU / LOOP_SECONDS) else 1f
    drawBreathing(art, 720f, breath)
    // The piggy bank swells and wobbles as the coin lands.
    drawPiece(art, Pig, Offset(780f, 868f), 1f + 0.06f * sin(PI.toFloat() * landed), 0.02f * sin(TAU * landed))
    drawReceipt(u, t)
    if (!moving) return
    // Bills go from the stack in the right hand to the left hand, flipping on the way: three per loop.
    val b = (u * 3) % 1f
    val p = ease(span(b, 0f, 0.75f))
    drawBill(
        Offset(lerp(572f, 462f, p), lerp(610f, 592f, p) - 74f * sin(PI.toFloat() * p)),
        flip = max(0.12f, abs(cos(PI.toFloat() * p))),
        alpha = 1f - span(b, 0.78f, 0.96f),
    )
    if (coin > 0f && coin < 1f) {
        drawCoin(
            Offset(lerp(630f, 795f, coin), lerp(866f, 676f, coin) - 190f * sin(PI.toFloat() * coin)),
            spin = abs(cos(coin * PI.toFloat() * 4)) * 0.85f + 0.15f,
        )
    }
    drawSparkle(Offset(795f, 660f), landed)
    drawBlink(u, Eyes, 26f, 30f)
}

/** The receipt floats a little; the beam runs down it in the first 60 % of the loop and each row it passes gets a tick. */
private fun DrawScope.drawReceipt(u: Float, t: Float) {
    val beam = lerp(112f, 448f, ease(span(u, 0f, 0.6f)))
    val fade = 1f - span(u, 0.88f, 1f)
    val bob = 8f * sin(t * TAU / LOOP_SECONDS)
    translate(0f, bob) {
        rotate(-0.07f * 180f / PI.toFloat(), Offset(165f, 290f)) {
            val paper = Path().apply {
                moveTo(55f, 95f); lineTo(275f, 95f); lineTo(275f, 470f)
                var x = 275f
                while (x > 55f) { lineTo(x - 11f, 486f); lineTo(x - 22f, 470f); x -= 22f }
                close()
            }
            drawPath(paper, Paper)
            drawPath(paper, Ink, style = Stroke(width = 6f, join = StrokeJoin.Round))

            fun line(color: Color, x1: Float, x2: Float, y: Float, width: Float, alpha: Float = 1f) =
                drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = width, cap = StrokeCap.Round, alpha = alpha)

            line(Ink, 115f, 215f, 130f, 12f)
            Rows.forEachIndexed { i, y ->
                line(Faint, 80f, if (i % 2 == 1) 160f else 180f, y, 10f)
                line(Price, 205f, 250f, y, 10f)
                val on = if (beam > y) fade else 0f
                if (on > 0f) {
                    line(Tick, 205f, 250f, y, 10f, on)
                    val tick = Path().apply { moveTo(60f, y); lineTo(66f, y + 6f); lineTo(75f, y - 7f) }
                    drawPath(tick, Tick, alpha = on, style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            drawLine(
                Price, Offset(75f, 372f), Offset(255f, 372f), strokeWidth = 4f, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)),
            )
            line(Ink, 80f, 135f, 415f, 16f)
            val total = if (beam > 415f) fade else 0f
            if (total > 0f) line(Tick, 170f, 250f, 415f, 16f, total) else line(Price, 170f, 250f, 415f, 16f)

            val shown = 1f - span(u, 0.6f, 0.68f)
            if (shown > 0f) {
                drawRect(
                    Brush.verticalGradient(0f to Magenta.copy(alpha = 0f), 1f to Magenta.copy(alpha = 0.28f), startY = beam - 54f, endY = beam),
                    Offset(42f, beam - 54f), Size(246f, 54f), alpha = shown,
                )
                line(Magenta, 38f, 292f, beam, 8f, shown)
            }
        }
    }
}

private fun DrawScope.drawBill(center: Offset, flip: Float, alpha: Float) {
    if (alpha <= 0f) return
    translate(center.x, center.y) {
        rotate(-0.35f * 180f / PI.toFloat(), Offset.Zero) {
            scale(flip, 1f, Offset.Zero) {
                drawRect(BillFill, Offset(-46f, -26f), Size(92f, 52f), alpha = alpha)
                drawRect(Ink, Offset(-46f, -26f), Size(92f, 52f), alpha = alpha, style = Stroke(width = 6f, join = StrokeJoin.Round))
                drawCircle(BillMark, 13f, Offset.Zero, alpha = alpha)
            }
        }
    }
}

private fun DrawScope.drawCoin(center: Offset, spin: Float) {
    translate(center.x, center.y) {
        scale(spin, 1f, Offset.Zero) {
            drawCircle(CoinFill, 24f, Offset.Zero)
            drawCircle(Ink, 24f, Offset.Zero, style = Stroke(width = 6f))
            drawCircle(CoinRim, 13f, Offset.Zero, style = Stroke(width = 4f))
        }
    }
}

// ── previews ────────────────────────────────────────────────────────────────

private val previewMessages = listOf("Membaca struk…", "Mencari daftar harga…", "Menjumlahkan totalnya…", "Hampir beres…")

@Preview
@Composable
private fun ReceiptLoadingPreview() = IrisPreview {
    ReceiptLoadingContent(previewMessages, "Membaca struk dengan AI", Modifier, reduceMotion = false)
}

@Preview
@Composable
private fun ReceiptLoadingReduceMotionPreview() = IrisPreview {
    ReceiptLoadingContent(previewMessages, "Membaca struk dengan AI", Modifier, reduceMotion = true)
}
