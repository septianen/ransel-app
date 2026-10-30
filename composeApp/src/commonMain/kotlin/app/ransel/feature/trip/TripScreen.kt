package app.ransel.feature.trip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import app.ransel.content.stringResource
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
import app.ransel.feature.group.GroupActionSheet
import app.ransel.feature.polls.PollKindSheet
import app.ransel.shared.model.PollKind
import app.ransel.feature.group.GroupTab
import app.ransel.feature.invite.InviteMembersSheet
import app.ransel.ui.components.IconFab
import app.ransel.ui.symbols.Symbols
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.group_fab_description
import app.ransel.feature.itinerary.ItineraryTab
import app.ransel.feature.packing.PackingTab
import app.ransel.ui.adaptive.rememberIsExpanded
import app.ransel.ui.components.TripHeader
import app.ransel.ui.components.TripTab
import app.ransel.ui.format.rememberDateFormatter
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
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
import app.ransel.feature.voting.opensResult

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
    /** Opens one voting round, from "Perlu tindakanmu" or the Group tab's lists (the OWNER sees the result of a closed round). */
    onOpenRound: (roundId: String, showResult: Boolean) -> Unit = { _, _ -> },
    /** "Riwayat voting" on the Group tab. */
    onOpenVotingHistory: () -> Unit = {},
    /** Polls live on their own screens: create one of a kind, or open one. */
    onCreatePoll: (PollKind) -> Unit = {},
    onOpenPoll: (pollId: String) -> Unit = {},
    onOpenPaymentMethods: () -> Unit = {},
    /** "Dokumen trip"; upload = opened from "Unggah dokumen". */
    onOpenDocuments: (upload: Boolean) -> Unit = {},
    onOpenRecap: () -> Unit = {},
    /** "Lihat semua" on the Biaya tab: "Semua transaksi". */
    onOpenAllTransactions: () -> Unit = {},
    /** Back from a payment in "Semua transaksi": the Biaya tab shows Saldo, then [onBalancesOpened] clears it. */
    openBalances: Boolean = false,
    onBalancesOpened: () -> Unit = {},
    /** Opened right after "Buat trip dengan grup ini": the invite sheet comes first. */
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
    // Voting rounds are server data too (docs/specs/voting-spec.md); like polls they only show on the Group tab.
    val voting: VotingStore = koinInject()
    val api: RanselApi = koinInject()
    val rounds by voting.rounds.collectAsState()
    LaunchedEffect(tripId, signedIn) { if (signedIn) voting.refresh(api, tripId) }
    val tripRounds = if (signedIn) rounds[tripId].orEmpty() else emptyList()
    val isOwner = current?.myRole == app.ransel.shared.model.Role.OWNER
    val actionRounds = tripRounds.actionRounds(isOwner)
    // The + button of the Group tab: Buat polling / Ajak anggota / Unggah dokumen.
    var actionSheetOpen by remember { mutableStateOf(false) }
    var inviteSheetOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var kindSheetOpen by remember { mutableStateOf(false) }
    // The OWNER and EDITORs ask the group; viewers vote.
    val canAsk = current?.myRole?.atLeast(app.ransel.shared.model.Role.EDITOR) == true

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
                // abroad or in WITA/WIT the header says how far local time is from WIB
                dates = current?.let { trip ->
                    val zone = trip.timeZone?.takeIf { z -> (app.ransel.shared.domain.minutesFromWib(z, kotlin.time.Clock.System.now()) ?: 0) != 0 }
                    dates.tripRange(trip.startDate, trip.endDate) + (zone?.let { z -> " · " + zoneCity(z) + ", " + zoneDifferenceText(z).orEmpty() } ?: "")
                }.orEmpty(),
                selectedTab = tab,
                onTabSelected = { tab = it },
                onBack = onBack,
                onEdit = if (current?.myRole == app.ransel.shared.model.Role.OWNER) onEditTrip else null,
                onRecap = current?.takeIf { app.ransel.shared.domain.recapAvailable(it.endDate, app.ransel.util.today()) }?.let { { onOpenRecap() } },
            )
            if (offline) app.ransel.ui.components.OfflineBanner()
            Box(Modifier.weight(1f).navigationBarsPadding()) {
            PullToRefresh(
                enabled = signedIn,
                refreshing = refreshing,
                onRefresh = { refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (current != null) {
                    when (tab) {
                        TripTab.OVERVIEW -> GroupTab(
                            onOpenMoney = { tab = TripTab.EXPENSES },
                            trip = current,
                            signedIn = signedIn,
                            hasNeeds = actionRounds.isNotEmpty(),
                            needs = {
                                // one card per round waiting for me; a tap opens its cards (or the owner's decision) at once
                                val now = kotlin.time.Clock.System.now()
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    actionRounds.forEach { round ->
                                        VoteBanner(
                                            round = round,
                                            destination = current.destination,
                                            isOwner = isOwner,
                                            myUserId = session.user?.id,
                                            onOpen = { onOpenRound(round.id, round.opensResult(isOwner, now)) },
                                            now = now,
                                        )
                                    }
                                }
                            },
                            onOpenMembers = onMembers,
                            onOpenPoll = onOpenPoll,
                            rounds = tripRounds,
                            onOpenRound = onOpenRound,
                            onOpenVotingHistory = onOpenVotingHistory,
                            onOpenDocuments = { onOpenDocuments(false) },
                            onInvite = if (isOwner) ({ inviteSheetOpen = true }) else null,
                            onStartDatePoll = if (canAsk) ({ onCreatePoll(PollKind.DATE) }) else null,
                            refreshKey = refreshCount,
                        )
                        TripTab.ITINERARY -> ItineraryTab(
                            current, onAddActivity, onEditActivity,
                            onExplore = if (signedIn) onExplore else null,
                            // The place text lives on the server: guests have no account to ask it with.
                            onOpenPlaceDetail = if (signedIn) onOpenPlaceDetail else null,
                            onOpenPoll = if (signedIn) onOpenPoll else null,
                        )
                        TripTab.PACKING -> PackingTab(current)
                        TripTab.EXPENSES -> ExpensesTab(
                            current,
                            refreshKey = refreshCount,
                            onOpenPaymentMethods = onOpenPaymentMethods,
                            onSeeAllTransactions = onOpenAllTransactions,
                            openBalances = openBalances,
                            onBalancesOpened = onBalancesOpened,
                        )
                    }
                }
            }
            if (tab == TripTab.OVERVIEW && signedIn && canAsk) {
                IconFab(
                    Symbols.add,
                    label = stringResource(Res.string.group_fab_description),
                    onClick = { actionSheetOpen = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
            }
        }
    }

    if (actionSheetOpen) {
        // a solo trip has nobody to ask yet: no "Buat polling" until someone joins
        val solo = signedIn && koinViewModel<app.ransel.feature.group.GroupViewModel>(key = "group-$tripId") { parametersOf(tripId) }
            .state.collectAsState().value.solo
        GroupActionSheet(
            canInvite = isOwner,
            onPoll = if (solo) null else ({ actionSheetOpen = false; kindSheetOpen = true }),
            onInvite = { actionSheetOpen = false; inviteSheetOpen = true },
            onDismiss = { actionSheetOpen = false },
            onDocument = if (current?.myRole?.atLeast(app.ransel.shared.model.Role.EDITOR) == true) ({ actionSheetOpen = false; onOpenDocuments(true) }) else null,
        )
    }
    if (kindSheetOpen) {
        PollKindSheet(onPick = { kindSheetOpen = false; onCreatePoll(it) }, onDismiss = { kindSheetOpen = false })
    }
    if (inviteSheetOpen && current != null) {
        InviteMembersSheet(tripId = tripId, tripTitle = current.title, onDismiss = { inviteSheetOpen = false })
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
