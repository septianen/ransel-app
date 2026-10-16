package app.ransel.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.feature.tripform.TripDateRangePicker
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.TripCover
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*

/**
 * Explore step 2 (design `PreferensiAI`, `JelajahTripBaru`): the form for one trip. [tripId] null = a new trip whose
 * destination and dates are asked here (step 2b). [onChangeTrip] is the "Ganti" button back to step 1.
 */
@Composable
fun ExplorePreferencesScreen(
    tripId: String?,
    onBack: () -> Unit,
    onChangeTrip: () -> Unit,
    onOpenSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExplorePreferencesViewModel = koinViewModel(key = "explore-prefs-$tripId") { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event is ExplorePreferencesEvent.StartFailed) snackbar.showSnackbar(getString(Res.string.explore_start_failed))
        }
    }
    val defaultName = stringResource(Res.string.explore_new_trip_default_name, state.destination.trim())
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
                }
                Text(
                    stringResource(if (state.newTrip) Res.string.explore_new_trip_title else Res.string.explore_prefs_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                PreferencesForm(state, viewModel, defaultName, onChangeTrip) { viewModel.start(defaultName, onOpenSwipe) }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            snackbar = { data -> UndoSnackbar(data.visuals.message) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferencesForm(
    state: ExplorePreferencesState,
    vm: ExplorePreferencesViewModel,
    defaultName: String,
    onChangeTrip: () -> Unit,
    onSearch: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var showDates by remember { mutableStateOf(false) }
    var destinationFocused by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.newTrip) {
            Text(stringResource(Res.string.explore_new_trip_body), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            IrisTextField(
                label = stringResource(Res.string.explore_destination),
                value = state.destination,
                onValueChange = vm::setDestination,
                placeholder = stringResource(Res.string.explore_destination_hint),
                leadingSymbol = Symbols.locationOn,
                modifier = Modifier.onFocusChanged {
                    if (destinationFocused && !it.hasFocus) vm.onDestinationBlur()
                    destinationFocused = it.hasFocus
                },
            )
            DatesField(state, onClick = { showDates = true })
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IrisTextField(
                    label = stringResource(Res.string.explore_new_trip_name),
                    value = state.tripName ?: defaultName,
                    onValueChange = vm::setTripName,
                    leadingSymbol = Symbols.edit,
                )
                Text(stringResource(Res.string.explore_new_trip_name_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        } else {
            TripCard(state, onChangeTrip)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IrisTextField(
                    label = stringResource(Res.string.explore_focus_area),
                    labelSuffix = stringResource(Res.string.explore_optional),
                    value = state.focusArea,
                    onValueChange = vm::setFocusArea,
                    placeholder = stringResource(Res.string.explore_focus_area_hint),
                    leadingSymbol = Symbols.locationOn,
                )
                Text(
                    stringResource(Res.string.explore_focus_area_helper, state.destination),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        PriceSection(state, vm)
        InterestsSection(state, vm)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.explore_style), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TravelStyle.entries.forEach { style ->
                    val selected = style == state.style
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(IrisShape.md)
                            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.4f) else scheme.surface)
                            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant), IrisShape.md)
                            .clickable(role = Role.RadioButton) { vm.setStyle(style) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) scheme.primary else scheme.outline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary)) }
                        Column {
                            Text(stringResource(style.label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(if (style == TravelStyle.SANTAI) Res.string.explore_style_relaxed_hint else Res.string.explore_style_packed_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        IrisTextField(
            label = stringResource(Res.string.explore_notes),
            labelSuffix = stringResource(Res.string.explore_optional),
            value = state.notes,
            onValueChange = vm::setNotes,
            placeholder = stringResource(Res.string.explore_notes_hint),
            singleLine = false,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadingButton(
                onClick = onSearch,
                loading = state.starting,
                enabled = state.canSearch,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Symbol(Symbols.autoAwesome, filled = true, tint = scheme.onPrimary)
                Text(
                    stringResource(if (state.newTrip) Res.string.explore_search_new_trip else Res.string.explore_search),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                if (state.newTrip) stringResource(Res.string.explore_new_trip_footnote)
                // TODO: the real remaining quota once the API can tell it (see ExploreTripPickerScreen).
                else stringResource(Res.string.explore_quota_use, EXPLORE_QUOTA_PLACEHOLDER_LEFT),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (!state.hasInterest && state.showErrors) {
                Text(stringResource(Res.string.explore_interest_required), style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
        }
    }
    if (showDates) {
        TripDateRangePicker(
            start = state.startDate,
            end = state.endDate,
            onConfirm = { s, e -> vm.setDates(s, e); showDates = false },
            onDismiss = { showDates = false },
        )
    }
}

@Composable
private fun TripCard(state: ExplorePreferencesState, onChange: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceVariant).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TripCover(state.tripTitle, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.explore_for_trip), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(state.tripTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                "${state.destination} · ${dates.range(state.startDate, state.endDate)} · " + pluralStringResource(Res.plurals.explore_days, state.days, state.days),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChange) { Text(stringResource(Res.string.explore_change_trip), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun DatesField(state: ExplorePreferencesState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.explore_dates), style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(IrisShape.md)
                .border(1.dp, scheme.outline, IrisShape.md)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(Symbols.calendarMonth, tint = scheme.onSurfaceVariant)
            Text(dates.range(state.startDate, state.endDate), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(pluralStringResource(Res.plurals.explore_days, state.days, state.days), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PriceSection(state: ExplorePreferencesState, vm: ExplorePreferencesViewModel) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(Res.string.explore_price_title), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(Res.string.explore_price_relative), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceLevel.entries.forEach { level ->
                val selected = level == state.priceLevel
                Column(
                    Modifier
                        .weight(1f)
                        .clip(IrisShape.md)
                        .background(if (selected) scheme.primaryContainer else scheme.surface)
                        .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant), IrisShape.md)
                        .clickable(role = Role.RadioButton) { vm.setPriceLevel(level) }
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(level.dollars, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(level.label), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(level.example), style = MaterialTheme.typography.labelSmall, color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                }
            }
        }
        val note = when {
            state.budgetNote != null ->
                stringResource(Res.string.explore_price_from_budget, state.tripTitle, formatMoney(state.budgetNote.amountMinor, state.budgetNote.currency))
            state.noBudget -> stringResource(Res.string.explore_price_no_budget)
            else -> null
        }
        note?.let {
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.sm).background(scheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Symbol(Symbols.savings, size = 16.dp, tint = scheme.primary)
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsSection(state: ExplorePreferencesState, vm: ExplorePreferencesViewModel) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(Res.string.explore_interests), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(Res.string.explore_interests_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Interest.entries.forEach { interest ->
                InterestChip(stringResource(interest.label), interest in state.interests, scheme.outline) { vm.toggleInterest(interest) }
            }
        }
        when (val suggestions = state.suggestions) {
            SuggestionsState.Hidden -> Unit
            is SuggestionsState.Loading -> KhasBox(stringResource(Res.string.explore_special_title, state.destination.trim())) {
                Text(
                    stringResource(Res.string.explore_special_loading, suggestions.destination),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onTertiaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(72, 96, 64, 88).forEach { w ->
                        Box(Modifier.width(w.dp).heightIn(min = 36.dp).clip(CircleShape).background(scheme.surface.copy(alpha = 0.6f)))
                    }
                }
            }
            is SuggestionsState.Loaded -> KhasBox(stringResource(Res.string.explore_special_title, state.destination.trim())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.interests.forEach { label ->
                        InterestChip(label, label in state.extraInterests, scheme.tertiary) { vm.toggleExtraInterest(label) }
                    }
                }
            }
        }
    }
}

/** The purple AI box with the sparkle (spec: destination interests look different from core ones). */
@Composable
private fun KhasBox(title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.tertiaryContainer.copy(alpha = 0.5f)).border(1.dp, scheme.tertiaryContainer, IrisShape.lg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Symbol(Symbols.autoAwesome, size = 16.dp, filled = true, tint = IrisTheme.accents.purple)
            Text(title, style = MaterialTheme.typography.labelLarge, color = IrisTheme.accents.purple, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun InterestChip(label: String, selected: Boolean, outline: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(1.dp, if (selected) scheme.primary else outline, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Symbol(Symbols.check, size = 18.dp, tint = scheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
