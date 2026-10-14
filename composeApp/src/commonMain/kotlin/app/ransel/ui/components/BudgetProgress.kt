package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.ui.format.formatMoney
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.budget_of
import ransel.composeapp.generated.resources.budget_over
import ransel.composeapp.generated.resources.budget_remaining

/**
 * Remaining budget + progress bar (Home, Expenses). Turns `error` when spending passes the budget.
 * All amounts are in the trip's base currency.
 */
@Composable
fun BudgetProgress(
    spentMinor: Long,
    budgetMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val remaining = budgetMinor - spentMinor
    val over = remaining < 0
    val fraction = if (budgetMinor <= 0) 0f else (spentMinor.toFloat() / budgetMinor).coerceIn(0f, 1f)
    val barColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(if (over) Res.string.budget_over else Res.string.budget_remaining),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyText(
            amountMinor = if (over) -remaining else remaining,
            currency = currency,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(barColor),
            )
        }
        Text(
            text = stringResource(Res.string.budget_of, formatMoney(budgetMinor, currency)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun BudgetProgressPreview() = IrisPreview {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BudgetProgress(spentMinor = 3_250_000, budgetMinor = 5_000_000, currency = "IDR")
        BudgetProgress(spentMinor = 5_400_000, budgetMinor = 5_000_000, currency = "IDR")
    }
}
