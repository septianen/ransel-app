package app.ransel.feature.interview

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.tripform.ThousandsVisualTransformation
import app.ransel.navigation.SheetBackHandler
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.domain.InterviewStep
import app.ransel.shared.domain.SummaryRow
import app.ransel.shared.domain.canContinue
import app.ransel.shared.domain.canSkip
import app.ransel.shared.domain.canSkipInterview
import app.ransel.shared.domain.isOverseas
import app.ransel.shared.domain.progress
import app.ransel.shared.domain.steps
import app.ransel.shared.domain.summaryRows
import app.ransel.shared.model.PriceLevel
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.dashedBorder
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_style_packed
import ransel.composeapp.generated.resources.explore_style_relaxed
import ransel.composeapp.generated.resources.interview_continue
import ransel.composeapp.generated.resources.interview_create
import ransel.composeapp.generated.resources.interview_create_failed
import ransel.composeapp.generated.resources.interview_create_hint
import ransel.composeapp.generated.resources.interview_creating
import ransel.composeapp.generated.resources.interview_leave_body
import ransel.composeapp.generated.resources.interview_leave_confirm
import ransel.composeapp.generated.resources.interview_leave_stay
import ransel.composeapp.generated.resources.interview_leave_title
import ransel.composeapp.generated.resources.interview_name_template
import ransel.composeapp.generated.resources.interview_save
import ransel.composeapp.generated.resources.interview_see_summary
import ransel.composeapp.generated.resources.interview_skip
import ransel.composeapp.generated.resources.interview_summary_add
import ransel.composeapp.generated.resources.interview_summary_budget
import ransel.composeapp.generated.resources.interview_summary_budget_hint
import ransel.composeapp.generated.resources.interview_summary_dates
import ransel.composeapp.generated.resources.interview_summary_dates_undecided
import ransel.composeapp.generated.resources.interview_summary_departure
import ransel.composeapp.generated.resources.interview_summary_departure_value
import ransel.composeapp.generated.resources.interview_summary_destination
import ransel.composeapp.generated.resources.interview_summary_from
import ransel.composeapp.generated.resources.interview_summary_local
import ransel.composeapp.generated.resources.interview_summary_name
import ransel.composeapp.generated.resources.interview_summary_not_set
import ransel.composeapp.generated.resources.interview_summary_subtitle
import ransel.composeapp.generated.resources.interview_summary_taste
import ransel.composeapp.generated.resources.interview_summary_taste_value
import ransel.composeapp.generated.resources.interview_summary_title
import app.ransel.feature.explore.label
import ransel.composeapp.generated.resources.interview_taste_selected
import ransel.composeapp.generated.resources.interview_pick_one
import ransel.composeapp.generated.resources.interview_create_offline_note
import ransel.composeapp.generated.resources.interview_credits_balance_short
import ransel.composeapp.generated.resources.interview_credits_balance
import ransel.composeapp.generated.resources.interview_create_and_search_unknown
import ransel.composeapp.generated.resources.interview_create_and_search
import ransel.composeapp.generated.resources.interview_summary_with_note
import ransel.composeapp.generated.resources.interview_summary_no_note
import ransel.composeapp.generated.resources.interview_summary_price
import ransel.composeapp.generated.resources.interview_summary_taste_change
import ransel.composeapp.generated.resources.interview_destination_bottom_note

/**
 * "Buat trip" as a short interview (spec "Wawancara Buat trip" 16 Nov 2026): Layar 1, W1–W4, then a summary. One route; the
 * questions change inside it. [onCreated] gets the new trip (and whether the user is signed in, for Ajak teman),
 * [onLeave] is the X or the system back after the leave dialog.
 */
@Composable
fun InterviewScreen(
    door: InterviewDoor,
    onLeave: () -> Unit,
    onCreated: (tripId: String, signedIn: Boolean) -> Unit,
    onCreatedAndSearch: (tripId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InterviewViewModel = koinViewModel(key = "interview-${door.name}") { parametersOf(door) },
) {
    val state by viewModel.state.collectAsState()
    val template = stringResource(Res.string.interview_name_template)
    LaunchedEffect(template) { viewModel.setNameTemplate(template) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is InterviewEvent.Created -> onCreated(event.tripId, event.signedIn)
                is InterviewEvent.CreatedAndSearch -> onCreatedAndSearch(event.tripId)
                InterviewEvent.Leave -> onLeave()
            }
        }
    }
    SheetBackHandler(onBack = viewModel::back)

    InterviewContent(
        state = state,
        modifier = modifier,
        viewModel = viewModel,
    )

    state.tasteDraft?.let { draft ->
        TasteSheet(
            draft = draft,
            khas = state.khas,
            destination = state.destinationName,
            onInterest = viewModel::draftToggleInterest,
            onExtra = viewModel::draftToggleExtra,
            onStyle = viewModel::draftStyle,
            onPrice = viewModel::draftPriceLevel,
            defaultPrice = state.budgetPriceLevel ?: PriceLevel.MEDIUM,
            priceFromBudget = state.budgetPriceLevel != null,
            onNotes = viewModel::draftNotes,
            onSave = viewModel::saveTasteSheet,
            onDismiss = viewModel::closeTasteSheet,
        )
    }
    if (state.leaveDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.interview_leave_title),
            body = stringResource(Res.string.interview_leave_body),
            confirmLabel = stringResource(Res.string.interview_leave_confirm),
            dismissLabel = stringResource(Res.string.interview_leave_stay),
            onConfirm = viewModel::leave,
            onDismiss = viewModel::dismissLeave,
            destructive = true,
            symbol = Symbols.error,
        )
    }
}

@Composable
private fun InterviewContent(state: InterviewUiState, viewModel: InterviewViewModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val door = state.door
    val answers = state.answers
    val step = state.step
    val editing = state.editing != null
    Column(modifier.fillMaxSize().background(scheme.surface).statusBarsPadding()) {
        // The header: the segments and "X dari Y" (not in "Mode ubah", which has no indicator)
        if (step != null && !editing) {
            val progress = answers.progress(step)
            InterviewHeader(
                position = progress.position,
                total = progress.total,
                leadingSymbol = if (step == InterviewStep.DESTINATION) Symbols.close else Symbols.arrowBack,
                onLeading = if (step == InterviewStep.DESTINATION) viewModel::requestLeave else viewModel::back,
                onSkipAll = if (canSkipInterview(step)) viewModel::skipInterview else null,
            )
        } else if (step == null) {
            val total = answers.steps().size
            InterviewHeader(position = total, total = total, leadingSymbol = Symbols.arrowBack, onLeading = viewModel::back, done = true)
        } else {
            InterviewHeader(position = 0, total = 0, leadingSymbol = Symbols.arrowBack, onLeading = viewModel::back, showProgress = false)
        }
        if (state.offline) OfflineBanner()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                InterviewStep.DESTINATION -> DestinationQuestion(
                    state, viewModel::onDestinationPicked, viewModel::onDestinationCleared, viewModel::onDates, viewModel::onDatesUndecided,
                )
                InterviewStep.ORIGIN -> OriginQuestion(state, onPickLast = viewModel::pickOrigin, onPicked = viewModel::onOriginPicked, onUseLocation = viewModel::useCurrentLocation)
                InterviewStep.DEPARTURE -> DepartureQuestion(state, viewModel::chooseDeparture)
                InterviewStep.LOCAL_TRANSPORT -> LocalTransportQuestion(state, viewModel::chooseLocalTransport)
                InterviewStep.TASTE -> TasteQuestion(state, viewModel::toggleInterest, viewModel::toggleExtraInterest, viewModel::chooseStyle)
                null -> InterviewSummary(state, viewModel)
            }
        }

        if (step != null) {
            val label = when {
                editing -> stringResource(Res.string.interview_save)
                step == InterviewStep.TASTE -> stringResource(Res.string.interview_see_summary)
                else -> stringResource(Res.string.interview_continue)
            }
            InterviewBottomBar(
                primaryLabel = label,
                primaryEnabled = canContinue(door, step, answers),
                onPrimary = viewModel::next,
                note = when {
                    step == InterviewStep.DESTINATION && !editing -> stringResource(Res.string.interview_destination_bottom_note)
                    // from Jelajah W4 has no Lewati: it says what it takes, then how many are picked
                    step == InterviewStep.TASTE && door == InterviewDoor.EXPLORE -> {
                        val picked = answers.taste.interests.size + answers.taste.extraInterests.size
                        if (picked == 0) stringResource(Res.string.interview_pick_one) else stringResource(Res.string.interview_taste_selected, picked)
                    }
                    else -> null
                },
                skipLabel = if (!editing && canSkip(door, step)) stringResource(Res.string.interview_skip) else null,
                onSkip = viewModel::skip,
            )
        } else {
            // From Jelajah, online: "Buat trip dan cari · 3 kredit" with the balance; offline it is only "Buat trip" (spec 4)
            val searches = door == InterviewDoor.EXPLORE && state.signedIn && !state.offline
            val credits = state.credits
            val cost = credits?.costs?.search
            val createLabel = when {
                state.creating -> stringResource(Res.string.interview_creating)
                searches && cost != null -> stringResource(Res.string.interview_create_and_search, cost)
                searches -> stringResource(Res.string.interview_create_and_search_unknown)
                else -> stringResource(Res.string.interview_create)
            }
            val createNote = when {
                state.createFailed -> stringResource(Res.string.interview_create_failed)
                door == InterviewDoor.EXPLORE && !searches -> stringResource(Res.string.interview_create_offline_note)
                searches && credits != null && cost != null ->
                    if (credits.available >= cost) {
                        stringResource(Res.string.interview_credits_balance, credits.available, credits.available - cost)
                    } else {
                        stringResource(Res.string.interview_credits_balance_short, credits.available)
                    }
                else -> stringResource(Res.string.interview_create_hint)
            }
            InterviewBottomBar(
                primaryLabel = createLabel,
                primaryEnabled = !state.creating,
                onPrimary = viewModel::create,
                primaryLoading = state.creating,
                note = createNote,
            )
        }
    }
}

// ── Summary ──────────────────────────────────────────────────────────────────

/** "Cek dulu, ya": the answers as rows. A row that was skipped is a dashed box with "Belum diisi · Isi". */
@Composable
private fun InterviewSummary(state: InterviewUiState, viewModel: InterviewViewModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val answers = state.answers
    val formatter = rememberDateFormatter()
    val rows = answers.summaryRows()
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QuestionTitle(stringResource(Res.string.interview_summary_title), stringResource(Res.string.interview_summary_subtitle))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (row, filled) ->
                when (row) {
                    SummaryRow.NAME -> IrisTextField(
                        label = stringResource(Res.string.interview_summary_name),
                        value = state.tripName,
                        onValueChange = viewModel::onNameChange,
                    )
                    SummaryRow.DESTINATION -> SummaryValueRow(
                        stringResource(Res.string.interview_summary_destination), state.destinationName, filled,
                        onClick = { viewModel.edit(InterviewStep.DESTINATION) },
                    )
                    SummaryRow.DATES -> SummaryValueRow(
                        stringResource(Res.string.interview_summary_dates),
                        if (answers.datesUndecided) stringResource(Res.string.interview_summary_dates_undecided)
                        else answers.startDate?.let { s -> answers.endDate?.let { e -> formatter.range(s, e) } }.orEmpty(),
                        filled,
                        onClick = { viewModel.edit(InterviewStep.DESTINATION) },
                    )
                    SummaryRow.DEPARTURE -> DepartureSummaryRow(state, filled, viewModel)
                    SummaryRow.LOCAL_TRANSPORT -> SummaryValueRow(
                        stringResource(Res.string.interview_summary_local),
                        answers.localTransport?.let { stringResource(it.labelRes) }.orEmpty(),
                        filled,
                        onClick = { viewModel.edit(InterviewStep.LOCAL_TRANSPORT) },
                    )
                    SummaryRow.TASTE -> {
                        val taste = answers.taste
                        val interests = (taste.interests.map { stringResource(it.label) } + taste.extraInterests).joinToString(", ")
                        val style = taste.style?.let { stringResource(if (it == app.ransel.shared.model.TravelStyle.SANTAI) Res.string.explore_style_relaxed else Res.string.explore_style_packed) }
                        val value = listOfNotNull(interests.takeIf { it.isNotEmpty() }, style).joinToString(" · ")
                        if (state.door == InterviewDoor.EXPLORE && filled) {
                            // from Jelajah the row is taller: what the search will use, with "Ubah" for the sheet
                            val price = state.priceLevel
                            TasteSummaryRow(
                                label = stringResource(Res.string.interview_summary_taste),
                                value = value,
                                detail = stringResource(
                                    Res.string.interview_summary_price,
                                    stringResource(price.labelRes), price.signs(),
                                    stringResource(if (taste.notes.isBlank()) Res.string.interview_summary_no_note else Res.string.interview_summary_with_note),
                                ),
                                onChange = viewModel::openTasteSheet,
                                onClick = { viewModel.edit(InterviewStep.TASTE) },
                            )
                        } else {
                            SummaryValueRow(
                                stringResource(Res.string.interview_summary_taste), value, filled,
                                onClick = { viewModel.edit(InterviewStep.TASTE) },
                            )
                        }
                    }
                    SummaryRow.BUDGET -> IrisTextField(
                        label = stringResource(Res.string.interview_summary_budget),
                        value = state.budgetDigits,
                        onValueChange = viewModel::onBudgetChange,
                        prefix = state.currency,
                        placeholder = stringResource(Res.string.interview_summary_budget_hint),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ThousandsVisualTransformation,
                    )
                }
            }
        }
    }
}

/** A summary row: its label, its value and a chevron; not filled = a dashed box that says "Belum diisi · Isi". */
@Composable
private fun SummaryValueRow(label: String, value: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(IrisShape.lg)
            .let { if (filled) it.border(1.dp, scheme.outlineVariant, IrisShape.lg) else it.dashedBorder(scheme.outline, radius = 16.dp) }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(
                if (filled) value else stringResource(Res.string.interview_summary_not_set),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (filled) scheme.onSurface else scheme.onSurfaceVariant,
            )
        }
        if (filled) {
            Symbol(Symbols.chevronRight, size = 20.dp, tint = scheme.onSurfaceVariant)
        } else {
            Text(stringResource(Res.string.interview_summary_add), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
        }
    }
}

/** The Seleramu row of the Jelajah summary: interests and style, the price range and note, and "Ubah" (the sheet). */
@Composable
private fun TasteSummaryRow(label: String, value: String, detail: String, onChange: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(IrisShape.lg)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        androidx.compose.material3.TextButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.interview_summary_taste_change), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
        }
    }
}

/** "Dari Subang · Motor sendiri": the city opens W1, the way of going opens W2. Abroad there is no W2. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DepartureSummaryRow(state: InterviewUiState, filled: Boolean, viewModel: InterviewViewModel) {
    val scheme = MaterialTheme.colorScheme
    val answers = state.answers
    val origin = answers.origin
    val departure = answers.departure
    if (!filled || origin == null) {
        SummaryValueRow(
            stringResource(Res.string.interview_summary_departure), "", filled = false,
            onClick = { viewModel.edit(if (origin == null) InterviewStep.ORIGIN else InterviewStep.DEPARTURE) },
        )
        return
    }
    // one card like the other rows: a tap anywhere opens W1 (the city); the way of going, when there is one, opens W2
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(IrisShape.lg)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .clickable(role = Role.Button) { viewModel.edit(InterviewStep.ORIGIN) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.interview_summary_departure), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.interview_summary_from, origin.name),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = scheme.onSurface,
                )
                if (departure != null && !answers.isOverseas()) {
                    Text(
                        "· " + stringResource(departure.labelRes),
                        Modifier.clickable(role = Role.Button) { viewModel.edit(InterviewStep.DEPARTURE) },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                }
            }
        }
        Symbol(Symbols.chevronRight, size = 20.dp, tint = scheme.onSurfaceVariant)
    }
}
