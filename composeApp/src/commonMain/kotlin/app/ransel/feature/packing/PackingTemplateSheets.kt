package app.ransel.feature.packing

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PackingTemplateDto
import app.ransel.shared.model.PackingCategory
import app.ransel.ui.category.label
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.PackingCategoryIcon
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.state_retry
import ransel.composeapp.generated.resources.template_add_items
import ransel.composeapp.generated.resources.template_apply_title
import ransel.composeapp.generated.resources.template_builtin
import ransel.composeapp.generated.resources.template_contents
import ransel.composeapp.generated.resources.template_error
import ransel.composeapp.generated.resources.template_keep_quantities
import ransel.composeapp.generated.resources.template_keep_quantities_hint
import ransel.composeapp.generated.resources.template_mine
import ransel.composeapp.generated.resources.template_name
import ransel.composeapp.generated.resources.template_name_error
import ransel.composeapp.generated.resources.template_none
import ransel.composeapp.generated.resources.template_save
import ransel.composeapp.generated.resources.template_save_from_list
import ransel.composeapp.generated.resources.template_save_note
import ransel.composeapp.generated.resources.template_save_subtitle
import ransel.composeapp.generated.resources.template_save_title
import ransel.composeapp.generated.resources.template_share
import ransel.composeapp.generated.resources.template_share_hint
import ransel.composeapp.generated.resources.template_size
import ransel.composeapp.generated.resources.template_skip_existing

// ── "Pakai template" ────────────────────────────────────────────────────────

@Composable
fun ApplyTemplateSheet(
    state: TemplatesUiState,
    existing: List<PackingItemDto>,
    onSelect: (String) -> Unit,
    onSkipChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onApply: () -> Unit,
    onSaveCurrent: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val selected = state.selected
    val toAdd = selected?.let { templateItemsToAdd(it, existing, state.skipExisting).size } ?: 0
    val skipped = (selected?.items?.size ?: 0) - (selected?.let { templateItemsToAdd(it, existing, true).size } ?: 0)
    AdaptiveSheet(onDismiss = onDismiss) {
        SheetHeader(stringResource(Res.string.template_apply_title), null, onDismiss)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.failure != null && state.templates.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(Res.string.template_error), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.state_retry)) }
                }
                else -> {
                    if (state.builtIn.isNotEmpty()) SectionLabel(stringResource(Res.string.template_builtin))
                    state.builtIn.forEach { TemplateCard(it, it.id == state.selectedId, onSelect) }
                    SectionLabel(stringResource(Res.string.template_mine))
                    if (state.mine.isEmpty()) {
                        Text(
                            stringResource(Res.string.template_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.mine.forEach { TemplateCard(it, it.id == state.selectedId, onSelect) }
                    if (onSaveCurrent != null && existing.isNotEmpty()) {
                        TextButton(onClick = onSaveCurrent, modifier = Modifier.heightIn(min = 48.dp)) {
                            Symbol(Symbols.bookmarkAdd, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(Res.string.template_save_from_list),
                                Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
        if (selected != null) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(IrisShape.md)
                        .clickable(role = Role.Checkbox) { onSkipChange(!state.skipExisting) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val scheme = MaterialTheme.colorScheme
                    Box(
                        Modifier.size(24.dp).clip(IrisShape.xs)
                            .background(if (state.skipExisting) scheme.primary else scheme.surface)
                            .border(2.dp, if (state.skipExisting) scheme.primary else scheme.outline, IrisShape.xs),
                        contentAlignment = Alignment.Center,
                    ) { if (state.skipExisting) Symbol(Symbols.check, size = 18.dp, tint = scheme.onPrimary) }
                    Text(
                        stringResource(Res.string.template_skip_existing, skipped),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Button(
                    onClick = onApply,
                    enabled = !state.busy && toAdd > 0,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(Res.string.template_add_items, toAdd),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun PackingTemplateDto.categories(): List<Pair<PackingCategory, List<String>>> =
    PackingCategory.entries.mapNotNull { c ->
        items.filter { it.category == c }.map { it.name }.takeIf { it.isNotEmpty() }?.let { c to it }
    }

@Composable
private fun TemplateCard(template: PackingTemplateDto, selected: Boolean, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val categories = template.categories()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, IrisShape.lg)
            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.4f) else scheme.surface)
            .selectable(selected = selected, role = Role.RadioButton) { onSelect(template.id) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(IrisShape.md).background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(if (template.ownerId == null) Symbols.checklist else Symbols.bookmark, filled = true)
            }
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    stringResource(Res.string.template_size, template.items.size, categories.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) scheme.primary else scheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary)) }
        }
        if (selected) {
            categories.forEach { (category, names) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PackingCategoryIcon(category)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(category.label), style = MaterialTheme.typography.titleSmall)
                        Text(
                            names.take(3).joinToString(", ") + if (names.size > 3) ", …" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("${names.size}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ── "Simpan sebagai template" ───────────────────────────────────────────────

@Composable
fun SaveTemplateSheet(
    defaultName: String,
    items: List<PackingItemDto>,
    busy: Boolean,
    onSave: (name: String, keepQuantities: Boolean, share: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    var keep by remember { mutableStateOf(true) }
    var share by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val counts = PackingCategory.entries.map { c -> c to items.count { it.category == c } }.filter { it.second > 0 }

    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SheetHeader(
                stringResource(Res.string.template_save_title),
                stringResource(Res.string.template_save_subtitle),
                onDismiss,
            )
            IrisTextField(
                label = stringResource(Res.string.template_name),
                value = name,
                onValueChange = { name = it; showError = false },
                error = if (showError) stringResource(Res.string.template_name_error) else null,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.template_contents, items.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                counts.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (category, n) ->
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PackingCategoryIcon(category)
                                Text("${stringResource(category.label)} $n", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            SwitchRow(stringResource(Res.string.template_keep_quantities), stringResource(Res.string.template_keep_quantities_hint), keep) { keep = it }
            SwitchRow(stringResource(Res.string.template_share), stringResource(Res.string.template_share_hint), share) { share = it }
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.md).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.info, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(Res.string.template_save_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    if (name.isBlank()) showError = true else onSave(name, keep, share)
                },
                enabled = !busy && items.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.template_save), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
            Symbol(Symbols.close, contentDescription = stringResource(Res.string.action_close))
        }
    }
}
