package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.ui.format.formatMoney
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.budget_over
import ransel.composeapp.generated.resources.budget_remaining
import ransel.composeapp.generated.resources.budget_spent
import ransel.composeapp.generated.resources.budget_total
import ransel.composeapp.generated.resources.budget_total_spent

/**
 * The "Sisa budget" card of Expenses (design: `accent-magenta`, white text and bar). Without a budget it
 * shows the total spent only. Spending past the budget switches the label to "Melebihi budget" and the
 * amount to what is over. All amounts are in the trip's base currency.
 */
@Composable
fun BudgetProgress(
    spentMinor: Long,
    budgetMinor: Long?,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val onAccent = IrisTheme.accents.onAccent
    val hasBudget = budgetMinor != null && budgetMinor > 0
    val remaining = (budgetMinor ?: 0) - spentMinor
    val over = hasBudget && remaining < 0
    val fraction = if (hasBudget) (spentMinor.toFloat() / budgetMinor!!).coerceIn(0f, 1f) else 0f
    val percent = if (hasBudget) (spentMinor * 100 / budgetMinor!!).toInt() else 0

    Column(
        modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(IrisTheme.accents.magenta)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Symbol(Symbols.accountBalanceWallet, size = 18.dp, filled = true, tint = onAccent)
            Text(
                stringResource(
                    when {
                        !hasBudget -> Res.string.budget_total_spent
                        over -> Res.string.budget_over
                        else -> Res.string.budget_remaining
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = onAccent,
            )
        }
        Text(
            formatMoney(if (!hasBudget) spentMinor else kotlin.math.abs(remaining), currency),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            color = onAccent,
        )
        if (hasBudget) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(onAccent.copy(alpha = 0.3f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(CircleShape).background(onAccent))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(Res.string.budget_spent, formatMoney(spentMinor, currency), "$percent%"),
                    style = MaterialTheme.typography.bodySmall,
                    color = onAccent,
                )
                Text(
                    stringResource(Res.string.budget_total, formatMoney(budgetMinor!!, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = onAccent,
                )
            }
        }
    }
}

@Preview
@Composable
private fun BudgetProgressPreview() = IrisPreview {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BudgetProgress(spentMinor = 3_250_000, budgetMinor = 5_000_000, currency = "IDR")
        BudgetProgress(spentMinor = 5_400_000, budgetMinor = 5_000_000, currency = "IDR")
        BudgetProgress(spentMinor = 120_000, budgetMinor = null, currency = "IDR")
    }
}
