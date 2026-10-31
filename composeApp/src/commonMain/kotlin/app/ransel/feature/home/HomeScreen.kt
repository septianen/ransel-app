package app.ransel.feature.home

import androidx.compose.foundation.background
import app.ransel.content.stringResource
import org.koin.compose.koinInject
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.auth.SessionState
import app.ransel.data.auth.SessionManager
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.border
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.ic_data_not_found
import ransel.composeapp.generated.resources.greeting_afternoon
import ransel.composeapp.generated.resources.greeting_evening
import ransel.composeapp.generated.resources.greeting_morning
import ransel.composeapp.generated.resources.greeting_night
import ransel.composeapp.generated.resources.home_empty_action
import ransel.composeapp.generated.resources.home_empty_body
import ransel.composeapp.generated.resources.home_empty_title
import ransel.composeapp.generated.resources.home_prep_days_filled
import ransel.composeapp.generated.resources.home_prep_ideas
import ransel.composeapp.generated.resources.home_prep_items_ready
import ransel.composeapp.generated.resources.home_explore_body
import ransel.composeapp.generated.resources.home_explore_cta
import ransel.composeapp.generated.resources.home_explore_title
import ransel.composeapp.generated.resources.home_prep_title
import ransel.composeapp.generated.resources.home_prep_spent_of
import ransel.composeapp.generated.resources.money_unit_billion
import ransel.composeapp.generated.resources.money_unit_million
import ransel.composeapp.generated.resources.money_unit_thousand
import ransel.composeapp.generated.resources.trips_spent_label
import app.ransel.ui.format.formatMoneyCompact
import ransel.composeapp.generated.resources.home_title
import ransel.composeapp.generated.resources.notification_bell
import ransel.composeapp.generated.resources.notifications_unread_badge
import ransel.composeapp.generated.resources.home_title_empty
import ransel.composeapp.generated.resources.home_trip_meta_day1
import ransel.composeapp.generated.resources.trip_cover_description
import ransel.composeapp.generated.resources.trip_day_of
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Which part of the trip a Home shortcut opens. */
enum class TripSection { OVERVIEW, ITINERARY, PACKING, EXPENSES }

@Composable
fun HomeScreen(
    onOpenTrip: (tripId: String, section: TripSection) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier,
    onExplore: (() -> Unit)? = null,
    onOpenNotifications: () -> Unit = {},
    onOpenRecap: (tripId: String) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // The bell is only for signed-in users; its badge is the unread count of the inbox.
    val session: SessionManager = koinInject()
    val signedIn = session.state.collectAsState().value is SessionState.SignedIn
    val inbox by koinInject<NotificationsStore>().inbox.collectAsState()
    val recapTrip by viewModel.recapTrip.collectAsState()
    val recap: (@Composable () -> Unit)? = recapTrip?.let { trip -> { RecapCardOnHome(trip.id) { onOpenRecap(trip.id) } } }
    HomeContent(state, onOpenTrip, onCreateTrip, modifier, onExplore, unreadCount = if (signedIn) (inbox?.unreadCount ?: 0) else null, onOpenNotifications = onOpenNotifications, recap = recap)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenTrip: (tripId: String, section: TripSection) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier,
    onExplore: (() -> Unit)? = null,
    /** Null = no bell (a guest has no notifications). */
    unreadCount: Int? = null,
    onOpenNotifications: () -> Unit = {},
    /** "Rekap siap" after a trip ended. */
    recap: (@Composable () -> Unit)? = null,
) {
    val bell: (@Composable () -> Unit)? = unreadCount?.let { count -> { NotificationBell(count, onOpenNotifications) } }
    when (state) {
        HomeUiState.Loading -> Box(modifier.fillMaxSize())
        HomeUiState.Empty -> Column(modifier.fillMaxSize()) {
            Header(title = stringResource(Res.string.home_title_empty), modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 0.dp), bell = bell)
            recap?.let { Box(Modifier.padding(16.dp)) { it() } }
            FullScreenState(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.home_empty_title),
                body = stringResource(Res.string.home_empty_body),
                primaryLabel = stringResource(Res.string.home_empty_action),
                onPrimary = onCreateTrip,
                illustration = painterResource(Res.drawable.ic_data_not_found),
            )
        }
        is HomeUiState.Content -> HomeTripContent(state, onOpenTrip, modifier, onExplore, bell, recap)
    }
}

@Composable
private fun Header(title: String, modifier: Modifier = Modifier, bell: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
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
        bell?.invoke()
    }
}

/** The bell (design `BerandaNotifikasiBaru`): outlined without news, filled with a `secondary` badge when something is unread. */
@Composable
private fun NotificationBell(unread: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val description = if (unread > 0) stringResource(Res.string.notifications_unread_badge, unread) else stringResource(Res.string.notification_bell)
    Box(
        Modifier
            .size(48.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).border(1.dp, scheme.outlineVariant, CircleShape).background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.notifications, size = 22.dp, filled = unread > 0, contentDescription = description) }
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .clip(CircleShape)
                    .background(scheme.secondary)
                    .border(2.dp, scheme.surface, CircleShape)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unread > 9) "9+" else unread.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = scheme.onSecondary)
            }
        }
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
    onExplore: (() -> Unit)? = null,
    bell: (@Composable () -> Unit)? = null,
    recap: (@Composable () -> Unit)? = null,
) {
    val dates = rememberDateFormatter()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Header(title = stringResource(Res.string.home_title), bell = bell)
        recap?.invoke()
        // the nearest trip opens on the Group tab when it is shared, on the itinerary when it is solo
        FeaturedTripCard(state, dates, onClick = { onOpenTrip(state.trip.id, TripSection.OVERVIEW) }) // Ringkasan is the way into a trip
        val canExplore = onExplore != null && state.trip.myRole.atLeast(app.ransel.shared.model.Role.EDITOR)
        TripPrepCard(state.trip, state.summary, onOpen = { onOpenTrip(state.trip.id, it) })
        if (canExplore && onExplore != null) ExploreCallout(state.trip.destination, onTry = onExplore)
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
            TripCover(seed = trip.id, modifier = Modifier.fillMaxSize(), radius = 0.dp, coverUrl = trip.coverUrl)
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
                val range = dates.tripRange(trip.startDate, trip.endDate)
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

/** "Persiapan trip": days filled, packing progress and what was spent against the budget. */
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
            // a trip without dates has no days to fill yet: its place ideas count instead
            val undated = trip.startDate == null || trip.endDate == null
            PrepColumn(
                modifier = Modifier.weight(1f),
                symbol = Symbols.map,
                tile = IrisTheme.accents.teal,
                valueText = if (undated) "${summary.unscheduledCount}" else "${summary.daysFilled}",
                totalText = if (undated) "" else "/${summary.daysTotal}",
                fraction = if (undated) 0f else fractionOf(summary.daysFilled.toLong(), summary.daysTotal.toLong()),
                label = stringResource(if (undated) Res.string.home_prep_ideas else Res.string.home_prep_days_filled),
                onClick = { onOpen(TripSection.ITINERARY) },
            )
            PrepColumn(
                modifier = Modifier.weight(1f),
                symbol = Symbols.checklist,
                tile = IrisTheme.accents.blue,
                valueText = "${summary.packingPacked}",
                totalText = "/${summary.packingTotal}",
                fraction = fractionOf(summary.packingPacked.toLong(), summary.packingTotal.toLong()),
                label = stringResource(Res.string.home_prep_items_ready),
                onClick = { onOpen(TripSection.PACKING) },
            )
            val thousand = stringResource(Res.string.money_unit_thousand)
            val million = stringResource(Res.string.money_unit_million)
            val billion = stringResource(Res.string.money_unit_billion)
            val budget = trip.budgetMinor
            PrepColumn(
                modifier = Modifier.weight(1f),
                symbol = Symbols.payments,
                tile = IrisTheme.accents.orange,
                valueText = formatMoneyCompact(summary.spentMinor, trip.baseCurrency, thousand, million, billion),
                totalText = null,
                fraction = if (budget != null) fractionOf(summary.spentMinor, budget) else 0f,
                // "terpakai dari Rp5 jt" with a budget, plain "terpakai" without one
                label = if (budget != null) {
                    stringResource(Res.string.home_prep_spent_of, formatMoneyCompact(budget, trip.baseCurrency, thousand, million, billion))
                } else {
                    stringResource(Res.string.trips_spent_label)
                },
                onClick = { onOpen(TripSection.EXPENSES) },
            )
        }
    }
}

private fun fractionOf(value: Long, total: Long): Float = if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)

/** "Bingung mau ke mana di Bali?" with a "Coba" button that opens Explore on a new trip. */
@Composable
private fun ExploreCallout(destination: String, onTry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.home_explore_title, destination),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                stringResource(Res.string.home_explore_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        androidx.compose.material3.Button(
            onClick = onTry,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
        ) { Text(stringResource(Res.string.home_explore_cta)) }
    }
}

@Composable
private fun PrepColumn(
    symbol: String,
    tile: Color,
    valueText: String,
    totalText: String?,
    fraction: Float,
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
            // An amount like "Rp1,25 jt" is longer than a count: one size smaller so it stays on one line.
            Text(
                valueText,
                style = if (totalText == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            if (totalText != null) {
                Text(
                    totalText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer6()
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
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

/** The recap card's numbers come from the trip's own recap view model. */
@Composable
private fun RecapCardOnHome(tripId: String, onOpen: () -> Unit) {
    val recapVm: app.ransel.feature.recap.RecapViewModel = koinViewModel(key = "recap-$tripId") { org.koin.core.parameter.parametersOf(tripId) }
    val state by recapVm.state.collectAsState()
    val trip = state.trip ?: return
    val recap = state.recap ?: return
    app.ransel.feature.recap.RecapHomeCard(trip.title, trip.baseCurrency, recap, state.openTransfers, onOpen)
}
