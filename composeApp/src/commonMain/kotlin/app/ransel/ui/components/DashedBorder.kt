package app.ransel.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 1 dp dashed rounded border (the "Belum dijadwalkan" box). */
fun Modifier.dashedBorder(color: Color, radius: Dp = 16.dp, width: Dp = 1.dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
    val half = width.toPx() / 2
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(half, half),
        size = androidx.compose.ui.geometry.Size(size.width - width.toPx(), size.height - width.toPx()),
        cornerRadius = CornerRadius(radius.toPx()),
        style = stroke,
    )
}
