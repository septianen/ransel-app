package app.ransel.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.trips_create

/**
 * Web navigation (windows ≥ 1240 dp): 96 dp rail with the "create trip" button on top and the four
 * tabs below (design "Detail trip versi web"). Replaces the bottom bar.
 */
@Composable
fun RanselNavigationRail(
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier,
    exploreHasSession: Boolean = false,
) {
    Row96(modifier) {
        Box(
            Modifier
                .size(56.dp)
                .clip(IrisShape.lg)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.trips_create), onClick = onCreateTrip),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(
                Symbols.add,
                contentDescription = stringResource(Res.string.trips_create),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                Modifier
                    .width(80.dp)
                    .clip(IrisShape.md)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .width(56.dp)
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(
                        tab.symbol,
                        filled = isSelected,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (tab == MainTab.EXPLORE && exploreHasSession) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 12.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                }
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 96 dp wide column with the rail's surface, right hairline and spacing. */
@Composable
private fun Row96(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(modifier.fillMaxHeight()) {
        Column(
            Modifier
                .width(96.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) { content() }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
