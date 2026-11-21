package app.ransel.ui.shell

import app.ransel.ui.tour.tourTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.content.stringResource as sr
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.ransel.ui.preview.IrisPreview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.tab_account
import ransel.composeapp.generated.resources.tab_explore
import ransel.composeapp.generated.resources.tab_home
import ransel.composeapp.generated.resources.tab_trips

enum class MainTab(val label: StringResource, val symbol: String) {
    HOME(Res.string.tab_home, Symbols.home),
    TRIPS(Res.string.tab_trips, Symbols.luggage),
    EXPLORE(Res.string.tab_explore, Symbols.autoAwesome),
    ACCOUNT(Res.string.tab_account, Symbols.person),
}

/** The new-user tour points at these tabs (Home itself is not a step). */
internal fun MainTab.tourTarget(): app.ransel.ui.tour.TourTarget? = when (this) {
    MainTab.HOME -> null
    MainTab.TRIPS -> app.ransel.ui.tour.TourTarget.TAB_TRIPS
    MainTab.EXPLORE -> app.ransel.ui.tour.TourTarget.TAB_EXPLORE
    MainTab.ACCOUNT -> app.ransel.ui.tour.TourTarget.TAB_ACCOUNT
}

/**
 * Bottom navigation (design: 64 dp, hairline top border, no indicator pill, selected = primary +
 * filled icon). No dot on Explore any more: an unfinished swipe session shows on the trip's "Cari inspirasi dengan AI"
 * entry (PRD v0.8, 21 Nov 2026; [app.ransel.ui.components.SessionPendingDot]).
 */
@Composable
fun RanselBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0),
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = tab == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(tab) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.Transparent,
                    ),
                    icon = {
                        Box(contentAlignment = Alignment.Center) {
                            Symbol(tab.symbol, filled = isSelected, size = 24.dp)
                        }
                    },
                    label = {
                        Text(
                            text = sr(tab.label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        )
                    },
                    modifier = Modifier.height(64.dp)
                        .then(tab.tourTarget()?.let { Modifier.tourTarget(it, cornerRadius = 16.dp, onClick = { onSelect(tab) }) } ?: Modifier),
                )
            }
        }
    }
}

@Preview
@Composable
private fun RanselBottomBarPreview() = IrisPreview {
    RanselBottomBar(selected = MainTab.HOME, onSelect = {})
}
