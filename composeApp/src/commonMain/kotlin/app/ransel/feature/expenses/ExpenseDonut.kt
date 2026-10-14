package app.ransel.feature.expenses

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** One slice of the donut: [amountMinor] drawn in [color]. Zero slices are skipped. */
data class DonutSlice(val amountMinor: Long, val color: Color)

/** 128 dp donut with 16 dp stroke (design "Per kategori"); [center] is drawn in the hole. */
@Composable
fun ExpenseDonut(
    slices: List<DonutSlice>,
    description: String,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit = {},
) {
    val visible = slices.filter { it.amountMinor > 0 }
    val total = visible.sumOf { it.amountMinor }.toFloat()
    Box(
        modifier.size(128.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(128.dp)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val gap = if (visible.size > 1) 3f else 0f
            var start = -90f
            visible.forEach { slice ->
                val sweep = slice.amountMinor / total * 360f
                drawArc(
                    color = slice.color,
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        center()
    }
}
