package app.ransel.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import app.ransel.ui.format.formatMoney
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview

/** An amount stored as `amountMinor` + currency, shown the Indonesian way ("Rp50.000"). */
@Composable
fun MoneyText(
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = formatMoney(amountMinor, currency),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
    )
}

@Preview
@Composable
private fun MoneyTextPreview() = IrisPreview {
    MoneyText(
        amountMinor = 3_250_000,
        currency = "IDR",
        style = MaterialTheme.typography.headlineMedium,
    )
}
