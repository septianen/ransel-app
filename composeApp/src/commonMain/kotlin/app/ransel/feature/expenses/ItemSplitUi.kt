package app.ransel.feature.expenses

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.currency.currencySymbol
import app.ransel.feature.tripform.ThousandsVisualTransformation
import app.ransel.shared.domain.currencyDecimals
import app.ransel.shared.dto.TripMemberDto
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.format.amountToInput
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.parseAmountMinor
import app.ransel.ui.format.sanitizeAmountInput
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.receipt_add_item
import ransel.composeapp.generated.resources.receipt_delete_item_cd
import ransel.composeapp.generated.resources.receipt_extras_spread
import ransel.composeapp.generated.resources.receipt_item_deleted
import ransel.composeapp.generated.resources.receipt_item_deleted_generic
import ransel.composeapp.generated.resources.receipt_item_name
import ransel.composeapp.generated.resources.receipt_item_name_error
import ransel.composeapp.generated.resources.receipt_per_person
import ransel.composeapp.generated.resources.receipt_per_person_extras

/**
 * Receipt lines (design "HasilStruk"): a card per line. Tap the name or the amount to edit that part; one line is
 * open at a time. Delete is immediate, and [onDeleted] shows the undo snackbar. "+ Tambah item" inserts a line
 * before the tax line. No quantity × price, and no second photo: changing a line does not spend a credit.
 *
 * When [picking], each item line shows who shares it. The tax line has no chips.
 */
@Composable
fun ItemSplitLines(
    split: ItemSplit,
    currency: String,
    members: List<TripMemberDto>,
    picking: Boolean,
    onDeleted: (message: String, undo: () -> Unit) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val deletedNamed = stringResource(Res.string.receipt_item_deleted)
    val deletedGeneric = stringResource(Res.string.receipt_item_deleted_generic)
    val itemIndexes = split.lines.indices.filter { it != split.extrasIndex }
    val taxIndex = split.extrasIndex.takeIf { it >= 0 }
    itemIndexes.forEach { index ->
        LineCard(split, index, currency, members, picking, scope, deletedNamed, deletedGeneric, onDeleted)
    }
    TextButton(
        onClick = { split.addOrFocusBlank() },
        enabled = split.canAddLine(),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Symbol(Symbols.add, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(Res.string.receipt_add_item),
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    if (taxIndex != null) {
        LineCard(split, taxIndex, currency, members, picking = false, scope, deletedNamed, deletedGeneric, onDeleted)
    }
}

@Composable
private fun LineCard(
    split: ItemSplit,
    index: Int,
    currency: String,
    members: List<TripMemberDto>,
    picking: Boolean,
    scope: CoroutineScope,
    deletedNamed: String,
    deletedGeneric: String,
    onDeleted: (message: String, undo: () -> Unit) -> Unit,
) {
    val line = split.lines[index]
    val extras = index == split.extrasIndex
    androidx.compose.runtime.key(split.keyAt(index)) {
        Column(
            Modifier.fillMaxWidth().clip(IrisShape.md).border(1.dp, MaterialTheme.colorScheme.outlineVariant, IrisShape.md).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (split.isEditing(index)) {
                LineEditor(split, index, currency, scope)
            } else {
                LineView(split, index, line.name, line.amountMinor, currency) {
                    val removed = split.delete(index) ?: return@LineView
                    val message = if (removed.name.isBlank()) deletedGeneric else deletedNamed.replace("%1\$s", removed.name)
                    onDeleted(message) { split.restore(removed) }
                }
            }
            if (extras) {
                Text(
                    stringResource(Res.string.receipt_extras_spread),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (picking) {
                SharerChips(split, index, members)
            }
        }
    }
}

@Composable
private fun LineView(
    split: ItemSplit,
    index: Int,
    name: String,
    amountMinor: Long,
    currency: String,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cd = if (name.isBlank()) {
        stringResource(Res.string.receipt_item_deleted_generic)
    } else {
        stringResource(Res.string.receipt_delete_item_cd, name)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable { split.beginEdit(index, ItemSplit.FOCUS_NAME) },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.heightIn(min = 48.dp).clickable { split.beginEdit(index, ItemSplit.FOCUS_AMOUNT) },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                formatMoney(amountMinor, currency),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Symbol(Symbols.delete, contentDescription = cd, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LineEditor(split: ItemSplit, index: Int, currency: String, scope: CoroutineScope) {
    val line = split.lines[index]
    val nameFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    var amountText by remember {
        mutableStateOf(if (line.amountMinor == 0L) "" else amountToInput(line.amountMinor, currency))
    }
    var armed by remember { mutableStateOf(false) }
    val openKey = split.editingKey
    LaunchedEffect(split.focusNonce, split.focusPart) {
        val target = if (split.focusPart == ItemSplit.FOCUS_AMOUNT) amountFocus else nameFocus
        runCatching { target.requestFocus() }
    }
    Column(
        Modifier.onFocusChanged { state ->
            if (state.hasFocus) armed = true
            else if (armed) {
                armed = false
                val key = openKey
                scope.launch { if (split.editingKey == key) split.commitEdit() }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IrisTextField(
                label = stringResource(Res.string.receipt_item_name),
                value = line.name,
                onValueChange = { split.setName(index, it) },
                modifier = Modifier.weight(1f),
                error = if (split.nameError) stringResource(Res.string.receipt_item_name_error) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { runCatching { amountFocus.requestFocus() } }),
                focusRequester = nameFocus,
            )
            IrisTextField(
                label = "",
                value = amountText,
                onValueChange = {
                    val cleaned = sanitizeAmountInput(it, currencyDecimals(currency))
                    amountText = cleaned
                    split.setAmount(index, parseAmountMinor(cleaned, currency) ?: 0L)
                },
                modifier = Modifier.width(140.dp),
                prefix = currencySymbol(currency),
                placeholder = "0",
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (currencyDecimals(currency) == 0) KeyboardType.Number else KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { split.commitEdit() }),
                visualTransformation = ThousandsVisualTransformation,
                focusRequester = amountFocus,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Composable
private fun SharerChips(split: ItemSplit, index: Int, members: List<TripMemberDto>) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        split.peopleOf(index).forEach { id ->
            val on = id in split.sharersOf(index)
            val gone = id in split.locked
            Box(
                Modifier.clip(CircleShape)
                    .alpha(if (gone) 0.5f else 1f)
                    .border(2.dp, if (on) scheme.primary else scheme.outlineVariant, CircleShape)
                    .then(if (gone) Modifier else Modifier.clickable(role = Role.Checkbox) { split.toggle(index, id) })
                    .padding(2.dp),
            ) {
                MemberAvatar(members.firstOrNull { it.memberId == id }?.displayName ?: "?", id, size = 32.dp, isGuest = !on)
            }
        }
    }
}

/** "Bagian tiap orang". A match with the lines is silent: no green or red banner. [name] = how a person is called. */
@Composable
fun ItemSplitSummary(
    shares: List<PersonShare>,
    currency: String,
    members: List<TripMemberDto>,
    name: (String) -> String,
) {
    if (shares.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.receipt_per_person), style = MaterialTheme.typography.labelLarge)
        shares.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberAvatar(members.firstOrNull { it.memberId == p.memberId }?.displayName ?: "?", p.memberId, size = 24.dp)
                Text(name(p.memberId), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatMoney(p.totalMinor, currency),
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    )
                    if (p.extrasMinor > 0) {
                        Text(
                            stringResource(Res.string.receipt_per_person_extras, formatMoney(p.extrasMinor, currency)),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
