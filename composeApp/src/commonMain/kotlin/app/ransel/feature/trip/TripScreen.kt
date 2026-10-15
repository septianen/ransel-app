package app.ransel.feature.trip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.sync.SyncStatus
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.data.repository.TripRepository
import app.ransel.feature.expenses.ExpensesTab
import app.ransel.feature.itinerary.ItineraryTab
import app.ransel.feature.packing.PackingTab
import app.ransel.ui.adaptive.rememberIsExpanded
import app.ransel.ui.components.TripHeader
import app.ransel.ui.components.TripTab
import app.ransel.ui.format.rememberDateFormatter
import org.koin.compose.koinInject

/**
 * Trip detail: shared header + the three tabs. Full screen over the bottom tabs (the designs show
 * no bottom navigation here). Each tab owns its ViewModel, keyed by trip id.
 */
@Composable
fun TripScreen(
    tripId: String,
    initialTab: TripTab,
    onBack: () -> Unit,
    onEditTrip: () -> Unit,
    onMembers: () -> Unit,
    onAddActivity: (dayDate: String?) -> Unit,
    onEditActivity: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    trips: TripRepository = koinInject(),
) {
    val trip by trips.observeTrip(tripId).collectAsState(initial = null)
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val dates = rememberDateFormatter()
    val current = trip

    val expanded = rememberIsExpanded()
    val sync: SyncStatus = koinInject()
    val offline by sync.offline.collectAsState()
    // Members live on the server: only signed-in users see the button (guests have no one to share with).
    val session: SessionManager = koinInject()
    val signedIn = session.state.collectAsState().value is SessionState.SignedIn

    @Composable
    fun Main(modifier: Modifier) {
        Column(modifier) {
            TripHeader(
                title = current?.title.orEmpty(),
                dates = current?.let { dates.range(it.startDate, it.endDate) }.orEmpty(),
                selectedTab = tab,
                onTabSelected = { tab = it },
                onBack = onBack,
                onEdit = onEditTrip,
                onMembers = if (signedIn) onMembers else null,
            )
            if (offline) app.ransel.ui.components.OfflineBanner()
            Box(Modifier.weight(1f).navigationBarsPadding()) {
                if (current != null) {
                    when (tab) {
                        TripTab.ITINERARY -> ItineraryTab(current, onAddActivity, onEditActivity)
                        TripTab.PACKING -> PackingTab(current)
                        TripTab.EXPENSES -> ExpensesTab(current)
                    }
                }
            }
        }
    }

    if (expanded && current != null) {
        // Wide web window: main column + 360 dp side panel (design "Detail trip versi web").
        Row(modifier.fillMaxSize().statusBarsPadding()) {
            Main(Modifier.weight(1f).fillMaxHeight())
            TripSidePanel(
                trip = current,
                onOpenTab = { tab = it },
                modifier = Modifier.width(384.dp),
            )
        }
    } else {
        Main(modifier.fillMaxSize().statusBarsPadding())
    }
}
