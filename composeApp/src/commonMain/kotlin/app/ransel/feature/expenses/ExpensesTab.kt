package app.ransel.feature.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.TripDto
import app.ransel.ui.category.expenseLabel
import app.ransel.ui.category.expenseSymbol
import app.ransel.ui.category.label
import app.ransel.ui.category.tileColor
import app.ransel.ui.clickableSemantics
import app.ransel.ui.components.BudgetProgress
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.MoneyText
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.formatMoneyCompact
import app.ransel.ui.format.formatTime
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.expenses_by_category
import ransel.composeapp.generated.resources.expenses_donut_description
import ransel.composeapp.generated.resources.expenses_before_trip
import ransel.composeapp.generated.resources.expenses_empty_body
import ransel.composeapp.generated.resources.expenses_empty_title
import ransel.composeapp.generated.resources.expenses_record
import ransel.composeapp.generated.resources.expenses_transactions
import ransel.composeapp.generated.resources.money_unit_billion
import ransel.composeapp.generated.resources.money_unit_million
import ransel.composeapp.generated.resources.money_unit_thousand
import ransel.composeapp.generated.resources.trips_spent_label

@Composable
fun ExpensesTab(
    trip: TripDto,
    modifier: Modifier = Modifier,
    viewModel: ExpensesViewModel = koinViewModel(key = "expenses-${trip.id}") { parametersOf(trip) },
) {
    val state by viewModel.state.collectAsState()
    var sheet by remember { mutableStateOf<ExpenseSheetTarget?>(null) }
    val dates = rememberDateFormatter()
    val tz = remember { TimeZone.currentSystemDefault() }

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> Unit
            state.isEmpty -> FullScreenState(
                title = stringResource(Res.string.expenses_empty_title),
                body = stringResource(Res.string.expenses_empty_body, trip.title),
                primaryLabel = stringResource(Res.string.expenses_record),
                onPrimary = { sheet = ExpenseSheetTarget.New },
                illustrationSize = 220.dp,
            )
            else -> {
                val summary = state.summary!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    BudgetProgress(
                        spentMinor = summary.spentMinor,
                        budgetMinor = trip.budgetMinor,
                        currency = trip.baseCurrency,
                    )
                    CategoryBreakdown(summary, trip.baseCurrency)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.expenses_transactions), style = MaterialTheme.typography.titleLarge)
                        state.groups.forEach { group ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    if (group.date < trip.startDate) {
                                        stringResource(Res.string.expenses_before_trip)
                                    } else {
                                        dates.fullWeekdayDayMonth(group.date)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MoneyText(
                                    group.totalMinor, trip.baseCurrency,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            group.items.forEach { expense ->
                                ExpenseRow(expense, tz, onClick = { sheet = ExpenseSheetTarget.Edit(expense.id) })
                            }
                        }
                    }
                }
                RecordFab(
                    onClick = { sheet = ExpenseSheetTarget.New },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }

    sheet?.let { target ->
        val existing = (target as? ExpenseSheetTarget.Edit)?.let { edit ->
            state.groups.flatMap { it.items }.firstOrNull { it.id == edit.id }
        }
        if (target is ExpenseSheetTarget.New || existing != null) {
            ExpenseSheet(
                trip = trip,
                existing = existing,
                onSave = {
                    viewModel.save(it)
                    sheet = null
                },
                onDelete = existing?.let {
                    {
                        viewModel.delete(it.id)
                        sheet = null
                    }
                },
                onDismiss = { sheet = null },
            )
        }
    }
}

sealed interface ExpenseSheetTarget {
    data object New : ExpenseSheetTarget
    data class Edit(val id: String) : ExpenseSheetTarget
}

@Composable
private fun CategoryBreakdown(
    summary: app.ransel.shared.dto.ExpenseSummaryDto,
    currency: String,
) {
    val slices = summary.byCategory.filter { it.amountMinor > 0 }.sortedByDescending { it.amountMinor }
    if (slices.isEmpty()) return
    val colors = slices.map { it.category.tileColor() }
    val total = summary.spentMinor.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.expenses_by_category), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            ExpenseDonut(
                slices = slices.mapIndexed { i, s -> DonutSlice(s.amountMinor, colors[i]) },
                description = stringResource(Res.string.expenses_donut_description),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatMoneyCompact(
                            summary.spentMinor, currency,
                            thousand = stringResource(Res.string.money_unit_thousand),
                            million = stringResource(Res.string.money_unit_million),
                            billion = stringResource(Res.string.money_unit_billion),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(Res.string.trips_spent_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slices.forEachIndexed { i, slice ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                        Text(
                            stringResource(slice.category.expenseLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(slice.amountMinor * 100 / total)}%",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseDto, tz: TimeZone, onClick: () -> Unit) {
    val time = expense.spentAt.toLocalDateTime(tz).time
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(IrisShape.md).background(expense.category.tileColor()),
            contentAlignment = Alignment.Center,
        ) { Symbol(expense.category.expenseSymbol, size = 22.dp, filled = true, tint = IrisTheme.accents.onAccent) }
        Column(Modifier.weight(1f)) {
            Text(
                expense.note?.takeIf { it.isNotBlank() } ?: stringResource(expense.category.expenseLabel),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatTime(time) + " · " + stringResource(expense.category.expenseLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(expense.amountMinor, expense.currency), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(expense.paymentMethod.label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f))
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickableSemantics(null, onClick)
            .padding(start = 20.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(Symbols.receiptLong, tint = MaterialTheme.colorScheme.onPrimary)
        Text(
            stringResource(Res.string.expenses_record),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
