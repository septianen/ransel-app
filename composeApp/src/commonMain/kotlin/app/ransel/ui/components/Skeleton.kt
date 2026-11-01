package app.ransel.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.motion.rememberReduceMotion
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.theme.IrisShape
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.state_loading

/*
 * Shimmer placeholders for the first load of server data (owner, 1 Nov 2026): one effect and two shapes, rows
 * ([SkeletonList]) and cards ([SkeletonCards], square in [SkeletonGrid]). A screen that fits neither uses cards; no
 * per-screen skeletons. Only while there is nothing to show yet: quiet refreshes keep the old data, long waits keep
 * the capybara scenes and buttons keep their own spinner.
 */

/** The gray block with a light sweeping across it every 1.2 s; still with reduced motion. */
@Composable
private fun Modifier.shimmer(shape: Shape): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val shine = MaterialTheme.colorScheme.surfaceContainerLowest
    if (rememberReduceMotion()) return clip(shape).background(base)
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    return clip(shape).background(base).drawWithContent {
        val x = size.width * progress
        drawRect(Brush.linearGradient(listOf(base, shine, base), start = Offset(x - size.width / 2, 0f), end = Offset(x + size.width / 2, size.height)))
    }
}

@Composable
private fun Block(width: Modifier, height: Dp, shape: Shape = IrisShape.sm) {
    Box(width.height(height).shimmer(shape))
}

/** Read once by screen readers instead of every gray block. */
@Composable
private fun Modifier.loadingSemantics(): Modifier {
    val label = stringResource(Res.string.state_loading)
    return semantics { contentDescription = label }
}

/** Rows: a round avatar or icon and two lines of text (members, notifications, documents, credits, Saldo). */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 6, padding: Dp = 16.dp) {
    Column(modifier.fillMaxWidth().padding(padding).loadingSemantics(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(rows) { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).shimmer(CircleShape))
                Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // a little variety so the list does not look like a barcode
                    Block(Modifier.fillMaxWidth(if (index % 2 == 0) 0.6f else 0.45f), 14.dp)
                    Block(Modifier.fillMaxWidth(if (index % 2 == 0) 0.35f else 0.5f), 12.dp)
                }
            }
        }
    }
}

/** Cards: rounded boxes (Ringkasan, polls, voting, albums, and any screen that fits no other shape). */
@Composable
fun SkeletonCards(modifier: Modifier = Modifier, cards: Int = 3, cardHeight: Dp = 120.dp, padding: Dp = 16.dp) {
    Column(modifier.fillMaxWidth().padding(padding).loadingSemantics(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Block(Modifier.fillMaxWidth(0.4f), 20.dp)
        repeat(cards) { Block(Modifier.fillMaxWidth(), cardHeight, IrisShape.lg) }
    }
}

/** The card shape made square, for a photo grid; [minCell] matches the grid it stands in for. */
@Composable
fun SkeletonGrid(modifier: Modifier = Modifier, minCell: Dp = 120.dp, rows: Int = 4) {
    BoxWithConstraints(modifier.fillMaxWidth().loadingSemantics()) {
        val columns = (maxWidth / minCell).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(columns) { Box(Modifier.weight(1f).aspectRatio(1f).shimmer(IrisShape.sm)) }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SkeletonListPreview() = IrisPreview { SkeletonList(rows = 3) }

@Preview
@Composable
private fun SkeletonCardsPreview() = IrisPreview { SkeletonCards(cards = 2) }

@Preview
@Composable
private fun SkeletonGridPreview() = IrisPreview { SkeletonGrid(rows = 2) }
