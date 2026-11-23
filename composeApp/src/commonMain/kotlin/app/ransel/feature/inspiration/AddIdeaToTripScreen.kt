package app.ransel.feature.inspiration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.getString
import app.ransel.content.stringResource
import app.ransel.feature.trips.TripPhase
import app.ransel.feature.trips.daysUntilStart
import app.ransel.feature.trips.phase
import app.ransel.shared.dto.TripDto
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.TripCover
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.today
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.credits_error_generic
import ransel.composeapp.generated.resources.explore_add_to_trip_empty_body
import ransel.composeapp.generated.resources.explore_add_to_trip_empty_title
import ransel.composeapp.generated.resources.explore_add_to_trip_snackbar
import ransel.composeapp.generated.resources.explore_add_to_trip_title
import ransel.composeapp.generated.resources.explore_picker_hidden_note
import ransel.composeapp.generated.resources.explore_picker_in_days
import ransel.composeapp.generated.resources.explore_picker_your_trips
import ransel.composeapp.generated.resources.ic_data_not_found
import ransel.composeapp.generated.resources.swipe_view_trip
import ransel.composeapp.generated.resources.trips_ongoing
import kotlin.time.Instant

@Composable
fun AddIdeaToTripScreen(
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddIdeaToTripViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is AddIdeaToTripEvent.Added -> {
                    val result = snackbar.showSnackbar(
                        getString(Res.string.explore_add_to_trip_snackbar, event.tripTitle),
                        actionLabel = getString(Res.string.swipe_view_trip),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenTrip(event.tripId)
                }
                AddIdeaToTripEvent.Failed -> snackbar.showSnackbar(getString(Res.string.credits_error_generic))
            }
        }
    }
    AddIdeaToTripContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onPick = viewModel::addTo,
        modifier = modifier,
    )
}

@Composable
fun AddIdeaToTripContent(
    state: AddIdeaToTripState,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    Scaffold(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        containerColor = scheme.background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                UndoSnackbar(data.visuals.message, actionLabel = data.visuals.actionLabel, onAction = { data.performAction() })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                IconButton(onClick = onBack) {
                    Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
                }
                Text(
                    stringResource(Res.string.explore_add_to_trip_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
            if (state.trips.isEmpty()) {
                FullScreenState(
                    title = stringResource(Res.string.explore_add_to_trip_empty_title),
                    body = stringResource(Res.string.explore_add_to_trip_empty_body),
                    illustration = painterResource(Res.drawable.ic_data_not_found),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.explore_picker_your_trips), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    state.trips.forEach { trip ->
                        TripPickRow(
                            trip = trip,
                            range = dates.tripRange(trip.startDate, trip.endDate),
                            enabled = !state.adding,
                            onClick = { onPick(trip.id) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Symbol(Symbols.info, size = 16.dp, tint = scheme.onSurfaceVariant)
                        Text(stringResource(Res.string.explore_picker_hidden_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TripPickRow(trip: TripDto, range: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val today = today()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TripCover(trip.id, Modifier.size(56.dp), coverUrl = trip.coverUrl)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(trip.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(range, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            val days = trip.daysUntilStart(today)
            Text(
                when {
                    trip.phase(today) == TripPhase.ONGOING -> stringResource(Res.string.trips_ongoing)
                    days == null -> app.ransel.feature.trips.countdownLabel(null)
                    else -> pluralStringResource(Res.plurals.explore_picker_in_days, days, days)
                },
                Modifier.clip(IrisShape.xs).background(scheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
    }
}

@Preview
@Composable
private fun AddIdeaToTripPreview() = IrisPreview {
    AddIdeaToTripContent(
        state = AddIdeaToTripState(
            trips = listOf(
                TripDto(
                    id = "t1", ownerId = "u", title = "Bali 4 hari", destination = "Bali",
                    startDate = LocalDate(2026, 12, 1), endDate = LocalDate(2026, 12, 4),
                    baseCurrency = "IDR", createdAt = Instant.parse("2026-11-01T00:00:00Z"), updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
                ),
            ),
            canAdd = true,
        ),
        onBack = {},
        onPick = {},
    )
}
