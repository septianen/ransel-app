package app.ransel.feature.packing

import app.ransel.content.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.shared.domain.validateMemberList
import app.ransel.shared.dto.MemberListDto
import app.ransel.shared.dto.MemberListItemDto
import app.ransel.shared.dto.PackingTemplateDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.PackingCategory
import app.ransel.ui.category.label
import app.ransel.ui.category.packingCategoryChoices
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.components.PackingCategoryIcon
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.newId
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.itinerary_close
import ransel.composeapp.generated.resources.member_list_add_category
import ransel.composeapp.generated.resources.member_list_add_item
import ransel.composeapp.generated.resources.member_list_blank
import ransel.composeapp.generated.resources.member_list_blank_hint
import ransel.composeapp.generated.resources.member_list_copy_mine
import ransel.composeapp.generated.resources.member_list_copy_mine_hint
import ransel.composeapp.generated.resources.member_list_default_name
import ransel.composeapp.generated.resources.member_list_name
import ransel.composeapp.generated.resources.member_list_next
import ransel.composeapp.generated.resources.member_list_remove_item
import ransel.composeapp.generated.resources.member_list_required_hint
import ransel.composeapp.generated.resources.member_list_required_toggle
import ransel.composeapp.generated.resources.member_list_save
import ransel.composeapp.generated.resources.member_list_save_template
import ransel.composeapp.generated.resources.member_list_save_template_hint
import ransel.composeapp.generated.resources.member_list_send_button
import ransel.composeapp.generated.resources.member_list_send_guest
import ransel.composeapp.generated.resources.member_list_send_note
import ransel.composeapp.generated.resources.member_list_send_subtitle
import ransel.composeapp.generated.resources.member_list_send_title
import ransel.composeapp.generated.resources.member_list_send_to_personal
import ransel.composeapp.generated.resources.member_list_start_note
import ransel.composeapp.generated.resources.member_list_start_subtitle
import ransel.composeapp.generated.resources.member_list_start_title
import ransel.composeapp.generated.resources.member_list_template_builtin
import ransel.composeapp.generated.resources.member_list_template_hint
import ransel.composeapp.generated.resources.member_list_title
import ransel.composeapp.generated.resources.member_list_totals
import ransel.composeapp.generated.resources.packing_name
import ransel.composeapp.generated.resources.packing_quantity_decrease
import ransel.composeapp.generated.resources.packing_quantity_increase
import ransel.composeapp.generated.resources.packing_required

/** Design "MulaiDaftarAnggota": where the members' list starts from. */
@Composable
fun MemberListStartSheet(
    tripTitle: String,
    memberCount: Int,
    mineCount: Int,
    templates: List<PackingTemplateDto>,
    onCopyMine: () -> Unit,
    onTemplate: (PackingTemplateDto) -> Unit,
    onBlank: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AdaptiveSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(Res.string.member_list_start_title), stringResource(Res.string.member_list_start_subtitle, tripTitle, memberCount), onDismiss)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (mineCount > 0) {
                StartOption(Symbols.contentCopy, stringResource(Res.string.member_list_copy_mine), stringResource(Res.string.member_list_copy_mine_hint, mineCount), onCopyMine)
            }
            // my templates first, then the built-in ones
            templates.sortedBy { it.ownerId == null }.forEach { template ->
                StartOption(
                    Symbols.bookmark,
                    template.name,
                    stringResource(if (template.ownerId == null) Res.string.member_list_template_builtin else Res.string.member_list_template_hint, template.items.size),
                ) { onTemplate(template) }
            }
            StartOption(Symbols.editNote, stringResource(Res.string.member_list_blank), stringResource(Res.string.member_list_blank_hint), onBlank)
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.info, size = 18.dp, tint = scheme.onSurfaceVariant)
                Text(stringResource(Res.string.member_list_start_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
            Symbol(Symbols.close, contentDescription = stringResource(Res.string.itinerary_close))
        }
    }
}

@Composable
private fun StartOption(symbol: String, title: String, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(IrisShape.lg).background(scheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(IrisShape.md).background(scheme.surface), contentAlignment = Alignment.Center) {
            Symbol(symbol, tint = scheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
    }
}

/**
 * Design "SusunDaftarAnggota": the list by category, "Wajib" per line, quantity, add a line per category or a new
 * category. "Lanjut" saves and goes on to sending when the list was not sent yet; "Simpan" otherwise. Lines keep their
 * ids across edits so members' ticks survive.
 */
@Composable
fun MemberListEditor(
    tripTitle: String,
    initialName: String?,
    initialItems: List<MemberListItemDto>,
    sendAfter: Boolean,
    busy: Boolean,
    onSave: (name: String, items: List<MemberListItemDto>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val defaultName = stringResource(Res.string.member_list_default_name, tripTitle)
    var name by remember { mutableStateOf(initialName ?: defaultName) }
    val items = remember { mutableStateListOf<MemberListItemDto>().apply { addAll(initialItems.sortedBy { it.sortOrder }) } }
    val categories = remember { mutableStateListOf<PackingCategory>().apply { addAll(initialItems.map { it.category }.distinct()) } }
    var categoryMenu by remember { mutableStateOf(false) }
    val problems = validateMemberList(name, items)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = scheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Symbol(Symbols.close, contentDescription = stringResource(Res.string.itinerary_close))
                    }
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(stringResource(Res.string.member_list_title), style = MaterialTheme.typography.titleLarge)
                        Text(tripTitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IrisTextField(label = stringResource(Res.string.member_list_name), value = name, onValueChange = { name = it.take(60) })
                    Row(
                        Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Symbol(Symbols.info, size = 18.dp, tint = scheme.onSurfaceVariant)
                        Text(stringResource(Res.string.member_list_required_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    categories.forEach { category ->
                        EditorCategory(
                            category = category,
                            lines = items.filter { it.category == category },
                            onChange = { changed -> items[items.indexOfFirst { it.id == changed.id }] = changed },
                            onRemove = { removed -> items.removeAll { it.id == removed.id } },
                            onAdd = { text ->
                                items += MemberListItemDto(newId(), category, text.trim(), 1, false, items.size.toDouble())
                            },
                        )
                    }
                    Box {
                        TextButton(onClick = { categoryMenu = true }, enabled = packingCategoryChoices().any { it !in categories }) {
                            Symbol(Symbols.playlistAdd)
                            Text(stringResource(Res.string.member_list_add_category), modifier = Modifier.padding(start = 8.dp))
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            packingCategoryChoices().filter { it !in categories }.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.label)) },
                                    leadingIcon = { PackingCategoryIcon(option) },
                                    onClick = { categoryMenu = false; categories += option },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(Res.string.member_list_totals, items.size, items.count { it.required }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    LoadingButton(
                        onClick = { onSave(name, items.toList()) },
                        loading = busy,
                        enabled = problems.isEmpty() && items.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(if (sendAfter) Res.string.member_list_next else Res.string.member_list_save)) }
                }
            }
        }
    }
}

@Composable
private fun EditorCategory(
    category: PackingCategory,
    lines: List<MemberListItemDto>,
    onChange: (MemberListItemDto) -> Unit,
    onRemove: (MemberListItemDto) -> Unit,
    onAdd: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    fun commit() {
        if (draft.isNotBlank()) onAdd(draft)
        draft = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PackingCategoryIcon(category)
            Text(stringResource(category.label), style = MaterialTheme.typography.titleMedium)
        }
        lines.forEach { line ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                val required = line.required
                Text(
                    stringResource(if (required) Res.string.packing_required else Res.string.member_list_required_toggle),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (required) scheme.onErrorContainer else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(if (required) Modifier.background(scheme.errorContainer) else Modifier.border(1.dp, scheme.outlineVariant, CircleShape))
                        .clickable(role = Role.Switch) { onChange(line.copy(required = !required)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                SmallStep(Symbols.remove, stringResource(Res.string.packing_quantity_decrease), line.quantity > 1) { onChange(line.copy(quantity = line.quantity - 1)) }
                Text(
                    "${line.quantity}",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(24.dp),
                )
                SmallStep(Symbols.add, stringResource(Res.string.packing_quantity_increase), line.quantity < 99) { onChange(line.copy(quantity = line.quantity + 1)) }
                Box(Modifier.size(40.dp).clickable(role = Role.Button) { onRemove(line) }, contentAlignment = Alignment.Center) {
                    Symbol(Symbols.close, size = 18.dp, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.member_list_remove_item, line.name))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(80) },
                placeholder = { Text(stringResource(Res.string.packing_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = ::commit, enabled = draft.isNotBlank()) {
                Symbol(Symbols.add)
                Text(stringResource(Res.string.member_list_add_item), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun SmallStep(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(32.dp).clip(CircleShape).border(1.dp, if (enabled) scheme.outline else scheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // an icon, not "−"/"+" text: glyphs sit on the font's baseline and look off-centre in the circle
        Symbol(symbol, size = 18.dp, tint = if (enabled) scheme.onSurface else scheme.outlineVariant)
    }
}

/**
 * Design "KirimDaftarAnggota": who gets the list. Everyone ticked = sent to all (members who join later get it too).
 * Members without an account get it on the guest page (the trip link on the web).
 */
@Composable
fun MemberListSendSheet(
    list: MemberListDto,
    members: List<TripMemberDto>,
    busy: Boolean,
    onSend: (memberIds: List<String>, saveAsTemplate: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val chosen = remember { mutableStateListOf<String>().apply { addAll(members.map { it.memberId }) } }
    var saveTemplate by remember { mutableStateOf(false) }
    AdaptiveSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(Res.string.member_list_send_title), stringResource(Res.string.member_list_send_subtitle, list.name, list.items.size), onDismiss)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            members.forEach { member ->
                val hasAccount = member.userId != null
                val on = member.memberId in chosen
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(IrisShape.md)
                        .clickable(role = Role.Checkbox) { if (on) chosen.remove(member.memberId) else chosen.add(member.memberId) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MemberAvatar(member.displayName, member.memberId, isGuest = !hasAccount)
                    Column(Modifier.weight(1f)) {
                        Text(member.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (hasAccount) Res.string.member_list_send_to_personal else Res.string.member_list_send_guest),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(checked = on, onCheckedChange = null)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.member_list_save_template), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(Res.string.member_list_save_template_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Switch(checked = saveTemplate, onCheckedChange = { saveTemplate = it })
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.info, size = 18.dp, tint = scheme.onSurfaceVariant)
                Text(stringResource(Res.string.member_list_send_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            LoadingButton(
                onClick = {
                    // everyone with an account = sent to all, so later members get it as well
                    val all = members.all { it.memberId in chosen }
                    onSend(if (all) emptyList() else chosen.toList(), saveTemplate)
                },
                loading = busy,
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp),
            ) {
                Symbol(Symbols.share)
                Text(stringResource(Res.string.member_list_send_button, chosen.size), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
