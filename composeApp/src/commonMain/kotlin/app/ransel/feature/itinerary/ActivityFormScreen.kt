package app.ransel.feature.itinerary

import app.ransel.content.stringResource
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.shared.model.Category
import app.ransel.ui.category.itineraryCategoryChoices
import app.ransel.ui.category.itineraryLabel
import app.ransel.ui.category.tileColor
import app.ransel.ui.components.CategoryIcon
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.FormScaffold
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.IrisTimePickerDialog
import app.ransel.ui.format.formatDuration
import app.ransel.ui.format.formatTime
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.activity_booking
import ransel.composeapp.generated.resources.activity_category
import ransel.composeapp.generated.resources.activity_day
import ransel.composeapp.generated.resources.activity_day_unscheduled
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.activity_delete
import ransel.composeapp.generated.resources.activity_delete_body
import ransel.composeapp.generated.resources.activity_delete_confirm
import ransel.composeapp.generated.resources.activity_delete_title
import ransel.composeapp.generated.resources.activity_duration
import ransel.composeapp.generated.resources.activity_ends_next_day
import ransel.composeapp.generated.resources.activity_next_day_tag
import ransel.composeapp.generated.resources.activity_form_add_title
import ransel.composeapp.generated.resources.activity_form_edit_title
import ransel.composeapp.generated.resources.activity_notes
import ransel.composeapp.generated.resources.activity_place
import ransel.composeapp.generated.resources.activity_save
import ransel.composeapp.generated.resources.activity_time
import ransel.composeapp.generated.resources.activity_time_clear
import ransel.composeapp.generated.resources.activity_time_end
import ransel.composeapp.generated.resources.activity_time_start
import ransel.composeapp.generated.resources.activity_title
import ransel.composeapp.generated.resources.error_required
import ransel.composeapp.generated.resources.itinerary_day_chip
import ransel.composeapp.generated.resources.trip_close
import ransel.composeapp.generated.resources.trip_optional

private enum class TimeTarget { START, END }

/** Add (itemId == null) or edit an activity; the same form (design "Tambah aktivitas") with a delete button. */
@Composable
fun ActivityFormScreen(
    tripId: String,
    itemId: String?,
    initialDay: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityFormViewModel = koinViewModel(key = "activity-form-$tripId-${itemId ?: "new"}") {
        parametersOf(tripId, itemId, initialDay)
    },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { onClose() }
    }
    var pickingTime by rememberSaveable { mutableStateOf<TimeTarget?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val dates = rememberDateFormatter()

    FormScaffold(
        modifier = modifier,
        title = stringResource(if (state.isEditing) Res.string.activity_form_edit_title else Res.string.activity_form_add_title),
        onBack = onClose,
        navigationSymbol = Symbols.close,
        navigationDescription = stringResource(Res.string.trip_close),
        saveLabel = stringResource(Res.string.activity_save),
        onSave = viewModel::save,
        saveEnabled = !state.loading,
        saving = state.saving,
    ) {
        IrisTextField(
            label = stringResource(Res.string.activity_title),
            value = state.title,
            onValueChange = viewModel::onTitle,
            error = if (state.titleError) stringResource(Res.string.error_required) else null,
        )

        // Category
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.activity_category), style = MaterialTheme.typography.labelLarge)
            itineraryCategoryChoices(state.category).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { category ->
                        val selected = category == state.category
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(IrisShape.lg)
                                .border(
                                    BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                    IrisShape.lg,
                                )
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable(role = Role.RadioButton) { viewModel.onCategory(category) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CategoryIcon(category)
                            Text(
                                stringResource(category.itineraryLabel),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Day (none on a trip without dates: everything is unscheduled until the dates are set)
        if (state.days.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.activity_day), style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.days.forEachIndexed { index, date ->
                    ChoiceChip(
                        selected = state.day == date,
                        onClick = { viewModel.onDay(date) },
                        title = stringResource(Res.string.itinerary_day_chip, index + 1),
                        subtitle = dates.dayMonth(date),
                    )
                }
                ChoiceChip(
                    selected = state.day == null,
                    onClick = { viewModel.onDay(null) },
                    title = stringResource(Res.string.activity_day_unscheduled),
                    subtitle = null,
                )
            }
        }

        // Time (only for a scheduled activity)
        if (state.day != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text(stringResource(Res.string.activity_time), style = MaterialTheme.typography.labelLarge)
                    Text(
                        " " + stringResource(Res.string.trip_optional),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton(
                        label = stringResource(Res.string.activity_time_start),
                        time = state.start,
                        modifier = Modifier.weight(1f),
                        onClick = { pickingTime = TimeTarget.START },
                    )
                    TimeButton(
                        label = stringResource(Res.string.activity_time_end),
                        time = state.end,
                        badge = if (state.endsNextDay) stringResource(Res.string.activity_next_day_tag) else null,
                        modifier = Modifier.weight(1f),
                        onClick = { pickingTime = TimeTarget.END },
                    )
                }
                state.durationMin?.let {
                    val duration = stringResource(Res.string.activity_duration, formatDuration(it))
                    Text(
                        if (state.endsNextDay) duration + " · " + stringResource(Res.string.activity_ends_next_day) else duration,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.start != null || state.end != null) {
                    TextButton(
                        onClick = {
                            viewModel.onStart(null)
                            viewModel.onEnd(null)
                        },
                    ) { Text(stringResource(Res.string.activity_time_clear), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        IrisTextField(
            label = stringResource(Res.string.activity_place),
            value = state.placeName,
            onValueChange = viewModel::onPlace,
            leadingSymbol = Symbols.locationOn,
        )
        IrisTextField(
            label = stringResource(Res.string.activity_notes),
            value = state.notes,
            onValueChange = viewModel::onNotes,
            singleLine = false,
        )
        IrisTextField(
            label = stringResource(Res.string.activity_booking),
            labelSuffix = stringResource(Res.string.trip_optional),
            value = state.bookingUrl,
            onValueChange = viewModel::onBooking,
            leadingSymbol = Symbols.link,
            placeholder = "https://",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        if (state.isEditing) {
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Symbol(Symbols.delete, tint = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(Res.string.activity_delete),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(Res.string.activity_delete_title, state.title),
            body = stringResource(Res.string.activity_delete_body),
            confirmLabel = stringResource(Res.string.activity_delete_confirm),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
            symbol = Symbols.delete,
        )
    }

    pickingTime?.let { target ->
        val initial = (if (target == TimeTarget.START) state.start else state.end)
            ?: state.start?.takeIf { target == TimeTarget.END }
            ?: LocalTime(9, 0)
        IrisTimePickerDialog(
            initial = initial,
            onConfirm = {
                if (target == TimeTarget.START) viewModel.onStart(it) else viewModel.onEnd(it)
                pickingTime = null
            },
            onDismiss = { pickingTime = null },
        )
    }
}

@Composable
private fun ChoiceChip(selected: Boolean, onClick: () -> Unit, title: String, subtitle: String?) {
    Column(
        Modifier
            .heightIn(min = 56.dp)
            .clip(IrisShape.lg)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                IrisShape.lg,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        Text(title, style = MaterialTheme.typography.labelLarge, color = color)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun TimeButton(
    label: String,
    time: LocalTime?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
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
        Symbol(Symbols.schedule, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            time?.let { formatTime(it) } ?: "––",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(IrisShape.xs)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
