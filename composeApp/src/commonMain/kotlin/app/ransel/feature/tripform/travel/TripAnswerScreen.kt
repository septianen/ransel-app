package app.ransel.feature.tripform.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.interview.DepartureEdit
import app.ransel.feature.interview.DepartureQuestion
import app.ransel.feature.interview.InterviewBottomBar
import app.ransel.feature.interview.LocalTransportQuestion
import app.ransel.feature.interview.OfflineBanner
import app.ransel.feature.interview.OriginQuestion
import app.ransel.shared.domain.TravelRow
import app.ransel.shared.domain.isOwnVehicle
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.interview_save
import ransel.composeapp.generated.resources.trip_answer_departure_title
import ransel.composeapp.generated.resources.trip_answer_local_title
import ransel.composeapp.generated.resources.trip_answer_origin_title

/**
 * "Kota asal", "Cara berangkat" or "Cara keliling" of Edit trip (spec 1638556, mockups s26-2): the W1-W3 screen of the interview
 * without the indicator and without Lewati, the current answer picked, and "Simpan", which stores the change in the trip at once.
 */
@Composable
fun TripAnswerScreen(
    tripId: String,
    row: TravelRow,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onSavedOpenLocalTransport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripAnswerViewModel = koinViewModel(key = "trip-answer-$tripId-${row.name}") { parametersOf(tripId, row.name) },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                TripAnswerEvent.Done -> onSaved()
                TripAnswerEvent.OpenLocalTransport -> onSavedOpenLocalTransport()
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(scheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back), tint = scheme.onSurface)
            }
            Text(
                stringResource(
                    when (row) {
                        TravelRow.ORIGIN -> Res.string.trip_answer_origin_title
                        TravelRow.DEPARTURE -> Res.string.trip_answer_departure_title
                        else -> Res.string.trip_answer_local_title
                    },
                ),
                Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
            )
        }
        if (state.offline) OfflineBanner()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val trip = state.trip
            if (state.loading || trip == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val interview = state.interviewState()
                when (row) {
                    TravelRow.ORIGIN -> OriginQuestion(
                        interview,
                        onPickLast = viewModel::pickOrigin,
                        onPicked = viewModel::onOriginPicked,
                        onUseLocation = viewModel::useCurrentLocation,
                    )
                    TravelRow.DEPARTURE -> DepartureQuestion(
                        interview, viewModel::chooseDeparture,
                        edit = DepartureEdit(currentIsOwn = isOwnVehicle(trip.departureMode)),
                    )
                    TravelRow.LOCAL_TRANSPORT -> LocalTransportQuestion(interview, viewModel::chooseLocalTransport)
                    TravelRow.TASTE -> Unit
                }
            }
        }
        InterviewBottomBar(
            primaryLabel = stringResource(Res.string.interview_save),
            primaryEnabled = state.hasChoice && !state.saving,
            onPrimary = viewModel::save,
            primaryLoading = state.saving,
        )
    }
}
