package app.ransel.feature.polls

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.shared.domain.PollAction
import app.ransel.shared.domain.actionFor
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.model.PollKind
import app.ransel.ui.components.FormScaffold
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.IrisTimePickerDialog
import app.ransel.ui.format.formatTime
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import app.ransel.util.fromPickerMillis
import app.ransel.util.toPickerMillis
import app.ransel.util.today
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.action_ok
import ransel.composeapp.generated.resources.poll_add_option
import ransel.composeapp.generated.resources.poll_anonymous
import ransel.composeapp.generated.resources.poll_anonymous_budget
import ransel.composeapp.generated.resources.poll_anonymous_hint
import ransel.composeapp.generated.resources.poll_apply_dates
import ransel.composeapp.generated.resources.poll_apply_destination
import ransel.composeapp.generated.resources.poll_apply_food
import ransel.composeapp.generated.resources.poll_apply_place
import ransel.composeapp.generated.resources.poll_apply_winner
import ransel.composeapp.generated.resources.poll_closes_1d
import ransel.composeapp.generated.resources.poll_closes_2d
import ransel.composeapp.generated.resources.poll_closes_3d
import ransel.composeapp.generated.resources.poll_closes_7d
import ransel.composeapp.generated.resources.poll_closes_label
import ransel.composeapp.generated.resources.poll_create_action
import ransel.composeapp.generated.resources.poll_create_error
import ransel.composeapp.generated.resources.poll_create_title
import ransel.composeapp.generated.resources.poll_food_day
import ransel.composeapp.generated.resources.poll_food_no_day
import ransel.composeapp.generated.resources.poll_food_time
import ransel.composeapp.generated.resources.poll_food_when
import ransel.composeapp.generated.resources.poll_multi
import ransel.composeapp.generated.resources.poll_option_delete
import ransel.composeapp.generated.resources.poll_option_edit
import ransel.composeapp.generated.resources.poll_options_empty
import ransel.composeapp.generated.resources.poll_options_title
import ransel.composeapp.generated.resources.poll_question_error
import ransel.composeapp.generated.resources.poll_question_label

/** The form of a new poll (opened from the Group tab). Closes by itself once the poll exists and opens it. */
@Composable
fun CreatePollScreen(
    tripId: String,
    kind: PollKind,
    onBack: () -> Unit,
    onCreated: (pollId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreatePollViewModel = koinViewModel(key = "create-poll-$tripId-${kind.name}") { parametersOf(tripId, kind) },
) {
    val state by viewModel.state.collectAsState()
    val defaultQuestion = kind.defaultQuestion()
    LaunchedEffect(defaultQuestion) { viewModel.prefillQuestion(defaultQuestion) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { if (it is CreatePollEvent.Created) onCreated(it.pollId) }
    }
    CreatePollContent(
        state = state,
        onBack = onBack,
        onQuestion = viewModel::onQuestion,
        onClosingDays = viewModel::onClosingDays,
        onMulti = viewModel::onMulti,
        onAnonymous = viewModel::onAnonymous,
        onApplyWinner = viewModel::onApplyWinner,
        onFoodDay = viewModel::onFoodDay,
        onFoodTime = viewModel::onFoodTime,
        onAddOption = { viewModel.openOptionSheet() },
        onEditOption = { viewModel.openOptionSheet(it) },
        onRemoveOption = viewModel::removeOption,
        onCreate = viewModel::create,
        modifier = modifier,
    )
    if (state.optionSheet) {
        PollOptionSheet(
            kind = kind,
            currency = state.currency,
            initial = state.editingId?.let { id -> state.options.firstOrNull { it.id == id }?.asOptionDto() },
            tripStart = state.tripStart,
            tripEnd = state.tripEnd,
            onSave = viewModel::saveOption,
            onDismiss = viewModel::closeOptionSheet,
        )
    }
}

private fun PollOptionInput.asOptionDto() = app.ransel.shared.dto.PollOptionDto(
    id = id, label = label, startDate = startDate, endDate = endDate, placeId = placeId, url = url, imageUrl = imageUrl,
    priceMinor = priceMinor, priceUnit = priceUnit, minMinor = minMinor, maxMinor = maxMinor,
)

@Composable
fun CreatePollContent(
    state: CreatePollUiState,
    onBack: () -> Unit,
    onQuestion: (String) -> Unit,
    onClosingDays: (Int) -> Unit,
    onMulti: (Boolean) -> Unit,
    onAnonymous: (Boolean) -> Unit,
    onApplyWinner: (Boolean) -> Unit,
    onFoodDay: (LocalDate?) -> Unit,
    onFoodTime: (LocalTime) -> Unit,
    onAddOption: () -> Unit,
    onEditOption: (String) -> Unit,
    onRemoveOption: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormScaffold(
        title = stringResource(Res.string.poll_create_title),
        onBack = onBack,
        saveLabel = stringResource(Res.string.poll_create_action),
        onSave = onCreate,
        saving = state.saving,
        modifier = modifier,
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            KindHeader(state.kind)
            IrisTextField(
                label = stringResource(Res.string.poll_question_label),
                value = state.question,
                onValueChange = onQuestion,
                error = if (state.questionWrong) stringResource(Res.string.poll_question_error) else null,
            )
            ClosingChoice(state.closingDays, onClosingDays)
            if (state.kind == PollKind.FOOD) FoodWhen(state.foodDay, state.foodTime, state.tripStart, onFoodDay, onFoodTime)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(stringResource(Res.string.poll_multi), null, state.multiChoice, true, onMulti)
                SwitchRow(
                    title = stringResource(Res.string.poll_anonymous),
                    hint = stringResource(if (state.anonymousLocked) Res.string.poll_anonymous_budget else Res.string.poll_anonymous_hint),
                    checked = state.anonymous,
                    enabled = !state.anonymousLocked,
                    onChange = onAnonymous,
                )
                if (state.hasAction) {
                    SwitchRow(stringResource(Res.string.poll_apply_winner), applyHint(state.kind), state.applyWinner, true, onApplyWinner)
                }
            }
            OptionsSection(state, onAddOption, onEditOption, onRemoveOption)
            if (state.failed) {
                Text(stringResource(Res.string.poll_create_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun applyHint(kind: PollKind): String = stringResource(
    when (actionFor(kind)) {
        PollAction.SET_TRIP_DATES -> Res.string.poll_apply_dates
        PollAction.SET_DESTINATION -> Res.string.poll_apply_destination
        PollAction.ADD_UNSCHEDULED_PLACE -> Res.string.poll_apply_place
        PollAction.ADD_ACTIVITY -> Res.string.poll_apply_food
        PollAction.NONE -> Res.string.poll_apply_winner
    },
)

@Composable
private fun KindHeader(kind: PollKind) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(IrisShape.md).background(kind.tileColor()), contentAlignment = Alignment.Center) {
            Symbol(kind.symbol, tint = IrisTheme.accents.onAccent)
        }
        Column {
            Text(kind.label(), style = MaterialTheme.typography.titleMedium)
            Text(kind.hint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClosingChoice(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.poll_closes_label), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CLOSING_CHOICES_DAYS.forEach { days ->
                FilterChip(
                    selected = days == selected,
                    onClick = { onSelect(days) },
                    label = {
                        Text(
                            stringResource(
                                when (days) {
                                    1 -> Res.string.poll_closes_1d
                                    2 -> Res.string.poll_closes_2d
                                    3 -> Res.string.poll_closes_3d
                                    else -> Res.string.poll_closes_7d
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodWhen(day: LocalDate?, time: LocalTime?, tripStart: LocalDate?, onDay: (LocalDate?) -> Unit, onTime: (LocalTime) -> Unit) {
    var pickDay by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val dates = rememberDateFormatter()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.poll_food_when), style = MaterialTheme.typography.labelLarge)
        PickerButton(Symbols.calendarMonth, day?.let { dates.dayMonthYear(it) } ?: stringResource(Res.string.poll_food_no_day)) { pickDay = true }
        if (day != null) {
            PickerButton(Symbols.schedule, formatTime(time ?: LocalTime(19, 0))) { pickTime = true }
            TextButton(onClick = { onDay(null) }) { Text(stringResource(Res.string.poll_food_no_day)) }
        }
    }
    if (pickDay) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (day ?: tripStart ?: today()).toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { onDay(it.fromPickerMillis()) }; pickDay = false }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDay = false }) { Text(stringResource(Res.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        IrisTimePickerDialog(initial = time ?: LocalTime(19, 0), onConfirm = { onTime(it); pickTime = false }, onDismiss = { pickTime = false })
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun OptionsSection(state: CreatePollUiState, onAdd: () -> Unit, onEdit: (String) -> Unit, onRemove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.poll_options_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.options.isEmpty()) {
            Text(stringResource(Res.string.poll_options_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.options.forEach { option ->
            val dto = option.asOptionDto()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(IrisShape.md).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(app.ransel.shared.domain.pollOptionLabel(state.kind, option), style = MaterialTheme.typography.titleSmall)
                    pollOptionSubtitle(dto, state.currency)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { onEdit(option.id) }) { Text(stringResource(Res.string.poll_option_edit)) }
                TextButton(onClick = { onRemove(option.id) }) {
                    Text(stringResource(Res.string.poll_option_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        TextButton(onClick = onAdd) {
            Symbol(Symbols.add, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(Res.string.poll_add_option), Modifier.padding(start = 8.dp))
        }
    }
}

@Preview
@Composable
private fun CreatePollPreview() = IrisPreview {
    CreatePollContent(
        state = CreatePollUiState(
            kind = PollKind.STAY, question = "Menginap di mana?",
            options = listOf(PollOptionInput("1", "Villa Ubud", priceMinor = 750_000, priceUnit = app.ransel.shared.model.PollPriceUnit.NIGHT)),
        ),
        onBack = {}, onQuestion = {}, onClosingDays = {}, onMulti = {}, onAnonymous = {}, onApplyWinner = {},
        onFoodDay = {}, onFoodTime = {}, onAddOption = {}, onEditOption = {}, onRemoveOption = {}, onCreate = {},
    )
}

@Preview
@Composable
private fun CreateBudgetPollPreview() = IrisPreview {
    CreatePollContent(
        state = CreatePollUiState(kind = PollKind.BUDGET, question = "Budget per orang berapa yang nyaman?", anonymous = true, questionWrong = true),
        onBack = {}, onQuestion = {}, onClosingDays = {}, onMulti = {}, onAnonymous = {}, onApplyWinner = {},
        onFoodDay = {}, onFoodTime = {}, onAddOption = {}, onEditOption = {}, onRemoveOption = {}, onCreate = {},
    )
}
