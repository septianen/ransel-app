package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.theme.IrisTheme

/** Colored square with a white (on-accent) filled icon. Base of the category tiles. */
@Composable
internal fun AccentTile(
    color: Color,
    symbol: String,
    size: Dp,
    cornerRadius: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(symbol = symbol, size = iconSize, filled = true, tint = IrisTheme.accents.onAccent)
    }
}
