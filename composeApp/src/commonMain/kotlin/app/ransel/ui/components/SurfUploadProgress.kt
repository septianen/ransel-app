package app.ransel.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.ui.motion.rememberReduceMotion
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.ic_surfing
import kotlin.math.PI
import kotlin.math.sin

/*
 * The stage is 1024 x 720 units; the capybara sprite keeps the 1024 x 1024 space of `ic_surfing` and is scaled down
 * onto the bar. Prototype: "Selancar di progress bar" in the "Animasi Loading Capybara" artifact.
 */
private const val STAGE_HEIGHT = 720f
private const val BAR_X = 36f
private const val BAR_Y = 610f
private const val BAR_W = 952f
private const val BAR_H = 56f
private const val SPRITE_SCALE = 0.56f
private const val SPRITE_FEET = 815f
/** Without a known progress, the bar fills over 86 % of a 7 s loop and holds full for the rest. */
private const val LOOP_SECONDS = 7f
private const val FILL_UNTIL = 0.86f
private const val STILL_PROGRESS = 0.45f
private val Water = Color(0xFF00A5EC)
private val Foam = Color.White.copy(alpha = 0.75f)
private val Drop = Color(0xFF7CC8EA)
private val DropEdge = Color(0xFF17607F)
private val Crest = polygon(pointsOf(702, 268, 830, 233, 960, 398, 992, 520, 962, 602, 760, 592, 722, 470))
private val Bar = Path().apply { addRoundRect(RoundRect(BAR_X, BAR_Y, BAR_X + BAR_W, BAR_Y + BAR_H, CornerRadius(BAR_H / 2))) }

/**
 * A capybara surfing along a water-filled progress bar while something uploads: the board rocks, the wave crest
 * swells, spray trails behind and the label's letters ripple one after another.
 *
 * [progress] (0..1) moves the capybara and fills the bar; null loops the bar on its own. [description] is read out
 * with the progress. With the system "reduce motion" setting on, the capybara stands still at the current progress
 * (halfway when it is unknown).
 */
@Composable
fun SurfUploadProgress(
    progress: Float?,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
) = SurfUploadProgressContent(progress, label, description, modifier, reduceMotion = rememberReduceMotion())

@Composable
internal fun SurfUploadProgressContent(progress: Float?, label: String, description: String, modifier: Modifier, reduceMotion: Boolean) {
    val shown by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(300), label = "surf-progress")
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            progressBarRangeInfo = if (progress == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SurfScene(
            progress = if (progress == null) null else ({ shown }),
            reduceMotion = reduceMotion,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(UNITS / STAGE_HEIGHT).clip(RoundedCornerShape(16.dp)),
        )
        RippleText(label, reduceMotion)
    }
}

@Composable
private fun SurfScene(progress: (() -> Float)?, reduceMotion: Boolean, modifier: Modifier) {
    val art = imageResource(Res.drawable.ic_surfing)
    val time = rememberSceneTime(running = !reduceMotion)
    val plate = scenePlate()
    Canvas(modifier) {
        val s = size.width / UNITS
        val moving = !reduceMotion
        val t = if (moving) time.value else 0f
        val p = progress?.invoke() ?: if (moving) ((t % LOOP_SECONDS) / LOOP_SECONDS / FILL_UNTIL).coerceIn(0f, 1f) else STILL_PROGRESS
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawRect(plate, Offset.Zero, Size(UNITS, STAGE_HEIGHT))
            drawSurfer(art, lerp(270f, 754f, p), t, moving)
            drawWaterBar(p, t, moving)
        }
    }
}

private fun DrawScope.drawSurfer(art: ImageBitmap, centerX: Float, t: Float, moving: Boolean) {
    translate(centerX, BAR_Y + 18f) {
        scale(SPRITE_SCALE, SPRITE_SCALE, Offset.Zero) {
            translate(-UNITS / 2, -SPRITE_FEET) {
                // Mirrored so the capybara faces the way the bar fills, with the wave behind it.
                scale(-1f, 1f, Offset(UNITS / 2, 0f)) {
                    val bob = if (moving) 14f * sin(t * 2.4f) else 0f
                    val rock = if (moving) 0.035f * sin(t * 1.9f) else 0f
                    withTransform({
                        translate(0f, bob)
                        rotate(rock * 180f / PI.toFloat(), Offset(450f, 700f))
                    }) {
                        drawArtwork(art)
                        drawPiece(art, Crest, Offset(830f, 620f), 1f + if (moving) 0.012f + 0.012f * sin(t * 3.1f) else 0f)
                        if (moving) drawSpray(t)
                    }
                }
            }
        }
    }
}

/** Seven drops thrown up off the back of the board, each a seventh of a cycle behind the one before. */
private fun DrawScope.drawSpray(t: Float) {
    for (i in 0 until 7) {
        val q = (t / 1.1f + i / 7f) % 1f
        val center = Offset(900f + 150f * q - (i % 3) * 22f, 640f - 150f * sin(PI.toFloat() * q) * (0.6f + (i % 2) * 0.4f))
        val radius = 11f - 5f * q
        drawCircle(Drop, radius, center, alpha = 1f - q)
        drawCircle(DropEdge, radius, center, alpha = 1f - q, style = Stroke(width = 4f))
    }
}

/** A white pill that fills with water up to [p], with a foam line rolling along the surface. */
private fun DrawScope.drawWaterBar(p: Float, t: Float, moving: Boolean) {
    drawPath(Bar, Color.White)
    clipPath(Bar) {
        val fill = BAR_W * p
        drawRect(Water, Offset(BAR_X, BAR_Y), Size(fill, BAR_H))
        if (fill > 0f) {
            val foam = Path()
            var i = 0f
            while (i <= fill) {
                val y = BAR_Y + 18f + 6f * sin(i / 34f - if (moving) t * 5f else 0f)
                if (i == 0f) foam.moveTo(BAR_X + i, y) else foam.lineTo(BAR_X + i, y)
                i += 12f
            }
            drawPath(foam, Foam, style = Stroke(width = 6f, cap = StrokeCap.Round))
        }
    }
    drawPath(Bar, Ink, style = Stroke(width = 7f))
}

/** The label with each letter hopping 5 dp in turn, 0.09 s apart, every 1.4 s. */
@Composable
private fun RippleText(text: String, reduceMotion: Boolean) {
    val clock = if (reduceMotion) null else rememberInfiniteTransition(label = "surf-label").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple",
    )
    Row(Modifier.clearAndSetSemantics { }) {
        text.forEachIndexed { index, char ->
            Text(
                char.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    // Keyframes 0 / 15 / 30 %: rest, 5 dp up, rest; still for the remaining 70 %.
                    val phase = clock?.let { ((it.value - index * 0.09f / 1.4f) % 1f + 1f) % 1f } ?: 1f
                    val lift = when {
                        phase < 0.15f -> ease(phase / 0.15f)
                        phase < 0.3f -> ease(1f - (phase - 0.15f) / 0.15f)
                        else -> 0f
                    }
                    translationY = -5.dp.toPx() * lift
                },
            )
        }
    }
}

// ── previews ────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun SurfUploadProgressPreview() = IrisPreview {
    SurfUploadProgressContent(0.6f, "Mengunggah…", "Mengunggah dokumen", Modifier, reduceMotion = false)
}

@Preview
@Composable
private fun SurfUploadProgressLoopPreview() = IrisPreview {
    SurfUploadProgressContent(null, "Mengunggah…", "Mengunggah dokumen", Modifier, reduceMotion = true)
}
