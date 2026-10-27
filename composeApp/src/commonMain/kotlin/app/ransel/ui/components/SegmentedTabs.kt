package app.ransel.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Two or three views of one tab, e.g. "Pribadi | Bersama" (Packing) and "Ringkasan | Saldo" (Biaya). A Material
 * segmented button in green: M3's default selection is `secondaryContainer`, which is pink in IRIS (owner, 27 Oct 2026).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedTabs(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = scheme.primaryContainer,
        activeContentColor = scheme.onPrimaryContainer,
        activeBorderColor = scheme.primary,
        inactiveContainerColor = scheme.surface,
        inactiveContentColor = scheme.onSurface,
        inactiveBorderColor = scheme.outlineVariant,
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = colors,
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Preview
@Composable
private fun SegmentedTabsPreview() = IrisPreview {
    SegmentedTabs(listOf("Ringkasan", "Saldo"), selected = "Ringkasan", label = { it }, onSelect = {})
}
