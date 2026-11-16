package app.ransel.feature.tripform.travel

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.getString
import app.ransel.content.stringResource
import app.ransel.feature.explore.label
import app.ransel.feature.interview.TasteSheet
import app.ransel.feature.interview.labelRes
import app.ransel.feature.interview.symbol
import app.ransel.feature.itinerary.travel.mainModeLabel
import app.ransel.shared.domain.InterviewTaste
import app.ransel.shared.domain.TravelRow
import app.ransel.shared.domain.cityDisplayName
import app.ransel.shared.domain.effectiveMainTravelMode
import app.ransel.shared.domain.localTransportFollowsVehicle
import app.ransel.shared.domain.travelRows
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.MainTravelMode
import app.ransel.shared.model.TravelStyle
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.dashedBorder
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.explore_style_packed
import ransel.composeapp.generated.resources.explore_style_relaxed
import ransel.composeapp.generated.resources.interview_summary_add
import ransel.composeapp.generated.resources.travel_undo
import ransel.composeapp.generated.resources.trip_answer_changed_departure
import ransel.composeapp.generated.resources.trip_answer_changed_local
import ransel.composeapp.generated.resources.trip_answer_changed_origin
import ransel.composeapp.generated.resources.trip_answer_changed_taste
import ransel.composeapp.generated.resources.trip_reset_action
import ransel.composeapp.generated.resources.trip_reset_body
import ransel.composeapp.generated.resources.trip_reset_body_destination
import ransel.composeapp.generated.resources.trip_reset_title
import ransel.composeapp.generated.resources.trip_travel_a11y_empty
import ransel.composeapp.generated.resources.trip_travel_a11y_follows
import ransel.composeapp.generated.resources.trip_travel_a11y_row
import ransel.composeapp.generated.resources.trip_travel_departure
import ransel.composeapp.generated.resources.trip_travel_follows
import ransel.composeapp.generated.resources.trip_travel_hint
import ransel.composeapp.generated.resources.trip_travel_local
import ransel.composeapp.generated.resources.trip_travel_not_set
import ransel.composeapp.generated.resources.trip_travel_origin
import ransel.composeapp.generated.resources.trip_travel_own_note
import ransel.composeapp.generated.resources.trip_travel_taste
import ransel.composeapp.generated.resources.trip_travel_title

/**
 * The section "Perjalanan" of Edit trip (spec 1638556, mockups s26-1), under the budget: one row per answer of the interview
 * (Berangkat dari, Cara berangkat, Keliling di {destinasi}, Seleramu). A tap opens the screen that changes it ([onOpenRow]) or, for
 * the Seleramu, the sheet. A change is stored at once; this section says so ("... diubah." with "Urungkan", into [snackbar]) and
 * explains a reset ("Cara berangkat direset"). Owner only: the form is the owner's.
 */
@Composable
fun TripTravelSection(
    tripId: String,
    snackbar: SnackbarHostState,
    onOpenRow: (TravelRow) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripTravelViewModel = koinViewModel(key = "trip-travel-$tripId") { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var reset by remember { mutableStateOf<ResetDialog?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.changes.collect { change ->
            if (change.tripId != tripId) return@collect
            if (change.resetDeparture != null) reset = ResetDialog(change.resetDeparture, change.byDestination)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val message = getString(
                    when (change.row) {
                        TravelRow.ORIGIN -> Res.string.trip_answer_changed_origin
                        TravelRow.DEPARTURE -> Res.string.trip_answer_changed_departure
                        TravelRow.LOCAL_TRANSPORT -> Res.string.trip_answer_changed_local
                        TravelRow.TASTE -> Res.string.trip_answer_changed_taste
                    },
                )
                val result = snackbar.showSnackbar(message = message, actionLabel = getString(Res.string.travel_undo))
                if (result == SnackbarResult.ActionPerformed) viewModel.undo(change)
            }
        }
    }
    val trip = state.trip ?: return
    // the answers are the creator's: only the owner changes them from here (an editor changes the main mode from the Travel sheet)
    if (trip.myRole != app.ransel.shared.model.Role.OWNER) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(Res.string.trip_travel_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(Res.string.trip_travel_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trip.travelRows().forEach { row ->
            when (row) {
                TravelRow.ORIGIN -> AnswerRow(
                    Symbols.locationOn, stringResource(Res.string.trip_travel_origin),
                    trip.origin?.name?.let(::cityDisplayName), onClick = { onOpenRow(row) },
                )
                TravelRow.DEPARTURE -> AnswerRow(
                    trip.departureMode?.symbol ?: Symbols.directionsCar, stringResource(Res.string.trip_travel_departure),
                    trip.departureMode?.let { stringResource(it.labelRes) }, onClick = { onOpenRow(row) },
                )
                TravelRow.LOCAL_TRANSPORT -> localRow(trip, onClick = { onOpenRow(row) })
                TravelRow.TASTE -> AnswerRow(
                    Symbols.autoAwesome, stringResource(Res.string.trip_travel_taste),
                    state.taste?.let { tasteLine(it) }, onClick = viewModel::openTasteSheet,
                )
            }
        }
        if (trip.localTransportFollowsVehicle()) {
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Symbol(Symbols.info, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(Res.string.trip_travel_own_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    state.draft?.let { draft ->
        TasteSheet(
            draft = draft,
            khas = state.khas,
            destination = trip.destination,
            onInterest = viewModel::draftToggleInterest,
            onExtra = viewModel::draftToggleExtra,
            onStyle = viewModel::draftStyle,
            onPrice = viewModel::draftPriceLevel,
            defaultPrice = state.budgetPriceLevel ?: app.ransel.shared.model.PriceLevel.MEDIUM,
            priceFromBudget = state.budgetPriceLevel != null,
            onNotes = viewModel::draftNotes,
            onSave = viewModel::saveTasteSheet,
            onDismiss = viewModel::closeTasteSheet,
        )
    }
    reset?.let { dialog ->
        ConfirmDialog(
            title = stringResource(Res.string.trip_reset_title),
            body = stringResource(
                if (dialog.byDestination) Res.string.trip_reset_body_destination else Res.string.trip_reset_body,
                stringResource(dialog.mode.labelRes),
            ),
            confirmLabel = stringResource(Res.string.trip_reset_action),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = {
                reset = null
                onOpenRow(TravelRow.DEPARTURE)
            },
            onDismiss = { reset = null },
            symbol = Symbols.warning,
        )
    }
}

/** What the dialog "Cara berangkat direset" names: the answer that stopped being possible, and why. */
private data class ResetDialog(val mode: app.ransel.shared.model.DepartureMode, val byDestination: Boolean)

/** "Keliling di Bandung": the answer of W3, or, with an own vehicle, "Motor · ikut kendaraanmu" (not a choice of its own). */
@Composable
private fun localRow(trip: TripDto, onClick: () -> Unit) {
    val label = stringResource(Res.string.trip_travel_local, trip.destination)
    if (trip.localTransportFollowsVehicle()) {
        val mode: MainTravelMode = trip.effectiveMainTravelMode()
        AnswerRow(
            if (mode == MainTravelMode.CAR) Symbols.directionsCar else app.ransel.ui.symbols.Symbols.twoWheeler,
            label, stringResource(Res.string.trip_travel_follows, mainModeLabel(mode)), onClick = null,
        )
    } else {
        AnswerRow(
            trip.localTransport?.symbol ?: app.ransel.ui.symbols.Symbols.twoWheeler,
            label, trip.localTransport?.let { stringResource(it.labelRes) }, onClick = onClick,
        )
    }
}

/** "Kuliner, Alam · Santai": the interests and the style on one line. */
@Composable
private fun tasteLine(taste: InterviewTaste): String {
    val interests = (taste.interests.map { stringResource(it.label) } + taste.extraInterests).joinToString(", ")
    val style = taste.style?.let { stringResource(if (it == TravelStyle.SANTAI) Res.string.explore_style_relaxed else Res.string.explore_style_packed) }
    return listOfNotNull(interests.takeIf { it.isNotEmpty() }, style).joinToString(" · ")
}

/**
 * One answer: a tile with its icon, the label and the value, and a chevron; the whole row taps ([onClick] null = it does not: the
 * answer follows the vehicle). Not answered: a dashed box that says "Belum diisi" with "Isi".
 */
@Composable
private fun AnswerRow(symbol: String, label: String, value: String?, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val filled = !value.isNullOrBlank()
    val description = when {
        onClick == null -> stringResource(Res.string.trip_travel_a11y_follows, label, value.orEmpty())
        filled -> stringResource(Res.string.trip_travel_a11y_row, label, value.orEmpty())
        else -> stringResource(Res.string.trip_travel_a11y_empty, label)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(IrisShape.lg)
            .let { if (filled) it.border(1.dp, scheme.outlineVariant, IrisShape.lg) else it.dashedBorder(scheme.outline, radius = 16.dp) }
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) role = Role.Button
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).let { it }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Symbol(symbol, size = 22.dp, tint = scheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurfaceVariant)
            Text(
                if (filled) value.orEmpty() else stringResource(Res.string.trip_travel_not_set),
                style = if (filled) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyLarge,
                color = if (filled) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!filled) {
            Text(
                stringResource(Res.string.interview_summary_add),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
            )
        } else if (onClick != null) {
            Symbol(Symbols.chevronRight, size = 20.dp, tint = scheme.onSurfaceVariant)
        }
    }
}
