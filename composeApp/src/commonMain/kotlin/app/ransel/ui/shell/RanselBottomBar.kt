package app.ransel.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.stringResource as sr
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.ransel.ui.preview.IrisPreview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_session_pending
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

/**
 * Bottom navigation (design: 64 dp, hairline top border, no indicator pill, selected = primary +
 * filled icon). [exploreHasSession] shows the magenta dot on Explore for an unfinished swipe session.
 */
@Composable
fun RanselBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    exploreHasSession: Boolean = false,
) {
    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
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
                            if (tab == MainTab.EXPLORE && exploreHasSession) {
                                val description = stringResource(Res.string.explore_session_pending)
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-2).dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .semantics { contentDescription = description },
                                ) {
                                    Box(
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary),
                                    )
                                }
                            }
                        }
                    },
                    label = {
                        Text(
                            text = sr(tab.label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        )
                    },
                    modifier = Modifier.height(64.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun RanselBottomBarPreview() = IrisPreview {
    RanselBottomBar(selected = MainTab.HOME, onSelect = {}, exploreHasSession = true)
}
