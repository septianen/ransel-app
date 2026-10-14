package app.ransel.ui.symbols

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.Font
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.material_symbols_rounded
import ransel.composeapp.generated.resources.material_symbols_rounded_fill

/**
 * Material Symbols Rounded glyph (weight 500, same as the design). [symbol] is a constant from [Symbols].
 * The bundled fonts are static subsets: `material_symbols_rounded` (outline) and
 * `material_symbols_rounded_fill` (filled), so no variable-font support is needed on any target.
 *
 * Decorative by default; pass [contentDescription] when the icon alone carries meaning.
 */
@Composable
fun Symbol(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    val outline = FontFamily(Font(Res.font.material_symbols_rounded))
    val fill = FontFamily(Font(Res.font.material_symbols_rounded_fill))
    // Icons ignore the user's font scale: the box is sized in dp, not sp.
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val style = remember(filled, fontSize, outline, fill) {
        TextStyle(
            fontFamily = if (filled) fill else outline,
            fontSize = fontSize,
            lineHeight = fontSize,
            textAlign = TextAlign.Center,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        )
    }
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Box(modifier.size(size).then(semanticsModifier), contentAlignment = Alignment.Center) {
        Text(text = symbol, style = style, color = tint, maxLines = 1, softWrap = false)
    }
}
