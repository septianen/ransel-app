package app.ransel.feature.interview

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.ransel.content.stringResource
import app.ransel.feature.destination.DestinationChoice
import app.ransel.feature.destination.DestinationField
import app.ransel.feature.destination.DestinationPicker
import app.ransel.feature.explore.label
import app.ransel.feature.tripform.TripDateRangePicker
import app.ransel.shared.domain.DepartureBand
import app.ransel.shared.domain.departureBand
import app.ransel.shared.domain.departureOptions
import app.ransel.shared.domain.isOwnVehicle
import app.ransel.shared.domain.localTransportOptions
import app.ransel.shared.domain.roadDistanceKm
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.Interest
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.TravelStyle
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.today
import org.jetbrains.compose.resources.StringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_special_title
import ransel.composeapp.generated.resources.explore_style_packed_hint
import ransel.composeapp.generated.resources.explore_style_relaxed_hint
import ransel.composeapp.generated.resources.interview_after_note
import ransel.composeapp.generated.resources.interview_dates_days
import ransel.composeapp.generated.resources.interview_dates_help
import ransel.composeapp.generated.resources.interview_dates_label
import ransel.composeapp.generated.resources.interview_dates_pick
import ransel.composeapp.generated.resources.interview_dates_undecided
import ransel.composeapp.generated.resources.interview_dates_undecided_hint
import ransel.composeapp.generated.resources.interview_departure_bus
import ransel.composeapp.generated.resources.interview_departure_car
import ransel.composeapp.generated.resources.interview_departure_effect_other
import ransel.composeapp.generated.resources.interview_departure_effect_own
import ransel.composeapp.generated.resources.interview_departure_ferry
import ransel.composeapp.generated.resources.interview_departure_motorcycle
import ransel.composeapp.generated.resources.interview_departure_plane
import ransel.composeapp.generated.resources.interview_departure_shuttle
import ransel.composeapp.generated.resources.interview_departure_subtitle
import ransel.composeapp.generated.resources.interview_departure_subtitle_island
import ransel.composeapp.generated.resources.interview_departure_subtitle_km
import ransel.composeapp.generated.resources.interview_departure_title
import ransel.composeapp.generated.resources.interview_departure_train
import ransel.composeapp.generated.resources.interview_destination_hint
import ransel.composeapp.generated.resources.interview_destination_title
import ransel.composeapp.generated.resources.interview_local_public
import ransel.composeapp.generated.resources.interview_local_public_effect
import ransel.composeapp.generated.resources.interview_local_rent_car
import ransel.composeapp.generated.resources.interview_local_rent_car_effect
import ransel.composeapp.generated.resources.interview_local_rent_motorcycle
import ransel.composeapp.generated.resources.interview_local_rent_motorcycle_effect
import ransel.composeapp.generated.resources.interview_local_ride_hailing
import ransel.composeapp.generated.resources.interview_local_ride_hailing_effect
import ransel.composeapp.generated.resources.interview_local_subtitle
import ransel.composeapp.generated.resources.interview_local_taxi
import ransel.composeapp.generated.resources.interview_local_taxi_effect
import ransel.composeapp.generated.resources.interview_local_title
import ransel.composeapp.generated.resources.interview_local_unknown
import ransel.composeapp.generated.resources.interview_local_unknown_effect
import ransel.composeapp.generated.resources.interview_origin_last
import ransel.composeapp.generated.resources.interview_origin_last_effect
import ransel.composeapp.generated.resources.interview_origin_picked
import ransel.composeapp.generated.resources.interview_origin_search
import ransel.composeapp.generated.resources.interview_origin_search_effect
import ransel.composeapp.generated.resources.interview_origin_subtitle
import ransel.composeapp.generated.resources.interview_origin_title
import ransel.composeapp.generated.resources.interview_pick_one
import ransel.composeapp.generated.resources.interview_taste_general
import ransel.composeapp.generated.resources.interview_taste_loading
import ransel.composeapp.generated.resources.interview_taste_style
import ransel.composeapp.generated.resources.interview_taste_subtitle
import ransel.composeapp.generated.resources.interview_taste_title
import ransel.composeapp.generated.resources.explore_style_packed
import ransel.composeapp.generated.resources.explore_style_relaxed

private val ScreenPadding = Modifier.fillMaxWidth().padding(horizontal = 16.dp)

internal val DepartureMode.labelRes: StringResource
    get() = when (this) {
        DepartureMode.OWN_MOTORCYCLE -> Res.string.interview_departure_motorcycle
        DepartureMode.OWN_CAR -> Res.string.interview_departure_car
        DepartureMode.TRAVEL_SHUTTLE -> Res.string.interview_departure_shuttle
        DepartureMode.BUS -> Res.string.interview_departure_bus
        DepartureMode.TRAIN -> Res.string.interview_departure_train
        DepartureMode.PLANE -> Res.string.interview_departure_plane
        DepartureMode.FERRY -> Res.string.interview_departure_ferry
    }

/** The icon subset has no motorbike, bus or train: these are the nearest symbols. */
internal val DepartureMode.symbol: String
    get() = when (this) {
        DepartureMode.OWN_MOTORCYCLE -> Symbols.explore
        DepartureMode.OWN_CAR -> Symbols.directionsCar
        DepartureMode.TRAVEL_SHUTTLE -> Symbols.groupAdd
        DepartureMode.BUS -> Symbols.luggage
        DepartureMode.TRAIN -> Symbols.schedule
        DepartureMode.PLANE -> Symbols.flight
        DepartureMode.FERRY -> Symbols.directionsBoat
    }

internal val LocalTransport.labelRes: StringResource
    get() = when (this) {
        LocalTransport.RENT_MOTORCYCLE -> Res.string.interview_local_rent_motorcycle
        LocalTransport.RENT_CAR -> Res.string.interview_local_rent_car
        LocalTransport.RIDE_HAILING -> Res.string.interview_local_ride_hailing
        LocalTransport.PUBLIC_TRANSPORT -> Res.string.interview_local_public
        LocalTransport.TAXI -> Res.string.interview_local_taxi
        LocalTransport.UNKNOWN -> Res.string.interview_local_unknown
    }

internal val LocalTransport.effectRes: StringResource
    get() = when (this) {
        LocalTransport.RENT_MOTORCYCLE -> Res.string.interview_local_rent_motorcycle_effect
        LocalTransport.RENT_CAR -> Res.string.interview_local_rent_car_effect
        LocalTransport.RIDE_HAILING -> Res.string.interview_local_ride_hailing_effect
        LocalTransport.PUBLIC_TRANSPORT -> Res.string.interview_local_public_effect
        LocalTransport.TAXI -> Res.string.interview_local_taxi_effect
        LocalTransport.UNKNOWN -> Res.string.interview_local_unknown_effect
    }

internal val LocalTransport.symbol: String
    get() = when (this) {
        LocalTransport.RENT_MOTORCYCLE -> Symbols.explore
        LocalTransport.RENT_CAR -> Symbols.directionsCar
        LocalTransport.RIDE_HAILING -> Symbols.person
        LocalTransport.PUBLIC_TRANSPORT -> Symbols.map
        LocalTransport.TAXI -> Symbols.directionsCar
        LocalTransport.UNKNOWN -> Symbols.help
    }

private val TravelStyle.labelRes: StringResource
    get() = when (this) {
        TravelStyle.SANTAI -> Res.string.explore_style_relaxed
        TravelStyle.PADAT -> Res.string.explore_style_packed
    }

private val TravelStyle.hintRes: StringResource
    get() = when (this) {
        TravelStyle.SANTAI -> Res.string.explore_style_relaxed_hint
        TravelStyle.PADAT -> Res.string.explore_style_packed_hint
    }

// ── Layar 1 ──────────────────────────────────────────────────────────────────

/** "Mau ke mana?": the destination (required), then the dates or "Tanggal belum pasti". Lanjut waits for both. */
@Composable
internal fun DestinationQuestion(
    state: InterviewUiState,
    onDestination: (DestinationChoice) -> Unit,
    onDates: (kotlinx.datetime.LocalDate, kotlinx.datetime.LocalDate) -> Unit,
    onUndecided: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val answers = state.answers
    val formatter = rememberDateFormatter()
    var showDates by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuestionTitle(stringResource(Res.string.interview_destination_title), null)
        DestinationField(
            label = "",
            value = state.destinationName,
            onPicked = onDestination,
            modifier = ScreenPadding,
            picked = state.destinationChoice?.place != null,
            placeholder = stringResource(Res.string.interview_destination_hint),
        )
        if (state.destinationChoice != null || answers.destination != null) {
            if (!answers.datesUndecided) {
                Column(ScreenPadding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.interview_dates_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(IrisShape.md).border(1.dp, scheme.outline, IrisShape.md)
                            .clickable(role = Role.Button) { showDates = true }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Symbol(Symbols.calendarMonth, size = 20.dp, tint = scheme.onSurfaceVariant)
                        val start = answers.startDate
                        val end = answers.endDate
                        Text(
                            if (start != null && end != null) formatter.range(start, end) else stringResource(Res.string.interview_dates_pick),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (start != null) scheme.onSurface else scheme.onSurfaceVariant,
                        )
                        if (start != null && end != null) {
                            Text(
                                stringResource(Res.string.interview_dates_days, (end.toEpochDays() - start.toEpochDays() + 1).toInt()),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!answers.hasDates) {
                        Text(stringResource(Res.string.interview_dates_help), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Row(ScreenPadding.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.interview_dates_undecided), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    if (answers.datesUndecided) {
                        Text(stringResource(Res.string.interview_dates_undecided_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                Switch(checked = answers.datesUndecided, onCheckedChange = onUndecided)
            }
            if (answers.destination != null && (answers.hasDates || answers.datesUndecided)) {
                Row(
                    ScreenPadding,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symbol(Symbols.info, size = 18.dp, tint = scheme.onSurfaceVariant)
                    Text(stringResource(Res.string.interview_after_note), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showDates) {
        TripDateRangePicker(
            start = answers.startDate,
            end = answers.endDate,
            onConfirm = { s, e ->
                onDates(s, e)
                showDates = false
            },
            onDismiss = { showDates = false },
            // no day before today (PRD "Trip dates")
            earliest = today(),
        )
    }
}

// ── W1 ───────────────────────────────────────────────────────────────────────

/** "Berangkat dari mana?": the last city, or search one (the picker also offers the current location, which is not saved). */
@Composable
internal fun OriginQuestion(
    state: InterviewUiState,
    onPickLast: (TripOriginDto) -> Unit,
    onPicked: (DestinationChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by remember { mutableStateOf(false) }
    val origin = state.answers.origin
    val last = state.lastOrigin
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuestionTitle(stringResource(Res.string.interview_origin_title), stringResource(Res.string.interview_origin_subtitle))
        Column(ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (last != null) {
                ChoiceRow(Symbols.schedule, last.name, stringResource(Res.string.interview_origin_last_effect), selected = origin == last, onClick = { onPickLast(last) })
            }
            if (origin != null && origin != last) {
                ChoiceRow(Symbols.locationOn, origin.name, stringResource(Res.string.interview_origin_picked), selected = true, onClick = { picker = true })
            }
            ChoiceRow(
                Symbols.search,
                stringResource(Res.string.interview_origin_search),
                stringResource(Res.string.interview_origin_search_effect),
                selected = false,
                onClick = { picker = true },
            )
        }
    }
    if (picker) {
        DestinationPicker(
            onPicked = {
                picker = false
                onPicked(it)
            },
            onDismiss = { picker = false },
        )
    }
}

// ── W2 ───────────────────────────────────────────────────────────────────────

/** "Ke {destinasi} naik apa?": the choices follow the distance; an own vehicle says what it changes. */
@Composable
internal fun DepartureQuestion(state: InterviewUiState, onChoose: (DepartureMode) -> Unit, modifier: Modifier = Modifier) {
    val answers = state.answers
    val options = answers.departureOptions(state.facts)
    val km = answers.roadDistanceKm()
    val from = answers.origin?.name
    val subtitle = when {
        from != null && answers.departureBand() == DepartureBand.OTHER_ISLAND ->
            stringResource(Res.string.interview_departure_subtitle_island, from, state.destinationName)
        from != null && km != null -> stringResource(Res.string.interview_departure_subtitle_km, from, state.destinationName, km)
        else -> stringResource(Res.string.interview_departure_subtitle)
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuestionTitle(stringResource(Res.string.interview_departure_title, state.destinationName), subtitle)
        Column(ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { mode ->
                ChoiceRow(
                    mode.symbol,
                    stringResource(mode.labelRes),
                    stringResource(if (isOwnVehicle(mode)) Res.string.interview_departure_effect_own else Res.string.interview_departure_effect_other),
                    selected = answers.departure == mode,
                    onClick = { onChoose(mode) },
                )
            }
        }
    }
}

// ── W3 ───────────────────────────────────────────────────────────────────────

/** "Di {destinasi} keliling pakai apa?": the choices follow the destination (a rental city, public transport, abroad). */
@Composable
internal fun LocalTransportQuestion(state: InterviewUiState, onChoose: (LocalTransport) -> Unit, modifier: Modifier = Modifier) {
    val answers = state.answers
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuestionTitle(stringResource(Res.string.interview_local_title, state.destinationName), stringResource(Res.string.interview_local_subtitle))
        Column(ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            answers.localTransportOptions(state.facts).forEach { choice ->
                ChoiceRow(
                    choice.symbol,
                    stringResource(choice.labelRes),
                    stringResource(choice.effectRes),
                    selected = answers.localTransport == choice,
                    onClick = { onChoose(choice) },
                )
            }
        }
    }
}

// ── W4 ───────────────────────────────────────────────────────────────────────

/** "Kamu suka apa di {destinasi}?": the destination's own interests, the general ones and the style. */
@Composable
internal fun TasteQuestion(
    state: InterviewUiState,
    onInterest: (Interest) -> Unit,
    onExtra: (String) -> Unit,
    onStyle: (TravelStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val taste = state.answers.taste
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuestionTitle(stringResource(Res.string.interview_taste_title, state.destinationName), stringResource(Res.string.interview_taste_subtitle))
        when (val khas = state.khas) {
            KhasState.Hidden -> Unit
            KhasState.Loading -> Column(ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.explore_special_title, state.destinationName), style = MaterialTheme.typography.labelLarge)
                ChipFlow { repeat(4) { SkeletonChip(Modifier.width(88.dp)) } }
                Text(stringResource(Res.string.interview_taste_loading, state.destinationName), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            is KhasState.Loaded -> Column(ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.explore_special_title, state.destinationName), style = MaterialTheme.typography.labelLarge)
                ChipFlow { khas.interests.forEach { InterestChip(it, it in taste.extraInterests, { onExtra(it) }, special = true) } }
            }
        }
        Column(ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.interview_taste_general), style = MaterialTheme.typography.labelLarge)
            ChipFlow { Interest.entries.forEach { InterestChip(stringResource(it.label), it in taste.interests, { onInterest(it) }) } }
            if (state.door == app.ransel.shared.domain.InterviewDoor.EXPLORE && !taste.hasInterest) {
                Text(stringResource(Res.string.interview_pick_one), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Column(ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.interview_taste_style), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TravelStyle.entries.forEach { style ->
                    StyleCard(stringResource(style.labelRes), stringResource(style.hintRes), taste.style == style, { onStyle(style) }, Modifier.weight(1f))
                }
            }
        }
    }
}
