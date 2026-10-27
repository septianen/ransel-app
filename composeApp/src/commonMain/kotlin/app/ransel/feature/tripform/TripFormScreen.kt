package app.ransel.feature.tripform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ransel.feature.currency.CurrencySheet
import app.ransel.feature.currency.currencyOption
import app.ransel.feature.currency.currencySymbol
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.FormScaffold
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.TripCover
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.flow.collect
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.trip_template_use
import ransel.composeapp.generated.resources.trip_template_none
import ransel.composeapp.generated.resources.trip_template_hint
import ransel.composeapp.generated.resources.trip_template_delete
import ransel.composeapp.generated.resources.destination_hint
import app.ransel.feature.destination.DestinationField
import app.ransel.shared.domain.isOverseas
import ransel.composeapp.generated.resources.currency_locked_hint
import ransel.composeapp.generated.resources.currency_sheet_title
import ransel.composeapp.generated.resources.delete_trip_activities
import ransel.composeapp.generated.resources.delete_trip_body
import ransel.composeapp.generated.resources.delete_trip_confirm
import ransel.composeapp.generated.resources.delete_trip_expenses
import ransel.composeapp.generated.resources.delete_trip_items
import ransel.composeapp.generated.resources.delete_trip_itinerary
import ransel.composeapp.generated.resources.delete_trip_packing
import ransel.composeapp.generated.resources.delete_trip_records
import ransel.composeapp.generated.resources.delete_trip_title
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.error_required
import ransel.composeapp.generated.resources.trip_budget
import ransel.composeapp.generated.resources.trip_close
import ransel.composeapp.generated.resources.trip_cover_description
import ransel.composeapp.generated.resources.trip_currency
import ransel.composeapp.generated.resources.trip_currency_hint
import ransel.composeapp.generated.resources.trip_dates
import ransel.composeapp.generated.resources.trip_delete
import ransel.composeapp.generated.resources.trip_destination
import ransel.composeapp.generated.resources.trip_duration
import ransel.composeapp.generated.resources.trip_duration_one
import ransel.composeapp.generated.resources.trip_end_date
import ransel.composeapp.generated.resources.trip_form_create_title
import ransel.composeapp.generated.resources.trip_form_edit_title
import ransel.composeapp.generated.resources.trip_name
import ransel.composeapp.generated.resources.trip_optional
import ransel.composeapp.generated.resources.trip_save_changes
import ransel.composeapp.generated.resources.trip_shorten_hint
import ransel.composeapp.generated.resources.trip_start_date
import ransel.composeapp.generated.resources.trips_create

/** Create trip (`tripId == null`) and Edit trip. One screen, like the two designs share one layout. */
@Composable
fun TripFormScreen(
    tripId: String?,
    onClose: () -> Unit,
    onSaved: (tripId: String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripFormViewModel = koinViewModel(key = "trip-form-${tripId ?: "new"}") { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()
    // "Pakai template trip" (new trips only): applied once the trip exists
    val templates: TripTemplatesViewModel? = if (tripId == null) koinViewModel(key = "trip-templates") else null
    val templateState = templates?.state?.collectAsState()?.value
    var templateSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TripFormEvent.Saved -> if (templates != null) {
                    val s = viewModel.state.value
                    templates.applyTo(event.tripId, s.startDate, s.endDate) { onSaved(event.tripId) }
                } else {
                    onSaved(event.tripId)
                }
                TripFormEvent.Deleted -> onDeleted()
            }
        }
    }
    var showDates by rememberSaveable { mutableStateOf(false) }
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    val dates = rememberDateFormatter()
    val symbol = currencySymbol(state.currency)

    FormScaffold(
        modifier = modifier,
        title = stringResource(if (state.isEditing) Res.string.trip_form_edit_title else Res.string.trip_form_create_title),
        onBack = onClose,
        navigationSymbol = Symbols.close,
        navigationDescription = stringResource(Res.string.trip_close),
        saveLabel = stringResource(if (state.isEditing) Res.string.trip_save_changes else Res.string.trips_create),
        onSave = viewModel::save,
        saveEnabled = !state.loading,
        saving = state.saving,
    ) {
        if (state.isEditing) {
            TripCover(
                seed = tripId.orEmpty(),
                modifier = Modifier.fillMaxWidth().height(132.dp),
                radius = 16.dp,
            )
        }
        IrisTextField(
            label = stringResource(Res.string.trip_name),
            value = state.title,
            onValueChange = viewModel::onTitleChange,
            error = if (state.titleError) stringResource(Res.string.error_required) else null,
        )
        DestinationField(
            label = stringResource(Res.string.trip_destination),
            value = state.destination,
            onPicked = viewModel::onDestinationPicked,
            picked = state.destinationPlace != null,
            placeholder = stringResource(Res.string.destination_hint),
        )
        if (isOverseas(state.countryCode)) {
            app.ransel.feature.trip.OverseasCard(state.currency, state.localCurrency, state.timeZone)
        }
        if (templates != null && templateState != null && templateState.templates.isNotEmpty()) {
            TemplateField(templateState.chosen) { templateSheet = true }
        }

        // Dates
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.trip_dates), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(
                    symbol = Symbols.flightTakeoff,
                    text = dates.weekdayDayMonth(state.startDate),
                    description = stringResource(Res.string.trip_start_date),
                    modifier = Modifier.weight(1f),
                    onClick = { showDates = true },
                )
                DateButton(
                    symbol = Symbols.flightLand,
                    text = dates.weekdayDayMonth(state.endDate),
                    description = stringResource(Res.string.trip_end_date),
                    modifier = Modifier.weight(1f),
                    onClick = { showDates = true },
                )
            }
            val days = state.startDate.daysUntil(state.endDate) + 1
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Symbol(Symbols.calendarMonth, size = 18.dp, filled = true, tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (days <= 1) stringResource(Res.string.trip_duration_one)
                    else stringResource(Res.string.trip_duration, days, days - 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isEditing) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(IrisShape.md)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symbol(Symbols.info, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(Res.string.trip_shorten_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Currency
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.trip_currency), style = MaterialTheme.typography.labelLarge)
            val option = currencyOption(state.currency)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), IrisShape.md)
                    .clip(IrisShape.md)
                    .clickable(enabled = !state.currencyLocked, role = Role.Button) { showCurrency = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(option.code, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(option.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!state.currencyLocked) Symbol(Symbols.expandMore, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (state.currencyLocked) stringResource(Res.string.currency_locked_hint)
                else stringResource(Res.string.trip_currency_hint, state.currency),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IrisTextField(
            label = stringResource(Res.string.trip_budget),
            labelSuffix = stringResource(Res.string.trip_optional),
            value = state.budgetDigits,
            onValueChange = viewModel::onBudgetChange,
            prefix = symbol,
            placeholder = "0",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = ThousandsVisualTransformation,
        )

        if (state.isEditing) {
            TextButton(
                onClick = viewModel::requestDelete,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Symbol(Symbols.delete, tint = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(Res.string.trip_delete),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    if (showDates) {
        TripDateRangePicker(
            start = state.startDate,
            end = state.endDate,
            onConfirm = { s, e ->
                viewModel.onDatesChange(s, e)
                showDates = false
            },
            onDismiss = { showDates = false },
            // no day before today; a trip under way may keep the start it already has
            earliest = minOf(app.ransel.util.today(), state.savedStartDate ?: app.ransel.util.today()),
        )
    }
    if (showCurrency) {
        CurrencySheet(
            title = stringResource(Res.string.currency_sheet_title),
            subtitle = null,
            selected = state.currency,
            onSelect = {
                viewModel.onCurrencyChange(it)
                showCurrency = false
            },
            onDismiss = { showCurrency = false },
        )
    }
    if (templateSheet && templates != null && templateState != null) {
        TemplateSheet(templateState.templates, templateState.chosen, onChoose = templates::choose, onDelete = templates::delete, onDismiss = { templateSheet = false })
    }
    state.deleteSummary?.let { summary ->
        ConfirmDialog(
            title = stringResource(Res.string.delete_trip_title, state.title),
            body = stringResource(Res.string.delete_trip_body),
            confirmLabel = stringResource(Res.string.delete_trip_confirm),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
            destructive = true,
            symbol = Symbols.delete,
            extra = {
                val activities = summary.activityCount + summary.unscheduledCount
                DeleteRow(Symbols.map, stringResource(Res.string.delete_trip_itinerary), stringResource(Res.string.delete_trip_activities, activities))
                DeleteRow(Symbols.checklist, stringResource(Res.string.delete_trip_packing), stringResource(Res.string.delete_trip_items, summary.packingTotal))
                DeleteRow(Symbols.receiptLong, stringResource(Res.string.delete_trip_expenses), stringResource(Res.string.delete_trip_records, summary.expenseCount))
            },
        )
    }
}

@Composable
private fun DateButton(
    symbol: String,
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), IrisShape.md)
            .clip(IrisShape.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(symbol, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = description)
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun DeleteRow(symbol: String, label: String, count: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(symbol, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(count, style = MaterialTheme.typography.labelLarge)
    }
}

/** "Pakai template trip": the chosen template, or "Tanpa template". */
@Composable
private fun TemplateField(chosen: app.ransel.shared.dto.TripTemplateDto?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.trip_template_use), style = MaterialTheme.typography.labelLarge)
        androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            app.ransel.ui.symbols.Symbol(Symbols.bookmark)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(chosen?.name ?: stringResource(Res.string.trip_template_none))
                if (chosen != null) {
                    Text(
                        stringResource(Res.string.trip_template_hint, chosen.dayCount, chosen.items.size, chosen.packing.size),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
            }
            app.ransel.ui.symbols.Symbol(Symbols.arrowDropDown)
        }
    }
}

/** The user's trip templates to start from, with "Tanpa template" and a delete per template. */
@Composable
private fun TemplateSheet(
    templates: List<app.ransel.shared.dto.TripTemplateDto>,
    chosen: app.ransel.shared.dto.TripTemplateDto?,
    onChoose: (app.ransel.shared.dto.TripTemplateDto?) -> Unit,
    onDelete: (app.ransel.shared.dto.TripTemplateDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    app.ransel.ui.components.AdaptiveSheet(onDismiss = onDismiss) {
        Text(stringResource(Res.string.trip_template_use), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
        val rows = listOf<app.ransel.shared.dto.TripTemplateDto?>(null) + templates
        rows.forEach { template ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onChoose(template); onDismiss() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(selected = template?.id == chosen?.id, onClick = null)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(template?.name ?: stringResource(Res.string.trip_template_none), style = MaterialTheme.typography.bodyLarge)
                    if (template != null) {
                        Text(
                            stringResource(Res.string.trip_template_hint, template.dayCount, template.items.size, template.packing.size),
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        )
                    }
                }
                if (template != null) {
                    Box(Modifier.size(48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { onDelete(template) }, contentAlignment = Alignment.Center) {
                        app.ransel.ui.symbols.Symbol(Symbols.delete, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.trip_template_delete))
                    }
                }
            }
        }
    }
}
