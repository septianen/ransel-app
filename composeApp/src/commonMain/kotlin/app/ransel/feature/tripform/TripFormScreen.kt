package app.ransel.feature.tripform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import ransel.composeapp.generated.resources.trip_cover_sheet_title
import ransel.composeapp.generated.resources.trip_cover_change
import ransel.composeapp.generated.resources.trip_cover_add
import app.ransel.ui.components.text
import app.ransel.ui.components.PhotoSourceSheet
import app.ransel.shared.domain.PhotoKind
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
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
import app.ransel.content.stringResource
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
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
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
import ransel.composeapp.generated.resources.trip_form_create_with_poll
import ransel.composeapp.generated.resources.trip_form_dates_required
import ransel.composeapp.generated.resources.trip_form_dates_required_or_undated
import ransel.composeapp.generated.resources.trip_form_pick_dates
import ransel.composeapp.generated.resources.trip_form_poll_failed_body
import ransel.composeapp.generated.resources.trip_form_poll_failed_ok
import ransel.composeapp.generated.resources.trip_form_poll_failed_title
import app.ransel.feature.polls.DatePollSetupSheet
import app.ransel.feature.polls.defaultQuestion
import app.ransel.shared.model.PollKind

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
    val pollQuestion = PollKind.DATE.defaultQuestion()
    LaunchedEffect(pollQuestion) { viewModel.prefillPollQuestion(pollQuestion) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TripFormEvent.Saved -> onSaved(event.tripId)
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
        saveLabel = stringResource(
            when {
                state.isEditing -> Res.string.trip_save_changes
                state.datesUndecided -> Res.string.trip_form_create_with_poll
                else -> Res.string.trips_create
            },
        ),
        onSave = viewModel::save,
        saveEnabled = !state.loading,
        saving = state.saving,
    ) {
        if (state.isEditing) {
            var coverSheet by rememberSaveable { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().height(132.dp)) {
                TripCover(
                    seed = tripId.orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                    radius = 16.dp,
                    coverUrl = state.coverUrl,
                )
                if (state.coverBusy) {
                    Box(
                        Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary) }
                } else if (state.canChangeCover) {
                    // a filled chip on the photo, so it reads on any picture
                    Button(
                        onClick = { coverSheet = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Symbol(Symbols.photoCamera, size = 18.dp)
                        Text(
                            stringResource(if (state.coverUrl == null) Res.string.trip_cover_add else Res.string.trip_cover_change),
                            Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            state.coverProblem?.let { problem ->
                Text(problem.text(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (coverSheet) {
                PhotoSourceSheet(
                    title = stringResource(Res.string.trip_cover_sheet_title),
                    kind = PhotoKind.COVER,
                    onPicked = viewModel::changeCover,
                    onUnreadable = viewModel::coverUnreadable,
                    onRemove = if (state.coverUrl != null) viewModel::removeCover else null,
                    onDismiss = { coverSheet = false },
                )
            }
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

        // "Tanggal belum pasti" (signed in, new trip): a DATE poll instead of the dates
        if (state.canAskDates) UndatedSwitchRow(state.datesUndecided, viewModel::onDatesUndecided)
        if (state.datesUndecided) {
            DatePollDraftCard(state.pollOptions, state.pollClosingDays, state.pollError, onEdit = viewModel::openPollSheet)
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Dates: "Pilih tanggal" until a range is picked (a new trip starts empty and needs one)
            val start = state.startDate
            val end = state.endDate
            val pick = stringResource(Res.string.trip_form_pick_dates)
            Text(stringResource(Res.string.trip_dates), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(
                    symbol = Symbols.flightTakeoff,
                    text = start?.let(dates::weekdayDayMonth) ?: pick,
                    description = stringResource(Res.string.trip_start_date),
                    modifier = Modifier.weight(1f),
                    error = state.datesError,
                    onClick = { showDates = true },
                )
                DateButton(
                    symbol = Symbols.flightLand,
                    text = end?.let(dates::weekdayDayMonth) ?: pick,
                    description = stringResource(Res.string.trip_end_date),
                    modifier = Modifier.weight(1f),
                    error = state.datesError,
                    onClick = { showDates = true },
                )
            }
            if (state.datesError) {
                Text(stringResource(if (state.canAskDates) Res.string.trip_form_dates_required_or_undated else Res.string.trip_form_dates_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (start != null && end != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val days = start.daysUntil(end) + 1
                Symbol(Symbols.calendarMonth, size = 18.dp, filled = true, tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (days <= 1) stringResource(Res.string.trip_duration_one)
                    else stringResource(Res.string.trip_duration, days, days - 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isEditing && state.savedStartDate != null) {
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
            end = state.endDate ?: state.startDate,
            onConfirm = { s, e ->
                viewModel.onDatesChange(s, e)
                showDates = false
            },
            onDismiss = { showDates = false },
            // no day before today; a trip under way may keep the start it already has
            earliest = minOf(app.ransel.util.today(), state.savedStartDate ?: app.ransel.util.today()),
        )
    }
    if (state.pollSheet) {
        DatePollSetupSheet(
            options = state.pollOptions,
            closingDays = state.pollClosingDays,
            currency = state.currency,
            onClosingDays = viewModel::onPollClosingDays,
            onSaveOption = viewModel::savePollOption,
            onRemoveOption = viewModel::removePollOption,
            onDismiss = viewModel::closePollSheet,
        )
    }
    if (state.pollFailedTripId != null) {
        // the trip is saved either way: one button, and closing the dialog goes on too
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::acknowledgePollFailure,
            icon = { Symbol(Symbols.info, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = { Text(stringResource(Res.string.trip_form_poll_failed_title)) },
            text = { Text(stringResource(Res.string.trip_form_poll_failed_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::acknowledgePollFailure) { Text(stringResource(Res.string.trip_form_poll_failed_ok)) }
            },
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
    error: Boolean = false,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .border(
                BorderStroke(if (error) 2.dp else 1.dp, if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
                IrisShape.md,
            )
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
