package app.ransel.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.action_edit_trip
import ransel.composeapp.generated.resources.action_members
import ransel.composeapp.generated.resources.trip_tab_expenses
import ransel.composeapp.generated.resources.trip_tab_itinerary
import ransel.composeapp.generated.resources.trip_tab_packing

enum class TripTab(val label: StringResource) {
    ITINERARY(Res.string.trip_tab_itinerary),
    PACKING(Res.string.trip_tab_packing),
    EXPENSES(Res.string.trip_tab_expenses),
}

/**
 * Header shared by Itinerary, Packing and Expenses: back, trip title + dates, the `group_add`
 * members button, and the three trip tabs. Pass [onMembers] = null to hide the button
 * (Phase 1-3; it appears in Phase 4).
 */
@Composable
fun TripHeader(
    title: String,
    dates: String,
    selectedTab: TripTab,
    onTabSelected: (TripTab) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMembers: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HeaderIconButton(Symbols.arrowBack, stringResource(Res.string.action_back), onBack)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dates,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (onEdit != null) {
                HeaderIconButton(Symbols.edit, stringResource(Res.string.action_edit_trip), onEdit)
            }
            if (onMembers != null) {
                HeaderIconButton(Symbols.groupAdd, stringResource(Res.string.action_members), onMembers)
            }
        }
        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
        ) {
            TripTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
    }
}

@Composable
private fun HeaderIconButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(symbol, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview
@Composable
private fun TripHeaderPreview() = IrisPreview {
    TripHeader(
        title = "Bali 4 hari",
        dates = "20–23 Nov 2026",
        selectedTab = TripTab.ITINERARY,
        onTabSelected = {},
        onBack = {},
        onMembers = {},
    )
}
