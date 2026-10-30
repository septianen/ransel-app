package app.ransel.feature.expenses.receipt

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ransel.feature.currency.currencySymbol
import app.ransel.feature.expenses.SheetHeader
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Category
import app.ransel.ui.category.expenseLabel
import app.ransel.ui.category.expenseSymbol
import app.ransel.ui.components.AdaptiveFormScreen
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.feature.expenses.ItemSplitLines
import app.ransel.feature.expenses.ItemSplitSummary
import app.ransel.feature.expenses.SplitSection
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.rememberDateFormatter
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.action_ok
import ransel.composeapp.generated.resources.receipt_change_date
import ransel.composeapp.generated.resources.receipt_extras_included
import app.ransel.util.toPickerMillis
import app.ransel.util.fromPickerMillis
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.newId
import app.ransel.util.now
import app.ransel.util.today
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_save
import ransel.composeapp.generated.resources.expense_error_rate
import ransel.composeapp.generated.resources.expense_rate
import ransel.composeapp.generated.resources.expense_rate_hint
import ransel.composeapp.generated.resources.receipt_camera
import ransel.composeapp.generated.resources.receipt_choose_manual
import ransel.composeapp.generated.resources.receipt_choose_manual_hint
import ransel.composeapp.generated.resources.receipt_choose_photo
import ransel.composeapp.generated.resources.receipt_choose_photo_hint
import ransel.composeapp.generated.resources.receipt_choose_title
import ransel.composeapp.generated.resources.receipt_cost
import ransel.composeapp.generated.resources.receipt_error_credits
import ransel.composeapp.generated.resources.receipt_error_generic
import ransel.composeapp.generated.resources.receipt_error_large
import ransel.composeapp.generated.resources.receipt_error_unreadable
import ransel.composeapp.generated.resources.receipt_error_verify
import ransel.composeapp.generated.resources.receipt_extras
import ransel.composeapp.generated.resources.receipt_gallery
import ransel.composeapp.generated.resources.receipt_manual_instead
import ransel.composeapp.generated.resources.receipt_per_item
import ransel.composeapp.generated.resources.receipt_place
import ransel.composeapp.generated.resources.receipt_reading
import ransel.composeapp.generated.resources.receipt_result_hint
import ransel.composeapp.generated.resources.receipt_result_title
import ransel.composeapp.generated.resources.receipt_retry
import ransel.composeapp.generated.resources.receipt_tip
import ransel.composeapp.generated.resources.receipt_title
import ransel.composeapp.generated.resources.shared_you

/** Design "TambahPengeluaranPilih": the + button on Pengeluaran asks how. */
@Composable
fun AddExpenseChooserSheet(tripTitle: String, onReceipt: () -> Unit, onManual: () -> Unit, onDismiss: () -> Unit) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetHeader(stringResource(Res.string.receipt_choose_title), tripTitle, onDismiss)
            ChooserRow(Symbols.photoCamera, stringResource(Res.string.receipt_choose_photo), stringResource(Res.string.receipt_choose_photo_hint), ai = true, onClick = onReceipt)
            ChooserRow(Symbols.editNote, stringResource(Res.string.receipt_choose_manual), stringResource(Res.string.receipt_choose_manual_hint), ai = false, onClick = onManual)
        }
    }
}

@Composable
private fun ChooserRow(symbol: String, title: String, hint: String, ai: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(IrisShape.md).background(if (ai) scheme.tertiaryContainer else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Symbol(symbol, filled = ai, tint = if (ai) scheme.onTertiaryContainer else scheme.onSurface) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (ai) Symbol(Symbols.autoAwesome, size = 16.dp, filled = true, tint = scheme.tertiary)
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
    }
}

/**
 * "Foto struk" → "Cek hasil struk": take or pick a photo, let the server read it (one credit), check the result and
 * save it as a normal expense, split equally or per item.
 */
@Composable
fun ReceiptFlow(
    trip: TripDto,
    members: List<TripMemberDto>,
    myMemberId: String?,
    onSave: (app.ransel.shared.dto.ExpenseInput) -> Unit,
    onDismiss: () -> Unit,
    onManual: () -> Unit = onDismiss,
    viewModel: ReceiptViewModel = koinViewModel(key = "receipt-${trip.id}-${remember { newId() }}") { parametersOf(trip) },
) {
    val step by viewModel.step.collectAsState()
    val wallet by viewModel.wallet.collectAsState()
    val scope = rememberCoroutineScope()
    val onFile: (PlatformFile?) -> Unit = { file ->
        if (file != null) {
            scope.launch {
                runCatching { prepareReceiptImage(file) }
                    .onSuccess(viewModel::read)
                    .onFailure { viewModel.failed(ErrorCode.VALIDATION_FAILED) }
            }
        }
    }
    val camera = rememberReceiptCamera(onFile)
    val gallery = rememberFilePickerLauncher(type = FileKitType.Image, onResult = onFile)

    // the result is a long form: its own screen (phones) or a dialog (wide web), not a sheet that closes while scrolling
    (step as? ReceiptStep.Draft)?.let { s ->
        val extras = stringResource(Res.string.receipt_extras)
        val form = remember(s.draft) { ReceiptFormState(s.draft, members, myMemberId, extras) }
        ReceiptResult(form, trip, members, myMemberId, onSave, onDismiss)
        return
    }
    AdaptiveSheet(onDismiss = onDismiss) {
        when (val s = step) {
            ReceiptStep.Pick, ReceiptStep.Reading -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SheetHeader(stringResource(Res.string.receipt_title), trip.title, onDismiss)
                if (s == ReceiptStep.Reading) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(Res.string.receipt_reading), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(stringResource(Res.string.receipt_tip), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    wallet?.let { w ->
                        Text(
                            stringResource(Res.string.receipt_cost, w.costs.receipt, w.available),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (camera != null) {
                        Button(onClick = camera, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Symbol(Symbols.photoCamera, size = 20.dp)
                            Text(stringResource(Res.string.receipt_camera), Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedButton(onClick = { gallery.launch() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Symbol(Symbols.addPhotoAlternate, size = 20.dp)
                        Text(stringResource(Res.string.receipt_gallery), Modifier.padding(start = 8.dp))
                    }
                }
            }
            is ReceiptStep.Failed -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SheetHeader(stringResource(Res.string.receipt_title), trip.title, onDismiss)
                Text(
                    when (s.code) {
                        ErrorCode.RECEIPT_UNREADABLE -> stringResource(Res.string.receipt_error_unreadable)
                        ErrorCode.INSUFFICIENT_CREDITS -> stringResource(Res.string.receipt_error_credits)
                        ErrorCode.FILE_TOO_LARGE -> stringResource(Res.string.receipt_error_large)
                        ErrorCode.EMAIL_NOT_VERIFIED -> stringResource(Res.string.receipt_error_verify)
                        else -> stringResource(Res.string.receipt_error_generic, s.code)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.receipt_retry))
                }
                OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.receipt_manual_instead))
                }
            }
            is ReceiptStep.Draft -> Unit
        }
    }
}

/** Design "HasilStruk". */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptResult(
    form: ReceiptFormState,
    trip: TripDto,
    members: List<TripMemberDto>,
    myMemberId: String?,
    onSave: (app.ransel.shared.dto.ExpenseInput) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    val currency = form.draft.currency
    val foreign = !currency.equals(trip.baseCurrency, ignoreCase = true)
    var rateText by remember { mutableStateOf("") }
    val rate = rateText.trim().replace(',', '.').toDoubleOrNull()
    val shared = form.memberIds.size > 1
    val you = stringResource(Res.string.shared_you)
    fun name(id: String) = members.firstOrNull { it.memberId == id }?.displayName?.let { if (id == myMemberId) "$it ($you)" else it } ?: "?"
    var pickDate by remember { mutableStateOf(false) }
    val split = form.split

    AdaptiveFormScreen(
        title = stringResource(Res.string.receipt_result_title),
        subtitle = trip.title,
        onDismiss = onDismiss,
        // the receipt was read (and paid for with a credit): leaving asks first
        confirmDiscard = true,
        footer = {
            Button(
                onClick = {
                    val tz = TimeZone.currentSystemDefault()
                    val time = now().toLocalDateTime(tz).time
                    val spentAt = LocalDateTime(form.date ?: today(), time).toInstant(tz)
                    onSave(form.toInput(newId(), spentAt, if (foreign) rateText.trim().replace(',', '.') else null))
                },
                enabled = form.canSave && (!foreign || (rate != null && rate > 0.0)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.action_save), style = MaterialTheme.typography.titleMedium) }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.tertiaryContainer).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(Symbols.autoAwesome, size = 18.dp, filled = true, tint = scheme.onTertiaryContainer)
            Text(stringResource(Res.string.receipt_result_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer)
        }
        IrisTextField(label = stringResource(Res.string.receipt_place), value = form.place, onValueChange = { form.place = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            // the day the AI read; tap to fix it. The year shows when it is not the trip's, so a misread stands out.
            val day = form.date ?: today()
            Row(
                Modifier.weight(1f).clip(IrisShape.md)
                    .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.receipt_change_date)) { pickDate = true }
                    .heightIn(min = 48.dp).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Symbol(Symbols.calendarMonth, size = 18.dp, tint = scheme.primary)
                Text(
                    dates.weekdayDayMonth(day) + if (day.year != (trip.startDate ?: today()).year) " ${day.year}" else "",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.primary,
                )
            }
            Text(formatMoney(form.total, currency), style = MaterialTheme.typography.headlineSmall)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Category.FOOD, Category.TRANSPORT, Category.LODGING, Category.ATTRACTION, Category.SHOPPING, Category.OTHER).forEach { c ->
                Choice(stringResource(c.expenseLabel), selected = form.category == c, symbol = c.expenseSymbol) { form.category = c }
            }
        }
        if (foreign) {
            IrisTextField(
                label = stringResource(Res.string.expense_rate, trip.baseCurrency),
                value = rateText,
                onValueChange = { rateText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                prefix = currencySymbol(trip.baseCurrency),
                placeholder = stringResource(Res.string.expense_rate_hint, currency, trip.baseCurrency),
                error = if (rateText.isNotEmpty() && (rate == null || rate <= 0.0)) stringResource(Res.string.expense_error_rate) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        if (split != null) {
            // the same "Dibayar oleh" / "Dibagi ke" as the manual form, plus "Per item" for a receipt with lines
            SplitSection(
                split, form.total, currency, myMemberId,
                perItemLabel = if (form.canSplitPerItem) stringResource(Res.string.receipt_per_item) else null,
                perItem = form.perItem,
                onPerItemChange = { form.perItem = it },
            )
        }
        ItemSplitLines(form.itemSplit, currency, members, picking = shared && form.perItem)
        if (form.includedExtrasMinor > 0) {
            Text(
                stringResource(Res.string.receipt_extras_included, formatMoney(form.includedExtrasMinor, currency)),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
        if (shared && form.perItem) {
            ItemSplitSummary(form.perPerson(), form.difference, form.total, currency, members, ::name)
        }
    }
    if (pickDate) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = (form.date ?: today()).toPickerMillis())
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { form.date = it.fromPickerMillis() }
                        pickDate = false
                    },
                ) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pickDate = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, symbol: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Symbol(Symbols.check, size = 18.dp, tint = scheme.onPrimaryContainer)
        else if (symbol != null) Symbol(symbol, size = 18.dp, tint = scheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1)
    }
}
