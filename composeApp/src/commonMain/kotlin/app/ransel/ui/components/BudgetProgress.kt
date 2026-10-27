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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.ransel.ui.format.formatMoney
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.budget_over
import ransel.composeapp.generated.resources.budget_remaining
import ransel.composeapp.generated.resources.budget_spent
import ransel.composeapp.generated.resources.budget_total
import ransel.composeapp.generated.resources.budget_total_spent

/**
 * The "Sisa budget" card of Expenses, a [HeroCard] (green, owner 27 Oct 2026; the design had `accent-magenta`).
 * Without a budget it shows the total spent only. Spending past the budget switches the label to "Melebihi budget"
 * and the amount to what is over; the card stays green. All amounts are in the trip's base currency.
 */
@Composable
fun BudgetProgress(
    spentMinor: Long,
    budgetMinor: Long?,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val hasBudget = budgetMinor != null && budgetMinor > 0
    val remaining = (budgetMinor ?: 0) - spentMinor
    val over = hasBudget && remaining < 0
    val fraction = if (hasBudget) (spentMinor.toFloat() / budgetMinor!!).coerceIn(0f, 1f) else 0f
    val percent = if (hasBudget) (spentMinor * 100 / budgetMinor!!).toInt() else 0

    HeroCard(
        label = stringResource(
            when {
                !hasBudget -> Res.string.budget_total_spent
                over -> Res.string.budget_over
                else -> Res.string.budget_remaining
            },
        ),
        value = formatMoney(if (!hasBudget) spentMinor else kotlin.math.abs(remaining), currency),
        symbol = Symbols.accountBalanceWallet,
        modifier = modifier,
    ) {
        if (hasBudget) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(onPrimary.copy(alpha = 0.3f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(CircleShape).background(onPrimary))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(Res.string.budget_spent, formatMoney(spentMinor, currency), "$percent%"),
                    style = MaterialTheme.typography.bodySmall,
                    color = onPrimary,
                )
                Text(
                    stringResource(Res.string.budget_total, formatMoney(budgetMinor!!, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = onPrimary,
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
