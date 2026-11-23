package app.ransel.feature.interview

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.platform.testTag
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
import app.ransel.shared.domain.departureSummaryEditStep
import app.ransel.shared.domain.isOverseas
import app.ransel.shared.domain.progress
import app.ransel.shared.domain.steps
import app.ransel.shared.domain.summaryRows
import app.ransel.shared.model.PriceLevel
import app.ransel.ui.components.ConfirmDialog
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
    /** From "Bikin trip dari sini": the destination and interests are filled in. */
    prefill: InterviewPrefill? = null,
    viewModel: InterviewViewModel = koinViewModel(key = "interview-${door.name}-${prefill?.hashCode() ?: 0}") { parametersOf(door) },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, prefill) { prefill?.let(viewModel::applyPrefill) }
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
        QuestionTitle(
            stringResource(Res.string.interview_summary_title),
            stringResource(Res.string.interview_summary_subtitle),
            modifier = Modifier.padding(top = 16.dp),
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (row, filled) ->
                when (row) {
                    SummaryRow.NAME -> SummaryEditableCard(
                        label = stringResource(Res.string.interview_summary_name),
                        value = state.tripName,
                        onValueChange = viewModel::onNameChange,
                        trailingEdit = true,
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
                    SummaryRow.DEPARTURE -> DepartureSummaryRow(state, filled, onEdit = viewModel::edit, modifier = Modifier.testTag("interview_summary_leaving"))
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
                    SummaryRow.BUDGET -> SummaryEditableCard(
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

/**
 * Summary-only editable row (spec "summary consistency", 22 Nov 2026): the same card as the info rows (radius 16,
 * outlineVariant 1 dp, min 56 dp, small grey label inside, bold value, placeholder of the same size in grey, focus = 2 dp
 * primary border without a height change). [IrisTextField] elsewhere is unchanged. The whole card is the text field
 * (decorationBox): a tap anywhere focuses it, and TalkBack reads the label with the value.
 */
@Composable
private fun SummaryEditableCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    trailingEdit: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val valueStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        singleLine = true,
        interactionSource = interaction,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = valueStyle.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(IrisShape.lg)
                    // the inner padding shrinks by the extra 1 dp when focused, so the height does not change
                    .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) scheme.primary else scheme.outlineVariant), IrisShape.lg)
                    .padding(horizontal = if (focused) 13.dp else 14.dp, vertical = if (focused) 9.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        prefix?.let { Text(it, style = valueStyle, color = scheme.onSurface) }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) Text(placeholder, style = valueStyle, color = scheme.onSurfaceVariant)
                            field()
                        }
                    }
                }
                if (trailingEdit) Symbol(Symbols.edit, size = 20.dp, tint = scheme.onSurfaceVariant)
            }
        },
    )
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
            .summaryCardClickable(onClick)
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

/** "Dari Subang · Motor sendiri": the whole card is one control like the sibling rows; the city line still opens W1. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DepartureSummaryRow(state: InterviewUiState, filled: Boolean, onEdit: (InterviewStep) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val answers = state.answers
    val origin = answers.origin
    val departure = answers.departure
    if (!filled || origin == null) {
        SummaryValueRow(
            stringResource(Res.string.interview_summary_departure), "", filled = false,
            onClick = { onEdit(answers.departureSummaryEditStep()) },
            modifier = modifier,
        )
        return
    }
    val valueStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    val showDeparture = departure != null && !answers.isOverseas()
    // The card opens "how to get there" (the departure step); "Dari <kota>" is its own target for the origin. Both span the
    // card's full height (min 56 dp), so neither is a small text target; before they were 48 dp text lines, which made
    // the card taller than its siblings (owner's screenshot, 22 Nov 2026).
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(IrisShape.lg)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .summaryCardClickable(onClick = { onEdit(answers.departureSummaryEditStep()) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val originColumn = Modifier
            .weight(1f, fill = !showDeparture)
            .fillMaxHeight()
            .then(if (showDeparture) Modifier.summaryCardClickable(onClick = { onEdit(InterviewStep.ORIGIN) }).testTag(LEAVING_ORIGIN_TAG) else Modifier)
            .padding(start = 14.dp, end = if (showDeparture) 4.dp else 14.dp, top = 10.dp, bottom = 10.dp)
        Column(originColumn, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
            Text(stringResource(Res.string.interview_summary_departure), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(stringResource(Res.string.interview_summary_from, origin.name), style = valueStyle, color = scheme.onSurface)
        }
        Row(
            Modifier.then(if (showDeparture) Modifier.weight(1f) else Modifier).fillMaxHeight().padding(end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            if (showDeparture && departure != null) {
                Text("· " + stringResource(departure.labelRes), Modifier.weight(1f), style = valueStyle, color = scheme.onSurface)
            }
            Symbol(Symbols.chevronRight, size = 20.dp, tint = scheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

/** The origin target inside the Leaving card ("Dari <kota>"), for tests. */
internal const val LEAVING_ORIGIN_TAG = "interview_summary_leaving_origin"
