package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The big card at the top of a tab (owner, 27 Oct 2026): solid primary green, white text, a small [label] with an
 * optional [symbol], one big [value] on one line (it shrinks rather than wraps), and [content] under it (a budget
 * bar, the trip dates). Used by `BudgetProgress` (Biaya) and the countdown of the Group tab.
 */
@Composable
fun HeroCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    symbol: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    CompositionLocalProvider(LocalContentColor provides onPrimary) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(IrisShape.lg)
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (symbol != null) Symbol(symbol, size = 18.dp, filled = true, tint = onPrimary)
                Text(label, style = MaterialTheme.typography.labelMedium, color = onPrimary)
            }
            val style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold)
            Text(
                value,
                style = style,
                color = onPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
            )
            content()
        }
    }
}

@Preview
@Composable
private fun HeroCardPreview() = IrisPreview {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeroCard(label = "Sisa budget", value = "Rp1.750.000", symbol = Symbols.accountBalanceWallet) {
            Text("Terpakai Rp3.250.000 (65%)", style = MaterialTheme.typography.bodySmall)
        }
        HeroCard(label = "Bali 4 hari", value = "12 hari lagi") {
            Text("20–23 Nov 2026", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
