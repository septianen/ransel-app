package app.ransel.feature.destination

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A destination input that opens [DestinationPicker] instead of a keyboard. [value] empty shows [placeholder].
 * [picked] marks a destination with a map point (pin filled).
 */
@Composable
fun DestinationField(
    label: String,
    value: String,
    onPicked: (DestinationChoice) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    picked: Boolean = false,
    error: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(IrisShape.md)
                .border(BorderStroke(if (error != null) 2.dp else 1.dp, if (error != null) scheme.error else scheme.outline), IrisShape.md)
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(Symbols.locationOn, size = 20.dp, filled = picked, tint = if (picked) scheme.primary else scheme.onSurfaceVariant)
            Text(
                value.ifEmpty { placeholder.orEmpty() },
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Symbol(Symbols.search, size = 20.dp, tint = scheme.onSurfaceVariant)
        }
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = scheme.error)
    }
    if (open) {
        DestinationPicker(
            onPicked = {
                open = false
                onPicked(it)
            },
            onDismiss = { open = false },
        )
    }
}

@Preview
@Composable
private fun DestinationFieldPreview() = IrisPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DestinationField("Destinasi", "Labuan Bajo", onPicked = {}, picked = true)
        DestinationField("Destinasi", "", onPicked = {}, placeholder = "Cari kota, pulau, atau tempat")
    }
}
