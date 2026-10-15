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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.data.explore.ExploreSession
import app.ransel.shared.model.BudgetLevel
import app.ransel.shared.model.Interest
import app.ransel.shared.model.TravelStyle
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.*

val Interest.label: StringResource
    get() = when (this) {
        Interest.NATURE -> Res.string.explore_interest_nature
        Interest.FOOD -> Res.string.explore_interest_food
        Interest.BEACH -> Res.string.explore_interest_beach
        Interest.CULTURE -> Res.string.explore_interest_culture
        Interest.SHOPPING -> Res.string.explore_interest_shopping
        Interest.ADVENTURE -> Res.string.explore_interest_adventure
        Interest.NIGHTLIFE -> Res.string.explore_interest_nightlife
        Interest.PHOTO -> Res.string.explore_interest_photo
    }

val BudgetLevel.label: StringResource
    get() = when (this) {
        BudgetLevel.HEMAT -> Res.string.explore_budget_low
        BudgetLevel.SEDANG -> Res.string.explore_budget_mid
        BudgetLevel.BEBAS -> Res.string.explore_budget_high
    }

val TravelStyle.label: StringResource
    get() = when (this) {
        TravelStyle.SANTAI -> Res.string.explore_style_relaxed
        TravelStyle.PADAT -> Res.string.explore_style_packed
    }

/** The Explore tab: guest, no trip, or the preference form with an optional "Lanjutkan" card. */
@Composable
fun ExploreScreen(
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onCreateTrip: () -> Unit,
    onOpenSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(Res.string.explore_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize())
            !state.signedIn -> GuestState(onSignIn, onSignUp)
            state.trip == null -> NoTripState(onCreateTrip)
            else -> ExploreForm(state, viewModel, onOpenSwipe)
        }
    }
}

@Composable
private fun IntroBlock(symbol: String, title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Symbol(symbol, size = 28.dp, filled = true, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GuestState(onSignIn: () -> Unit, onSignUp: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IntroBlock(Symbols.lockPerson, stringResource(Res.string.explore_guest_title), stringResource(Res.string.explore_guest_body))
        listOf(
            Triple(Symbols.autoAwesome, Res.string.explore_guest_point1_title, Res.string.explore_guest_point1_body),
            Triple(Symbols.eventNote, Res.string.explore_guest_point2_title, Res.string.explore_guest_point2_body),
            Triple(Symbols.cloudSync, Res.string.explore_guest_point3_title, Res.string.explore_guest_point3_body),
        ).forEach { (symbol, title, body) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(IrisShape.md).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Symbol(symbol, filled = true)
                }
                Column {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.explore_guest_sign_in), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = onSignUp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.explore_guest_sign_up), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(Res.string.explore_guest_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoTripState(onCreateTrip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IntroBlock(Symbols.luggage, stringResource(Res.string.explore_notrip_title), stringResource(Res.string.explore_notrip_body))
        listOf(
            Res.string.explore_notrip_step1_title to Res.string.explore_notrip_step1_body,
            Res.string.explore_notrip_step2_title to Res.string.explore_notrip_step2_body,
            Res.string.explore_notrip_step3_title to Res.string.explore_notrip_step3_body,
        ).forEachIndexed { index, (title, body) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(onClick = onCreateTrip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Symbol(Symbols.add, tint = MaterialTheme.colorScheme.onPrimary)
            Text(stringResource(Res.string.explore_notrip_cta), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreForm(state: ExploreUiState, vm: ExploreViewModel, onOpenSwipe: () -> Unit) {
    val trip = state.trip!!
    val dates = rememberDateFormatter()
    var tripMenu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        state.resume?.let { ResumeCard(it, onOpenSwipe) }
        IntroBlock(
            Symbols.autoAwesome,
            stringResource(if (state.resume != null) Res.string.explore_new_search else Res.string.explore_intro_title),
            stringResource(Res.string.explore_intro_body),
        )
        IrisTextField(
            label = stringResource(Res.string.explore_destination),
            value = state.destination,
            onValueChange = vm::setDestination,
            placeholder = stringResource(Res.string.explore_destination_hint),
            leadingSymbol = Symbols.locationOn,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.explore_dates), style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, scheme.outline, IrisShape.md).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.calendarMonth, tint = scheme.onSurfaceVariant)
                Text(dates.range(trip.startDate, trip.endDate), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                val days = trip.startDate.daysUntil(trip.endDate) + 1
                Text(
                    pluralStringResource(Res.plurals.explore_days, days, days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Box {
                Row(
                    Modifier.clip(IrisShape.sm).clickable(enabled = state.trips.size > 1, role = Role.Button) { tripMenu = true }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Symbol(Symbols.info, size = 16.dp, tint = scheme.onSurfaceVariant)
                    Text(
                        stringResource(Res.string.explore_from_trip, trip.title),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (state.trips.size > 1) Symbol(Symbols.expandMore, size = 16.dp, tint = scheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = tripMenu, onDismissRequest = { tripMenu = false }) {
                    state.trips.forEach { t ->
                        DropdownMenuItem(text = { Text(t.title) }, onClick = { tripMenu = false; vm.selectTrip(t) })
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.explore_budget), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetLevel.entries.forEach { level ->
                    val selected = level == state.budget
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(IrisShape.md)
                            .background(if (selected) scheme.primaryContainer else scheme.surface)
                            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant), IrisShape.md)
                            .clickable(role = Role.RadioButton) { vm.setBudget(level) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(level.label), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(
                                when (level) {
                                    BudgetLevel.HEMAT -> Res.string.explore_budget_low_hint
                                    BudgetLevel.SEDANG -> Res.string.explore_budget_mid_hint
                                    BudgetLevel.BEBAS -> Res.string.explore_budget_high_hint
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(Res.string.explore_interests), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.explore_interests_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Interest.entries.forEach { interest ->
                    val selected = interest in state.interests
                    Row(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(if (selected) scheme.primaryContainer else scheme.surface)
                            .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, CircleShape)
                            .clickable(role = Role.Checkbox) { vm.toggleInterest(interest) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (selected) Symbol(Symbols.check, size = 18.dp, tint = scheme.onPrimaryContainer)
                        Text(stringResource(interest.label), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
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
        Button(
            onClick = { vm.start(onOpenSwipe) },
            enabled = state.canSearch,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Symbol(Symbols.autoAwesome, filled = true, tint = scheme.onPrimary)
            Text(stringResource(Res.string.explore_search), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Design `JelajahLanjut`: "Belum selesai · Rekomendasi untuk Bali · 3 dari 12 dilihat · 2 disimpan". */
@Composable
private fun ResumeCard(session: ExploreSession, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(scheme.primaryContainer)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.explore_resume_label), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
            Text(
                stringResource(Res.string.explore_for, session.form.destination),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimaryContainer,
            )
            Text(
                stringResource(Res.string.explore_resume_progress, session.position, session.cards.size, session.saved),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer,
            )
        }
        Text(stringResource(Res.string.explore_resume_cta), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
    }
}
