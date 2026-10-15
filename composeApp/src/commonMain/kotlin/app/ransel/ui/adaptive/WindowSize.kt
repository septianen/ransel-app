package app.ransel.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width from which the web layout applies: 96 dp navigation rail, content, 360 dp side panel (TRD "Layout"). */
val ExpandedWidth: Dp = 1240.dp

/** True when the window is at least [ExpandedWidth] wide (desktop browsers; phones never are). */
@Composable
fun rememberIsExpanded(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { widthPx.toDp() } >= ExpandedWidth
}
