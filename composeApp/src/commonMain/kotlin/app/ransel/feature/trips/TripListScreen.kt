package app.ransel.feature.trips

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.TripCover
import app.ransel.ui.format.DateFormatter
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.formatMoneyCompact
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.irisCard
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.capybara_reading_map_compass
import ransel.composeapp.generated.resources.money_unit_billion
import ransel.composeapp.generated.resources.money_unit_million
import ransel.composeapp.generated.resources.money_unit_thousand
import ransel.composeapp.generated.resources.trip_day_of
import ransel.composeapp.generated.resources.trips_create
import ransel.composeapp.generated.resources.trips_empty_body
import ransel.composeapp.generated.resources.trips_empty_title
import ransel.composeapp.generated.resources.trips_finished
import ransel.composeapp.generated.resources.trips_itinerary_empty
import ransel.composeapp.generated.resources.trips_no_upcoming_body
import ransel.composeapp.generated.resources.trips_no_upcoming_title
import ransel.composeapp.generated.resources.trips_ongoing
import ransel.composeapp.generated.resources.trips_packing_progress
import ransel.composeapp.generated.resources.trips_remaining
import ransel.composeapp.generated.resources.trips_see_all
import ransel.composeapp.generated.resources.trips_show_less
import ransel.composeapp.generated.resources.trips_spent
import ransel.composeapp.generated.resources.trips_spent_label
import ransel.composeapp.generated.resources.trips_title
import ransel.composeapp.generated.resources.trips_upcoming

private const val FINISHED_PREVIEW_COUNT = 3

@Composable
fun TripListScreen(
    onOpenTrip: (String) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    TripListContent(state, onOpenTrip, onCreateTrip, modifier)
}

@Composable
fun TripListContent(
    state: TripListUiState,
    onOpenTrip: (String) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(Res.string.trips_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        )
        when (state) {
            TripListUiState.Loading -> Box(Modifier.weight(1f))
            TripListUiState.Empty -> FullScreenState(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.trips_empty_title),
                body = stringResource(Res.string.trips_empty_body),
                primaryLabel = stringResource(Res.string.trips_create),
                onPrimary = onCreateTrip,
                illustration = painterResource(Res.drawable.capybara_reading_map_compass),
            )
            is TripListUiState.Content -> Box(Modifier.weight(1f)) {
                TripGroups(state, onOpenTrip, onCreateTrip)
                CreateTripFab(onCreateTrip, Modifier.align(Alignment.BottomEnd).padding(16.dp))
            }
        }
    }
}

@Composable
private fun TripGroups(state: TripListUiState.Content, onOpenTrip: (String) -> Unit, onCreateTrip: () -> Unit) {
    val dates = rememberDateFormatter()
    var showAllFinished by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.ongoing.isNotEmpty()) {
            Group(stringResource(Res.string.trips_ongoing)) {
                state.ongoing.forEach { OngoingCard(it, state, dates) { onOpenTrip(it.trip.id) } }
            }
        }
        if (state.upcoming.isNotEmpty()) {
            Group(stringResource(Res.string.trips_upcoming)) {
                state.upcoming.forEach { UpcomingCard(it, state, dates) { onOpenTrip(it.trip.id) } }
            }
        } else if (state.ongoing.isEmpty() && state.finished.isNotEmpty()) {
            NoUpcomingCard(state.finished.first(), onCreateTrip)
        }
        if (state.finished.isNotEmpty()) {
            val visible = if (showAllFinished) state.finished else state.finished.take(FINISHED_PREVIEW_COUNT)
            Column {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.trips_finished),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.finished.size > FINISHED_PREVIEW_COUNT) {
                        Text(
                            if (showAllFinished) {
                                stringResource(Res.string.trips_show_less)
                            } else {
                                stringResource(Res.string.trips_see_all, state.finished.size)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { showAllFinished = !showAllFinished }
                                .padding(start = 12.dp)
                                .padding(vertical = 14.dp),
                        )
                    }
                }
                visible.forEachIndexed { index, card ->
                    FinishedRow(card, dates) { onOpenTrip(card.trip.id) }
                    if (index < visible.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun OngoingCard(card: TripCardData, state: TripListUiState.Content, dates: DateFormatter, onClick: () -> Unit) {
    val trip = card.trip
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .irisCard(IrisShape.lg, containerColor = MaterialTheme.colorScheme.primary, edge = false)
            .clip(IrisShape.lg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TripCover(trip.id, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    trip.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(Res.string.trip_day_of, trip.dayNumber(state.today), trip.totalDays()) +
                        " · " + dates.range(trip.startDate, trip.endDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onPrimary,
                )
            }
            Symbol(Symbols.chevronRight, tint = onPrimary)
        }
        val budget = trip.budgetMinor
        if (budget != null && budget > 0) {
            val fraction = (card.summary.spentMinor.toFloat() / budget).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(onPrimary.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape).background(onPrimary))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(Res.string.trips_spent, formatMoney(card.summary.spentMinor, trip.baseCurrency)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = onPrimary,
            )
            card.summary.remainingMinor?.let {
                Text(
                    stringResource(Res.string.trips_remaining, formatMoney(it, trip.baseCurrency)),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = onPrimary,
                )
            }
        }
    }
}

@Composable
private fun UpcomingCard(card: TripCardData, state: TripListUiState.Content, dates: DateFormatter, onClick: () -> Unit) {
    val trip = card.trip
    val soon = trip.daysUntilStart(state.today) < 60
    Row(
        Modifier
            .fillMaxWidth()
            .irisCard(IrisShape.lg)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TripCover(trip.id, Modifier.size(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(trip.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                dates.range(trip.startDate, trip.endDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .height(24.dp)
                        .clip(IrisShape.xs)
                        .background(
                            if (soon) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (soon) {
                        Symbol(
                            Symbols.flightTakeoff, size = 14.dp, filled = true,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        countdownLabel(trip.daysUntilStart(state.today)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (soon) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    if (card.summary.activityCount == 0) {
                        stringResource(Res.string.trips_itinerary_empty)
                    } else {
                        stringResource(
                            Res.string.trips_packing_progress, card.summary.packingPacked, card.summary.packingTotal,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Symbol(Symbols.chevronRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FinishedRow(card: TripCardData, dates: DateFormatter, onClick: () -> Unit) {
    val trip = card.trip
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TripCover(trip.id, Modifier.size(44.dp), radius = 10.dp)
        Column(Modifier.weight(1f)) {
            Text(
                trip.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                dates.range(trip.startDate, trip.endDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatMoneyCompact(
                    card.summary.spentMinor, trip.baseCurrency,
                    thousand = stringResource(Res.string.money_unit_thousand),
                    million = stringResource(Res.string.money_unit_million),
                    billion = stringResource(Res.string.money_unit_billion),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(Res.string.trips_spent_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shown above "Selesai" when every trip is finished (design: dashed card). */
@Composable
private fun NoUpcomingCard(last: TripCardData, onCreateTrip: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), IrisShape.lg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.flightTakeoff, filled = true, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(
            stringResource(Res.string.trips_no_upcoming_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(Res.string.trips_no_upcoming_body, last.trip.destination),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onCreateTrip, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.trips_create), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CreateTripFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f))
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(Symbols.add, tint = MaterialTheme.colorScheme.onPrimary)
        Text(
            stringResource(Res.string.trips_create),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
