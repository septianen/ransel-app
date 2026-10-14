package app.ransel.feature.packing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.model.PackingCategory
import app.ransel.ui.category.label
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.PackingCategoryIcon
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.error_required
import ransel.composeapp.generated.resources.itinerary_close
import ransel.composeapp.generated.resources.packing_add
import ransel.composeapp.generated.resources.packing_add_another
import ransel.composeapp.generated.resources.packing_add_another_hint
import ransel.composeapp.generated.resources.packing_delete_item
import ransel.composeapp.generated.resources.packing_name
import ransel.composeapp.generated.resources.packing_quantity
import ransel.composeapp.generated.resources.packing_quantity_decrease
import ransel.composeapp.generated.resources.packing_quantity_hint
import ransel.composeapp.generated.resources.packing_quantity_increase
import ransel.composeapp.generated.resources.packing_save
import ransel.composeapp.generated.resources.packing_sheet_edit_title
import ransel.composeapp.generated.resources.packing_sheet_subtitle
import ransel.composeapp.generated.resources.activity_category

/**
 * Add (existing == null) or edit a packing item (design "Tambah barang"). "Tambah lagi" saves and
 * clears the name but keeps the category, for quick entry of several items.
 */
@Composable
fun AddPackingItemSheet(
    tripTitle: String,
    itemCount: Int,
    existing: PackingItemDto?,
    onSave: (name: String, category: PackingCategory, quantity: Int, keepOpen: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: PackingCategory.CLOTHING) }
    var quantity by remember { mutableStateOf(existing?.quantity ?: 1) }
    var showError by remember { mutableStateOf(false) }

    fun submit(keepOpen: Boolean) {
        if (name.isBlank()) {
            showError = true
            return
        }
        onSave(name, category, quantity, keepOpen)
        if (keepOpen) {
            name = ""
            quantity = 1
            showError = false
        }
    }

    AdaptiveSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (existing == null) Res.string.packing_add else Res.string.packing_sheet_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(Res.string.packing_sheet_subtitle, tripTitle, itemCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.size(48.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Symbol(
                    Symbols.close,
                    contentDescription = stringResource(Res.string.itinerary_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IrisTextField(
                label = stringResource(Res.string.packing_name),
                value = name,
                onValueChange = {
                    name = it
                    showError = false
                },
                error = if (showError) stringResource(Res.string.error_required) else null,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.activity_category), style = MaterialTheme.typography.labelLarge)
                PackingCategory.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            val selected = option == category
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(IrisShape.lg)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                    )
                                    .border(
                                        BorderStroke(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                        ),
                                        IrisShape.lg,
                                    )
                                    .clickable(role = Role.RadioButton) { category = option }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PackingCategoryIcon(option)
                                Text(
                                    stringResource(option.label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.packing_quantity), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(Res.string.packing_quantity_hint, quantity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StepperButton(Symbols.remove, stringResource(Res.string.packing_quantity_decrease), enabled = quantity > 1) {
                    quantity -= 1
                }
                Text(
                    "$quantity",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn20(),
                )
                StepperButton(Symbols.add, stringResource(Res.string.packing_quantity_increase), enabled = quantity < 99) {
                    quantity += 1
                }
            }
        }
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing == null) {
                    OutlinedButton(
                        onClick = { submit(keepOpen = true) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Symbol(Symbols.playlistAdd)
                        Text(
                            stringResource(Res.string.packing_add_another),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Button(
                    onClick = { submit(keepOpen = false) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.packing_save), style = MaterialTheme.typography.titleMedium) }
            }
            if (existing == null) {
                Text(
                    stringResource(Res.string.packing_add_another_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Symbol(Symbols.delete, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(Res.string.packing_delete_item),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(
            symbol,
            contentDescription = description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

private fun Modifier.widthIn20(): Modifier = this.padding(horizontal = 8.dp)
