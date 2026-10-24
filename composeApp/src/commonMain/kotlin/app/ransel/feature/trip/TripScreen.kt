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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import app.ransel.data.remote.RanselApi
import app.ransel.data.voting.VotingStore
import app.ransel.data.voting.actionRounds
import app.ransel.feature.voting.VoteBanner

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
    onExplore: () -> Unit,
    onAddActivity: (dayDate: String?) -> Unit,
    onEditActivity: (itemId: String) -> Unit,
    onOpenPlaceDetail: ((placeId: String, name: String, currency: String) -> Unit)? = null,
    /** Opens the list of all voting rounds of this trip, the one place the banner leads to. */
    onOpenVoteList: (() -> Unit)? = null,
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
    // Voting rounds are server data too (docs/specs/voting-spec.md): the banner shows for signed-in users only.
    val voting: VotingStore = koinInject()
    val api: RanselApi = koinInject()
    val rounds by voting.rounds.collectAsState()
    LaunchedEffect(tripId, signedIn) { if (signedIn) voting.refresh(api, tripId) }
    val tripRounds = if (signedIn) rounds[tripId].orEmpty() else emptyList()

    // Pull to refresh (signed in only: a guest's trip lives on this phone alone). Mobile syncs, web fetches again;
    // then the server-only parts (voting rounds, members and balances in Expenses) are asked for again.
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var refreshCount by remember { mutableIntStateOf(0) }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                sync.refresh()
                voting.refresh(api, tripId)
                refreshCount++
            } finally {
                refreshing = false
            }
        }
    }

    @Composable
    fun Main(modifier: Modifier) {
        Column(modifier) {
            TripHeader(
                title = current?.title.orEmpty(),
                dates = current?.let { dates.range(it.startDate, it.endDate) }.orEmpty(),
                selectedTab = tab,
                onTabSelected = { tab = it },
                onBack = onBack,
                onEdit = if (current?.myRole == app.ransel.shared.model.Role.OWNER) onEditTrip else null,
                onMembers = if (signedIn) onMembers else null,
            )
            if (offline) app.ransel.ui.components.OfflineBanner()
            // One banner: the round that needs the user (vote, or decide for the owner), else a quiet link to the list.
            if (tripRounds.isNotEmpty() && current != null && onOpenVoteList != null) {
                val isOwner = current.myRole == app.ransel.shared.model.Role.OWNER
                val actions = tripRounds.actionRounds(isOwner)
                VoteBanner(
                    round = actions.firstOrNull(),
                    roundCount = tripRounds.size,
                    moreActions = (actions.size - 1).coerceAtLeast(0),
                    destination = current.destination,
                    isOwner = isOwner,
                    myUserId = session.user?.id,
                    onOpen = onOpenVoteList,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            PullToRefresh(
                enabled = signedIn,
                refreshing = refreshing,
                onRefresh = { refresh() },
                modifier = Modifier.weight(1f).navigationBarsPadding(),
            ) {
                if (current != null) {
                    when (tab) {
                        TripTab.ITINERARY -> ItineraryTab(
                            current, onAddActivity, onEditActivity,
                            onExplore = if (signedIn) onExplore else null,
                            // The place text lives on the server: guests have no account to ask it with.
                            onOpenPlaceDetail = if (signedIn) onOpenPlaceDetail else null,
                        )
                        TripTab.PACKING -> PackingTab(current)
                        TripTab.EXPENSES -> ExpensesTab(current, refreshKey = refreshCount)
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

/** Pull-to-refresh around a tab; disabled (guests) it is a plain box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefresh(
    enabled: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier) { content() }
    } else {
        Box(modifier) { content() }
    }
}
