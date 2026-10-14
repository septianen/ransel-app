package app.ransel.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.feature.trips.TripPhase
import app.ransel.feature.trips.countdownLabel
import app.ransel.feature.trips.dayNumber
import app.ransel.feature.trips.daysUntilStart
import app.ransel.feature.trips.phase
import app.ransel.feature.trips.totalDays
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.model.Role
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.TripCover
import app.ransel.ui.format.DateFormatter
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.irisCard
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.greeting_afternoon
import ransel.composeapp.generated.resources.greeting_evening
import ransel.composeapp.generated.resources.greeting_morning
import ransel.composeapp.generated.resources.greeting_night
import ransel.composeapp.generated.resources.home_empty_action
import ransel.composeapp.generated.resources.home_empty_body
import ransel.composeapp.generated.resources.home_empty_title
import ransel.composeapp.generated.resources.home_prep_days_filled
import ransel.composeapp.generated.resources.home_prep_items_ready
import ransel.composeapp.generated.resources.home_prep_title
import ransel.composeapp.generated.resources.home_title
import ransel.composeapp.generated.resources.home_title_empty
import ransel.composeapp.generated.resources.home_trip_meta_day1
import ransel.composeapp.generated.resources.trip_cover_description
import ransel.composeapp.generated.resources.trip_day_of
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Which part of the trip a Home shortcut opens. */
enum class TripSection { ITINERARY, PACKING, EXPENSES }

@Composable
fun HomeScreen(
    onOpenTrip: (tripId: String, section: TripSection) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    HomeContent(state, onOpenTrip, onCreateTrip, modifier)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenTrip: (tripId: String, section: TripSection) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HomeUiState.Loading -> Box(modifier.fillMaxSize())
        HomeUiState.Empty -> Column(modifier.fillMaxSize()) {
            Header(title = stringResource(Res.string.home_title_empty), modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 0.dp))
            FullScreenState(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.home_empty_title),
                body = stringResource(Res.string.home_empty_body),
                primaryLabel = stringResource(Res.string.home_empty_action),
                onPrimary = onCreateTrip,
                illustrationSize = 200.dp,
            )
        }
        is HomeUiState.Content -> HomeTripContent(state, onOpenTrip, modifier)
    }
}

@Composable
private fun Header(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            greeting(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun greeting(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (hour) {
        in 4..10 -> stringResource(Res.string.greeting_morning)
        in 11..14 -> stringResource(Res.string.greeting_afternoon)
        in 15..17 -> stringResource(Res.string.greeting_evening)
        else -> stringResource(Res.string.greeting_night)
    }
}

@Composable
private fun HomeTripContent(
    state: HomeUiState.Content,
    onOpenTrip: (String, TripSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Header(title = stringResource(Res.string.home_title))
        FeaturedTripCard(state, dates, onClick = { onOpenTrip(state.trip.id, TripSection.ITINERARY) })
        TripPrepCard(state.trip, state.summary, onOpen = { onOpenTrip(state.trip.id, it) })
    }
}

@Composable
private fun FeaturedTripCard(state: HomeUiState.Content, dates: DateFormatter, onClick: () -> Unit) {
    val trip = state.trip
    val phase = trip.phase(state.today)
    Column(
        Modifier
            .fillMaxWidth()
            .irisCard(RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            TripCover(seed = trip.id, modifier = Modifier.fillMaxSize(), radius = 0.dp)
            val badge = when (phase) {
                TripPhase.UPCOMING -> countdownLabel(trip.daysUntilStart(state.today))
                TripPhase.ONGOING -> stringResource(Res.string.trip_day_of, trip.dayNumber(state.today), trip.totalDays())
                TripPhase.FINISHED -> null
            }
            if (badge != null) {
                Row(
                    Modifier
                        .padding(16.dp)
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Symbol(Symbols.flightTakeoff, size = 16.dp, filled = true, tint = MaterialTheme.colorScheme.primary)
                    Text(badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    trip.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val range = dates.range(trip.startDate, trip.endDate)
                Text(
                    if (state.firstDayCount > 0) {
                        stringResource(Res.string.home_trip_meta_day1, range, state.firstDayCount)
                    } else {
                        range
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Symbol(Symbols.chevronRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Persiapan trip": days filled and packing progress. The unscheduled column arrives with Explore (Phase 5). */
@Composable
private fun TripPrepCard(trip: TripDto, summary: TripSummaryDto, onOpen: (TripSection) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .irisCard(IrisShape.lg)
            .padding(vertical = 16.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(Res.string.home_prep_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                trip.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row {
            PrepColumn(
                modifier = Modifier.weight(1f),
                symbol = Symbols.map,
                tile = IrisTheme.accents.teal,
                value = summary.daysFilled,
                total = summary.daysTotal,
                label = stringResource(Res.string.home_prep_days_filled),
                onClick = { onOpen(TripSection.ITINERARY) },
            )
            PrepColumn(
                modifier = Modifier.weight(1f),
                symbol = Symbols.checklist,
                tile = IrisTheme.accents.blue,
                value = summary.packingPacked,
                total = summary.packingTotal,
                label = stringResource(Res.string.home_prep_items_ready),
                onClick = { onOpen(TripSection.PACKING) },
            )
        }
    }
}

@Composable
private fun PrepColumn(
    symbol: String,
    tile: Color,
    value: Int,
    total: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(tile),
            contentAlignment = Alignment.Center,
        ) { Symbol(symbol, size = 18.dp, filled = true, tint = IrisTheme.accents.onAccent) }
        Spacer6()
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "/$total",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer6()
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            val fraction = if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(tile))
        }
    }
}

@Composable
private fun Spacer6() = Box(Modifier.height(6.dp))

@Preview
@Composable
private fun HomeContentPreview() = IrisPreview {
    val today = LocalDate(2026, 10, 13)
    val trip = TripDto(
        id = "bali", ownerId = "u", title = "Bali 4 hari", destination = "Bali",
        startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
        budgetMinor = 5_000_000, myRole = Role.OWNER,
        createdAt = Instant.fromEpochMilliseconds(0), updatedAt = Instant.fromEpochMilliseconds(0),
    )
    HomeContent(
        state = HomeUiState.Content(
            trip = trip,
            summary = TripSummaryDto(
                daysTotal = 4, daysFilled = 3, activityCount = 8, unscheduledCount = 0, packingTotal = 9,
                packingPacked = 5, expenseCount = 0, spentMinor = 0,
            ),
            firstDayCount = 4,
            today = today,
        ),
        onOpenTrip = { _, _ -> },
        onCreateTrip = {},
    )
}
