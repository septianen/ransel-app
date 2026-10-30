package app.ransel.ui.symbols

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.ui.theme.IrisShape

/**
 * The RANSEL logo without its tile: a backpack whose side curve and bottom notch hint at an R
 * (`docs/design/logo/ransel-mark.svg`, cropped to the 66 safe zone). One colour, the pocket is a hole, so it can be
 * tinted like a [Symbol]. Decorative by default.
 */
@Composable
fun RanselMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Image(RanselMarkVector, contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** [RanselMark] on its `primary` tile, as in the app icon. [size] is the tile; the mark fills the middle 80%. */
@Composable
fun RanselLogoTile(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(modifier.size(size).background(MaterialTheme.colorScheme.primary, IrisShape.sm).padding(size * 0.1f)) {
        RanselMark(size = size * 0.8f, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

// Same paths as the SVG, on a 72 grid starting at (18, 18) of the 108 launcher grid.
private val RanselMarkVector: ImageVector by lazy {
    ImageVector.Builder("RanselMark", 24.dp, 24.dp, viewportWidth = 72f, viewportHeight = 72f).apply {
        addPath(
            pathData = addPathNodes("M27,15 a9,9 0 0 1 18,0"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 5f,
            strokeLineCap = StrokeCap.Round,
        )
        addPath(
            pathData = addPathNodes(
                "M13,34 a21,21 0 0 1 21,-21 h4 a21,21 0 0 1 21,21 v8 c0,2.5 -1,4 -2.5,5.5 c1.5,1.5 2.5,3 2.5,5.5 v1 " +
                    "a12,12 0 0 1 -12,12 H39 L35,60 V66 H25 a12,12 0 0 1 -12,-12 z " +
                    "M28.5,31 h15 a2.5,2.5 0 0 1 0,5 h-15 a2.5,2.5 0 0 1 0,-5 z",
            ),
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        )
    }.build()
}

