package app.ransel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.shared.model.Category
import app.ransel.shared.model.PackingCategory
import app.ransel.ui.category.symbol
import app.ransel.ui.category.tileColor
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Itinerary/expense category tile: 40 dp, radius 12, 22 dp filled icon (itinerary-spec "Timeline row").
 * [symbol] overrides the default icon for a more specific place type; the tile color stays the same.
 */
@Composable
fun CategoryIcon(
    category: Category,
    modifier: Modifier = Modifier,
    symbol: String = category.symbol,
) {
    AccentTile(
        color = category.tileColor(),
        symbol = symbol,
        size = 40.dp,
        cornerRadius = 12.dp,
        iconSize = 22.dp,
        modifier = modifier,
    )
}

/** Packing category tile: 32 dp, radius 10, 20 dp filled icon (packing-spec "Category header"). */
@Composable
fun PackingCategoryIcon(
    category: PackingCategory,
    modifier: Modifier = Modifier,
) {
    AccentTile(
        color = category.tileColor(),
        symbol = category.symbol,
        size = 32.dp,
        cornerRadius = 10.dp,
        iconSize = 20.dp,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun CategoryIconPreview() = IrisPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { CategoryIcon(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PackingCategory.entries.forEach { PackingCategoryIcon(it) }
        }
    }
}
