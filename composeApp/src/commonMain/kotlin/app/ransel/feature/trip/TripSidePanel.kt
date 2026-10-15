package app.ransel.feature.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.feature.packing.PackingGroup
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.ui.category.label
import app.ransel.ui.category.tileColor
import app.ransel.ui.components.BudgetProgress
import app.ransel.ui.components.PackingCategoryIcon
import app.ransel.ui.components.TripTab
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.irisCard
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.expenses_record
import ransel.composeapp.generated.resources.sidepanel_open_packing
import ransel.composeapp.generated.resources.sidepanel_packing_ready
import ransel.composeapp.generated.resources.trip_tab_packing

/**
 * Right-hand panel of the wide web trip screen (design "Detail trip versi web"): remaining budget with
 * a shortcut to record an expense, and the packing summary per category. 360 dp wide.
 * The Unscheduled box stays in the itinerary column: scheduling a place happens there.
 */
@Composable
fun TripSidePanel(
    trip: TripDto,
    onOpenTab: (TripTab) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripSidePanelViewModel = koinViewModel(key = "side-panel-${trip.id}") { parametersOf(trip.id) },
) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.summary?.let { summary ->
            BudgetCard(
                trip, summary,
                onRecordExpense = if (trip.myRole.atLeast(app.ransel.shared.model.Role.EDITOR)) ({ onOpenTab(TripTab.EXPENSES) }) else null,
            )
        }
        if (state.packingGroups.isNotEmpty()) {
            PackingCard(state.packingGroups, onOpenPacking = { onOpenTab(TripTab.PACKING) })
        }
    }
}

@Composable
private fun BudgetCard(trip: TripDto, summary: TripSummaryDto, onRecordExpense: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BudgetProgress(spentMinor = summary.spentMinor, budgetMinor = trip.budgetMinor, currency = trip.baseCurrency)
        if (onRecordExpense != null) {
            Button(onClick = onRecordExpense, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                app.ransel.ui.symbols.Symbol(Symbols.receiptLong, tint = MaterialTheme.colorScheme.onPrimary)
                Text(
                    stringResource(Res.string.expenses_record),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PackingCard(groups: List<PackingGroup>, onOpenPacking: () -> Unit) {
    val packed = groups.sumOf { it.packed }
    val total = groups.sumOf { it.items.size }
    Column(
        Modifier.fillMaxWidth().irisCard(IrisShape.lg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(Res.string.trip_tab_packing),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(Res.string.sidepanel_packing_ready, packed, total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        groups.forEach { group ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PackingCategoryIcon(group.category)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(
                            stringResource(group.category.label),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${group.packed}/${group.items.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val fraction = if (group.items.isEmpty()) 0f else group.packed.toFloat() / group.items.size
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(group.category.tileColor()),
                        )
                    }
                }
            }
        }
        Text(
            stringResource(Res.string.sidepanel_open_packing),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clickable(onClick = onOpenPacking)
                .padding(vertical = 10.dp),
        )
    }
}
