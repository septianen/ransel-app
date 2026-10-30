package app.ransel.feature.expenses

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.feature.currency.CurrencySheet
import app.ransel.feature.currency.currencySymbol
import app.ransel.shared.domain.convertPayers
import app.ransel.shared.domain.currencyDecimals
import app.ransel.shared.domain.splitEqually
import app.ransel.shared.dto.ExpensePayerDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.feature.trip.zone
import app.ransel.shared.domain.convertMinor
import app.ransel.shared.domain.isOverseas
import app.ransel.shared.model.Category
import app.ransel.shared.model.FxSource
import app.ransel.ui.format.formatMoney
import org.koin.compose.koinInject
import app.ransel.shared.model.PaymentMethod
import app.ransel.ui.category.expenseLabel
import app.ransel.ui.category.expenseSymbol
import app.ransel.ui.category.label
import app.ransel.ui.category.symbol
import app.ransel.ui.components.AdaptiveFormScreen
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.format.amountToInput
import app.ransel.ui.format.parseAmountMinor
import app.ransel.ui.format.FX_RATE_INPUT_DECIMALS
import app.ransel.ui.format.fxRateFromInput
import app.ransel.ui.format.fxRateToInput
import app.ransel.ui.format.sanitizeAmountInput
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.feature.tripform.ThousandsVisualTransformation
import app.ransel.util.fromPickerMillis
import app.ransel.util.newId
import app.ransel.util.now
import app.ransel.util.toPickerMillis
import app.ransel.util.today
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.expense_rate_daily
import ransel.composeapp.generated.resources.expense_rate_own
import ransel.composeapp.generated.resources.expense_estimate_banner
import ransel.composeapp.generated.resources.expense_estimate_save
import ransel.composeapp.generated.resources.expense_receipt_items
import ransel.composeapp.generated.resources.expense_split_by_items
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.action_ok
import ransel.composeapp.generated.resources.action_save
import ransel.composeapp.generated.resources.currency_sheet_title
import ransel.composeapp.generated.resources.expense_amount
import ransel.composeapp.generated.resources.expense_change_currency
import ransel.composeapp.generated.resources.expense_date
import ransel.composeapp.generated.resources.expense_delete
import ransel.composeapp.generated.resources.expense_error_amount
import ransel.composeapp.generated.resources.expense_error_rate
import ransel.composeapp.generated.resources.expense_note
import ransel.composeapp.generated.resources.expense_paid_with
import ransel.composeapp.generated.resources.expense_rate
import ransel.composeapp.generated.resources.expense_rate_hint
import ransel.composeapp.generated.resources.expense_sheet_edit_title
import ransel.composeapp.generated.resources.expense_sheet_subtitle_day
import ransel.composeapp.generated.resources.activity_category
import ransel.composeapp.generated.resources.expenses_record
import ransel.composeapp.generated.resources.itinerary_close
import ransel.composeapp.generated.resources.itinerary_day_chip

private val CategoryOrder = listOf(
    Category.FOOD, Category.TRANSPORT, Category.LODGING, Category.ATTRACTION, Category.SHOPPING, Category.OTHER,
)

/** Record (existing == null) or edit an expense (design "Catat pengeluaran"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSheet(
    trip: TripDto,
    existing: ExpenseDto?,
    onSave: (ExpenseInput) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** Trip members (empty = solo: no paid-by / split section). */
    members: List<TripMemberDto> = emptyList(),
    myMemberId: String? = null,
    api: RanselApi = koinInject(),
) {
    val tz = remember(trip.timeZone) { trip.zone() }
    val dates = rememberDateFormatter()
    val defaultDate = remember {
        // today inside the trip, else its first day; a trip without dates just takes today
        today().takeIf { it.inTrip(trip) } ?: trip.startDate ?: today()
    }
    val existingLocal = remember(existing) { existing?.spentAt?.toLocalDateTime(tz) }

    // abroad a new expense starts in the local currency (PRD "Overseas trips")
    var currency by remember {
        mutableStateOf(existing?.currency ?: trip.localCurrency?.takeIf { isOverseas(trip.countryCode) } ?: trip.baseCurrency)
    }
    var amountText by remember { mutableStateOf(existing?.let { amountToInput(it.amountMinor, it.currency) }.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: Category.FOOD) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var date by remember { mutableStateOf(existingLocal?.date ?: defaultDate) }
    var method by remember { mutableStateOf(existing?.paymentMethod ?: PaymentMethod.CASH) }
    var rateText by remember { mutableStateOf(existing?.fxRateToBase?.let(::fxRateToInput).orEmpty()) }
    var fxSource by remember { mutableStateOf(existing?.fxSource) }
    var fxDate by remember { mutableStateOf(existing?.fxDate) }
    var dailyRate by remember { mutableStateOf<app.ransel.shared.dto.FxRateDto?>(null) }
    // a typed rate stays; otherwise the day's reference rate fills it (signed in and online only)
    var showRateField by remember { mutableStateOf(existing?.fxSource == FxSource.MANUAL) }
    var showErrors by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var methodMenu by remember { mutableStateOf(false) }

    val shared = members.size > 1
    val split = remember(members) { if (shared) SplitState(members, myMemberId, existing, currency) else null }

    val foreign = !currency.equals(trip.baseCurrency, ignoreCase = true)
    androidx.compose.runtime.LaunchedEffect(currency, date, foreign) {
        if (!foreign || fxSource == FxSource.MANUAL) return@LaunchedEffect
        when (val r = api.fxRate(currency, trip.baseCurrency, date)) {
            is ApiResult.Success -> {
                dailyRate = r.value
                rateText = fxRateToInput(r.value.rate)
                fxSource = FxSource.DAILY
                fxDate = r.value.date
            }
            // offline or signed out: the rate is typed by hand
            is ApiResult.Failure -> if (rateText.isBlank()) showRateField = true
        }
    }
    val amountMinor = parseAmountMinor(amountText, currency)
    // "," starts the decimals, as in the amount field; the field shows the thousands, so "16.250" is never typed
    val storedRate = fxRateFromInput(rateText)
    val rate = storedRate?.toDouble()
    val amountError = showErrors && (amountMinor == null || amountMinor <= 0)
    val rateError = showErrors && foreign && rate == null

    fun save() {
        if (amountMinor == null || amountMinor <= 0 || (foreign && rate == null)) {
            showErrors = true
            return
        }
        if (split != null && !split.isValid(amountMinor, currency)) return
        // receipt lines stay while the amount does; with "Per item" the server derives the splits from them
        val keptItems = existing?.items?.takeIf { it.isNotEmpty() && existing.amountMinor == amountMinor }.orEmpty()
        // Members not loaded (offline) while editing a shared expense: keep who paid and re-split the old people.
        val keptSplits = when {
            split != null || existing == null || existing.splits.isEmpty() -> null
            existing.amountMinor == amountMinor -> existing.splits
            else -> splitEqually(amountMinor, existing.splits.map { it.memberId }, existing.paidByMemberId)
                .map { (id, share) -> ExpenseSplitDto(id, share) }
        }
        // Same for who paid: keep the old payers, scaled when the amount changed.
        val keptPayers = when {
            split != null || existing == null || existing.payers.isEmpty() -> null
            existing.amountMinor == amountMinor -> existing.payers
            else -> convertPayers(existing.payers.map { it.memberId to it.amountMinor }, existing.amountMinor, amountMinor)
                .map { (id, paid) -> ExpensePayerDto(id, paid) }
        }
        val time = existingLocal?.time ?: now().toLocalDateTime(tz).time
        onSave(
            ExpenseInput(
                id = existing?.id ?: newId(),
                amountMinor = amountMinor,
                currency = currency,
                fxRateToBase = if (foreign) storedRate else null,
                fxSource = if (foreign) fxSource ?: FxSource.MANUAL else null,
                fxDate = if (foreign && fxSource == FxSource.DAILY) fxDate else null,
                category = category,
                paymentMethod = method,
                note = note.trim().ifEmpty { null },
                spentAt = LocalDate(date.year, date.month, date.day).let {
                    kotlinx.datetime.LocalDateTime(it, LocalTime(time.hour, time.minute)).toInstant(tz)
                },
                paidByMemberId = keptPayers?.firstOrNull()?.memberId ?: split?.paidBy ?: existing?.paidByMemberId,
                payers = split?.toPayers(amountMinor, currency) ?: keptPayers.orEmpty(),
                splits = split?.toSplits(amountMinor, currency) ?: keptSplits.orEmpty(),
                // saving an estimate records the real payment
                isEstimate = false,
                items = keptItems,
                itemsSplit = existing?.itemsSplit ?: true,
            ),
        )
    }

    // what the form started with, to ask "Buang perubahan?" only when something changed
    val initial = remember { listOf(currency, amountText, category, note, date, method, rateText) to split?.snapshot() }
    val dirty = initial != (listOf(currency, amountText, category, note, date, method, rateText) to split?.snapshot())
    val dayNumber = trip.startDate?.daysUntil(date)?.plus(1) ?: 0

    AdaptiveFormScreen(
        title = stringResource(if (existing == null) Res.string.expenses_record else Res.string.expense_sheet_edit_title),
        subtitle = if (date.inTrip(trip)) {
            stringResource(Res.string.expense_sheet_subtitle_day, trip.title, dayNumber)
        } else {
            trip.title
        },
        onDismiss = onDismiss,
        confirmDiscard = dirty,
        footer = {
            Button(
                onClick = ::save,
                enabled = split == null || split.isValid(amountMinor, currency) || amountMinor == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(if (existing?.isEstimate == true) Res.string.expense_estimate_save else Res.string.action_save),
                    style = MaterialTheme.typography.titleMedium,
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
                        stringResource(Res.string.expense_delete),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) {
        IrisTextField(
            label = stringResource(Res.string.expense_amount),
            value = amountText,
            onValueChange = { amountText = sanitizeAmountInput(it, currencyDecimals(currency)) },
            visualTransformation = ThousandsVisualTransformation,
            prefix = currencySymbol(currency),
            placeholder = "0",
            error = if (amountError) stringResource(Res.string.expense_error_amount) else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (currencyDecimals(currency) == 0) KeyboardType.Number else KeyboardType.Decimal,
            ),
            trailing = {
                Row(
                    Modifier
                        .clip(IrisShape.sm)
                        .clickable(role = Role.Button) { pickCurrency = true }
                        .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(currency, style = MaterialTheme.typography.labelLarge)
                    Symbol(
                        Symbols.expandMore,
                        size = 20.dp,
                        contentDescription = stringResource(Res.string.expense_change_currency),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        if (foreign && rate != null && amountMinor != null) {
            val inBase = convertMinor(amountMinor, currency, trip.baseCurrency, storedRate)
            Text("≈ " + formatMoney(inBase, trip.baseCurrency), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (foreign && !showRateField && fxSource == FxSource.DAILY && rate != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        Res.string.expense_rate_daily, currency, formatMoney(kotlin.math.round(rate).toLong(), trip.baseCurrency),
                        fxDate?.let { dates.dayMonthYear(it) }.orEmpty(),
                    ),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showRateField = true }) { Text(stringResource(Res.string.expense_rate_own)) }
            }
        }
        if (foreign && showRateField) {
            IrisTextField(
                label = stringResource(Res.string.expense_rate, trip.baseCurrency),
                value = rateText,
                onValueChange = {
                    rateText = sanitizeAmountInput(it, FX_RATE_INPUT_DECIMALS)
                    fxSource = FxSource.MANUAL
                    fxDate = null
                },
                visualTransformation = ThousandsVisualTransformation,
                prefix = currencySymbol(trip.baseCurrency),
                placeholder = stringResource(Res.string.expense_rate_hint, currency, trip.baseCurrency),
                error = if (rateError) stringResource(Res.string.expense_error_rate) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.activity_category), style = MaterialTheme.typography.labelLarge)
            CategoryOrder.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        val selected = option == category
                        Row(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
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
                                    CircleShape,
                                )
                                .clickable(role = Role.RadioButton) { category = option }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        ) {
                            Symbol(
                                option.expenseSymbol, size = 18.dp, filled = selected,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(option.expenseLabel),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // date and payment method come before the note: they are picked, the note is typed last
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.expense_date), style = MaterialTheme.typography.labelLarge)
                PickerButton(Symbols.calendarMonth, dates.weekdayDayMonth(date), onClick = { pickDate = true })
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.expense_paid_with), style = MaterialTheme.typography.labelLarge)
                Box {
                    PickerButton(
                        method.symbol,
                        stringResource(method.label),
                        trailingChevron = true,
                        onClick = { methodMenu = true },
                    )
                    DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                        PaymentMethod.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.label)) },
                                leadingIcon = { Symbol(option.symbol) },
                                onClick = {
                                    method = option
                                    methodMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        IrisTextField(
            label = stringResource(Res.string.expense_note),
            value = note,
            onValueChange = { note = it },
        )

        if (existing?.isEstimate == true) {
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.md).background(MaterialTheme.colorScheme.tertiaryContainer).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.info, size = 18.dp, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    stringResource(Res.string.expense_estimate_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        // the receipt's lines, also when the split was "Bagi rata" or "Atur nominal" (owner, 30 Oct 2026)
        val receiptLines = existing?.items?.takeIf { it.isNotEmpty() && existing.amountMinor == amountMinor }.orEmpty()
        if (receiptLines.isNotEmpty()) {
            ReceiptLines(receiptLines, existing!!.currency, split?.members.orEmpty())
        }
        val itemized = receiptLines.isNotEmpty() && existing!!.itemsSplit
        if (split != null && itemized) {
            Text(
                stringResource(Res.string.expense_split_by_items, existing!!.items.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (split != null) {
            SplitSection(split, amountMinor, currency, myMemberId)
        }
    }

    if (pickCurrency) {
        CurrencySheet(
            title = stringResource(Res.string.currency_sheet_title),
            subtitle = null,
            selected = currency,
            onSelect = {
                currency = it
                amountText = sanitizeAmountInput(amountText, currencyDecimals(it))
                pickCurrency = false
            },
            onDismiss = { pickCurrency = false },
        )
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { date = it.fromPickerMillis() }
                        pickDate = false
                    },
                ) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

/** "Item dari struk": each line and its amount; with a "Per item" split, who shares it. */
@Composable
private fun ReceiptLines(items: List<ExpenseItemDto>, currency: String, members: List<TripMemberDto>) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.expense_receipt_items), style = MaterialTheme.typography.labelLarge)
        Column(
            Modifier.fillMaxWidth().clip(IrisShape.md).border(1.dp, scheme.outlineVariant, IrisShape.md).padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            items.forEach { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatMoney(item.amountMinor, currency), style = MaterialTheme.typography.titleSmall)
                    }
                    if (item.memberIds.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item.memberIds.forEach { id ->
                                app.ransel.ui.components.MemberAvatar(members.firstOrNull { it.memberId == id }?.displayName ?: "?", id, size = 20.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerButton(
    symbol: String,
    text: String,
    onClick: () -> Unit,
    trailingChevron: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), IrisShape.md)
            .clip(IrisShape.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(symbol, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        if (trailingChevron) Symbol(Symbols.expandMore, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** True when the day is one of the trip's days (never for a trip without dates). */
private fun LocalDate.inTrip(trip: TripDto): Boolean {
    val start = trip.startDate ?: return false
    val end = trip.endDate ?: return false
    return this in start..end
}
