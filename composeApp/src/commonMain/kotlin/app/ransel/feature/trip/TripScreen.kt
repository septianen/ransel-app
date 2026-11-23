package app.ransel.feature.trip

import app.ransel.feature.trips.phase
import app.ransel.ui.tour.tourTarget
import app.ransel.ui.RefreshOnReturn
import app.ransel.shared.dto.TripDto
import ransel.composeapp.generated.resources.trip_gone_body
import ransel.composeapp.generated.resources.trip_gone_title
import app.ransel.ui.components.GoneState
import kotlinx.coroutines.flow.map
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
    /**
     * The swipe screen of this trip: after "Cari tempat" in the sheet stored a new session, or straight back into an
     * unfinished one (banner "Lanjutkan cari tempat", the + row, the itinerary entry), without a new charge.
     */
    onOpenSwipe: () -> Unit,
    onAddActivity: (dayDate: String?) -> Unit,
    onOpenActivity: (itemId: String) -> Unit,
    /** "Isi lokasi" on the day's mini map: the activity's form with the place picker open. */
    onFillLocation: ((itemId: String) -> Unit)? = null,
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
    /** "Album trip"; add = opened from "Tambah album". */
    onOpenAlbums: (add: Boolean) -> Unit = {},
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
    // null until the repository answers; its trip is null when the trip is gone (deleted, or the user left it)
    val answer by remember(tripId) { trips.observeTrip(tripId).map { TripAnswer(it) } }.collectAsState(initial = null)
    val trip = answer?.trip
    // An unfinished swipe session of this trip: its dot sits on "Cari inspirasi dengan AI" (PRD v0.8, not the tab icon)
    val exploreSessions by koinInject<app.ransel.data.explore.ExploreSessionStore>().sessions.collectAsState()
    val explorePending = exploreSessions[tripId]?.let { !it.finished } == true
    // "Cari tempat" (plan explore-trip-one-tap v4): the Seleramu sheet filled in from the trip, then the swipe
    var exploreSheetOpen by rememberSaveable { mutableStateOf(false) }
    fun startExplore() {
        when {
            explorePending -> onOpenSwipe()
            // nothing to search yet: the owner sets it in Edit trip (the + row is dimmed for everyone)
            trip?.destination.isNullOrBlank() -> if (trip?.myRole == app.ransel.shared.model.Role.OWNER) onEditTrip()
            else -> exploreSheetOpen = true
        }
    }
    // the Explore trip picker, "Ubah preferensi" on the swipe screen and old form links ask for the sheet
    val exploreRequests: app.ransel.data.explore.TripExploreRequests = koinInject()
    val exploreRequest by exploreRequests.tripId.collectAsState()
    LaunchedEffect(exploreRequest, trip != null) {
        if (trip != null && exploreRequests.consume(tripId)) exploreSheetOpen = true
    }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    // New-user tour: the first trip ever opened (tabs, then +), and a tip the first time each tab opens
    val tour: app.ransel.ui.tour.TourController = koinInject()
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
    // the effect above runs again when the screen comes back from another; this also covers the app coming to the front
    val refreshScope = rememberCoroutineScope()
    RefreshOnReturn { if (signedIn) refreshScope.launch { voting.refresh(api, tripId) } }
    val tripRounds = if (signedIn) rounds[tripId].orEmpty() else emptyList()
    val isOwner = current?.myRole == app.ransel.shared.model.Role.OWNER
    val actionRounds = tripRounds.actionRounds(isOwner)
    // The + button of the Group tab: Buat polling / Ajak anggota / Unggah dokumen / Tambah album.
    var actionSheetOpen by remember { mutableStateOf(false) }
    var inviteSheetOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var kindSheetOpen by remember { mutableStateOf(false) }
    // The OWNER and EDITORs ask the group; viewers vote.
    val canAsk = current?.myRole?.atLeast(app.ransel.shared.model.Role.EDITOR) == true
    // A Seleramu kept only on this phone (made before the server kept it) goes to the server once, so every member and
    // device starts "Cari tempat" from it.
    val tasteRepository: app.ransel.data.explore.TripTasteRepository = koinInject()
    val exploreStore: app.ransel.data.explore.ExploreSessionStore = koinInject()
    // an item made from an idea: its card reaches the server once the trip has (with the region's point for the map)
    val itineraryIdeas: app.ransel.data.explore.ItineraryIdeas = koinInject()
    LaunchedEffect(tripId, signedIn, canAsk) {
        if (!signedIn || !canAsk) return@LaunchedEffect
        repeat(IDEA_UPLOAD_TRIES) {
            if (!itineraryIdeas.hasPending(tripId) || itineraryIdeas.push(tripId)) return@LaunchedEffect
            kotlinx.coroutines.delay(IDEA_UPLOAD_PAUSE_MILLIS)
        }
    }
    LaunchedEffect(tripId, signedIn, canAsk, current != null) {
        val t = current ?: return@LaunchedEffect
        if (signedIn && canAsk && exploreStore.taste(tripId) != null) tasteRepository.load(t, canEdit = true)
    }

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

    val loaded = current != null
    LaunchedEffect(loaded, tab) {
        if (!loaded) return@LaunchedEffect
        when (tab) {
            TripTab.OVERVIEW -> tour.start(app.ransel.ui.tour.TourId.TRIP, app.ransel.ui.tour.tripTour)
            TripTab.ITINERARY -> tour.start(app.ransel.ui.tour.TourId.ITINERARY, app.ransel.ui.tour.itineraryTip)
            TripTab.PACKING -> tour.start(app.ransel.ui.tour.TourId.PACKING, app.ransel.ui.tour.packingTip)
            TripTab.EXPENSES -> tour.start(app.ransel.ui.tour.TourId.EXPENSES, app.ransel.ui.tour.expensesTip)
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
                            onOpenAlbums = { onOpenAlbums(false) },
                            onInvite = if (isOwner) ({ inviteSheetOpen = true }) else null,
                            onStartDatePoll = if (canAsk) ({ onCreatePoll(PollKind.DATE) }) else null,
                            refreshKey = refreshCount,
                            exploreBanner = app.ransel.feature.explore.tripExploreBanner(
                                signedIn, current.myRole, current.phase(app.ransel.util.today()), current.destination, explorePending,
                            ),
                            onExploreBanner = {
                                if (current.destination.isBlank()) onEditTrip() else startExplore()
                            },
                        )
                        TripTab.ITINERARY -> ItineraryTab(
                            current, onAddActivity, onOpenActivity,
                            onFillLocation = onFillLocation,
                            onExplore = if (signedIn) ({ startExplore() }) else null,
                            explorePending = explorePending,
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
                            onManageDocuments = { onOpenDocuments(false) },
                        )
                    }
                } else if (answer != null) {
                    GoneState(
                        title = stringResource(Res.string.trip_gone_title),
                        body = stringResource(Res.string.trip_gone_body),
                        onBack = onBack,
                    )
                }
            }
            if (tab == TripTab.OVERVIEW && signedIn && canAsk) {
                IconFab(
                    Symbols.add,
                    label = stringResource(Res.string.group_fab_description),
                    onClick = { actionSheetOpen = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        .tourTarget(app.ransel.ui.tour.TourTarget.TRIP_ADD, cornerRadius = 16.dp, onClick = { actionSheetOpen = true }),
                )
            }
            }
        }
    }

    if (actionSheetOpen) {
        // a solo trip has nobody to ask yet: no "Buat polling" until someone joins
        val solo = signedIn && koinViewModel<app.ransel.feature.group.GroupViewModel>(key = "group-$tripId") { parametersOf(tripId) }
            .state.collectAsState().value.solo
        val phase = current?.phase(app.ransel.util.today())
        GroupActionSheet(
            canInvite = phase != null && app.ransel.feature.group.canInviteFromSheet(isOwner, phase),
            onPoll = if (phase != null && app.ransel.feature.group.canCreatePollFromSheet(solo, phase)) ({
                actionSheetOpen = false; kindSheetOpen = true
            }) else null,
            onInvite = { actionSheetOpen = false; inviteSheetOpen = true },
            onDismiss = { actionSheetOpen = false },
            onDocument = if (current?.myRole?.atLeast(app.ransel.shared.model.Role.EDITOR) == true) ({ actionSheetOpen = false; onOpenDocuments(true) }) else null,
            onAlbum = if (current?.myRole?.atLeast(app.ransel.shared.model.Role.EDITOR) == true) ({ actionSheetOpen = false; onOpenAlbums(true) }) else null,
            // the trip-first search for this trip (preferences, then swipe; 2+ members vote on the cards, voting spec)
            onExplore = if (phase != null && app.ransel.feature.group.canSearchPlacesFromSheet(current?.myRole, phase)) ({
                actionSheetOpen = false
                startExplore()
            }) else null,
            exploreSolo = solo,
            // the search price from the server; nothing while it is unknown or when the unfinished session resumes for free
            explorePrice = app.ransel.feature.group.explorePrice(app.ransel.feature.credits.rememberCredits().value, resumes = explorePending),
            exploreBlocked = current?.destination.isNullOrBlank(),
        )
    }
    if (exploreSheetOpen) {
        app.ransel.feature.explore.TripExploreSheet(
            tripId = tripId,
            onSearch = { exploreSheetOpen = false; onOpenSwipe() },
            onDismiss = { exploreSheetOpen = false },
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

private class TripAnswer(val trip: TripDto?)

/** A new trip reaches the server a few seconds after "Buat trip": the idea card upload tries again for about a minute. */
private const val IDEA_UPLOAD_TRIES = 12
private const val IDEA_UPLOAD_PAUSE_MILLIS = 5_000L
