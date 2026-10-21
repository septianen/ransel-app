package app.ransel.feature.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.data.explore.ExploreCard
import app.ransel.feature.explore.IconBox
import app.ransel.feature.explore.SwipeDeck
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.RoundState
import app.ransel.shared.domain.hoursLeft
import app.ransel.shared.dto.RecommendationItem
import app.ransel.shared.dto.RoundDto
import app.ransel.shared.dto.RoundPlaceDto
import app.ransel.shared.dto.TallyDto
import app.ransel.shared.dto.VoterDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.Vote
import app.ransel.ui.category.itineraryLabel
import app.ransel.ui.category.symbol
import app.ransel.ui.category.tileColor
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*

/**
 * One voting round (designs `Voting`, `VotingMenunggu`, `VotingHasil`, `VotingDitutupMenunggu`, `VotingDitutup`).
 * Which one shows follows [VoteUiState.mode]; [showResult] opens the OWNER on the result.
 */
@Composable
fun VoteScreen(
    tripId: String,
    roundId: String,
    showResult: Boolean,
    onClose: () -> Unit,
    onOpenItinerary: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenDetail: (placeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoteViewModel = koinViewModel(key = voteViewModelKey(roundId)) { parametersOf(tripId, roundId, showResult) },
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val message = when (event) {
                is VoteEvent.Decided -> getString(Res.string.vote_decided_snackbar, event.count)
                is VoteEvent.Problem -> getString(
                    if (event.code == ErrorCode.VOTING_CLOSED) Res.string.vote_error_closed else Res.string.vote_error_generic,
                )
            }
            snackbar.showSnackbar(message)
        }
    }
    Box(modifier.fillMaxSize().statusBarsPadding()) {
        VoteContent(
            state = state,
            onClose = onClose,
            onRetry = viewModel::retry,
            onLike = viewModel::like,
            onSkip = viewModel::skip,
            onUndo = viewModel::undo,
            onChangeVotes = viewModel::changeVotes,
            onToggle = viewModel::toggle,
            onDecide = viewModel::decide,
            onStartVoting = viewModel::startVoting,
            onOpenItinerary = onOpenItinerary,
            onOpenMembers = onOpenMembers,
            onOpenDetail = onOpenDetail,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            snackbar = { data -> UndoSnackbar(data.visuals.message, actionLabel = null, onAction = {}) },
        )
    }
}

/** The same key from the vote screen and the place detail, so both talk to one ViewModel. */
fun voteViewModelKey(roundId: String) = "vote-$roundId"

@Composable
fun VoteContent(
    state: VoteUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onLike: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onChangeVotes: () -> Unit,
    onToggle: (placeId: String) -> Unit,
    onDecide: () -> Unit,
    onStartVoting: () -> Unit,
    onOpenItinerary: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenDetail: (placeId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(if (state.mode == VoteMode.WAITING) scheme.surface else scheme.surfaceContainer)) {
        val round = state.round
        when (state.mode) {
            VoteMode.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            VoteMode.ERROR -> {
                Header(stringResource(Res.string.vote_result_title), null, Symbols.close, onClose)
                val failure = state.failure
                if (failure != null && failure.isConnection) {
                    ConnectionErrorState(onRetry = onRetry, errorCode = failure.code, onBack = onClose)
                } else {
                    FullScreenState(
                        title = stringResource(Res.string.swipe_error_title),
                        body = stringResource(Res.string.swipe_error_body),
                        errorCode = failure?.code,
                        primaryLabel = stringResource(Res.string.state_retry),
                        onPrimary = onRetry,
                    )
                }
            }
            VoteMode.GONE -> {
                Header(stringResource(Res.string.vote_closed_title), null, Symbols.close, onClose)
                FullScreenState(
                    title = stringResource(Res.string.vote_gone_title),
                    body = stringResource(Res.string.vote_gone_body),
                    illustration = painterResource(Res.drawable.ic_data_not_found),
                    errorCode = ErrorCode.ROUND_NOT_FOUND,
                    primaryLabel = stringResource(Res.string.vote_see_itinerary),
                    onPrimary = onOpenItinerary,
                )
            }
            VoteMode.VOTING -> if (round != null) VotingDeck(state, round, onClose, onLike, onSkip, onUndo, onOpenMembers, onOpenDetail)
            VoteMode.WAITING -> if (round != null) Waiting(state, round, onClose, onChangeVotes, onOpenItinerary)
            VoteMode.RESULT -> if (round != null) OwnerResult(state, round, onClose, onToggle, onDecide, onStartVoting, onOpenItinerary)
            VoteMode.CLOSED -> if (round != null) MemberClosed(state, round, onClose, onOpenItinerary)
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String?, icon: String, onIcon: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(icon, stringResource(if (icon == Symbols.close) Res.string.action_close else Res.string.action_back), onIcon)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        action?.invoke()
    }
}

@Composable
private fun voteTitle(state: VoteUiState) = stringResource(Res.string.vote_title, state.destination.ifBlank { state.tripTitle })

@Composable
private fun timeLeft(state: VoteUiState, round: RoundDto) =
    stringResource(Res.string.vote_subtitle, round.createdByName, hoursLeft(state.now, round.closesAt))

// ── Voting ──────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.VotingDeck(
    state: VoteUiState,
    round: RoundDto,
    onClose: () -> Unit,
    onLike: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenDetail: (placeId: String) -> Unit,
) {
    val cursor = state.cursor ?: 0
    val places = round.places
    Header(voteTitle(state), timeLeft(state, round), Symbols.close, onClose) {
        IconBox(Symbols.groupAdd, stringResource(Res.string.vote_members), onOpenMembers)
    }
    SwipeDeck(
        card = places.getOrNull(cursor)?.let { ExploreCard(it.category, it.item) },
        nextCard = places.getOrNull(cursor + 1)?.let { ExploreCard(it.category, it.item) },
        progress = stringResource(Res.string.swipe_progress, minOf(cursor + 1, places.size), places.size),
        countLabel = stringResource(Res.string.vote_liked_count, state.likedCount),
        canUndo = state.history.isNotEmpty(),
        likeDescription = stringResource(Res.string.vote_like),
        likeLabel = stringResource(Res.string.vote_label_like),
        onLike = onLike,
        onSkip = onSkip,
        onUndo = onUndo,
        onOpenDetail = onOpenDetail,
        modifier = Modifier.weight(1f),
    )
}

// ── Waiting (VotingMenunggu) ────────────────────────────────────────────────

@Composable
private fun ColumnScope.Waiting(
    state: VoteUiState,
    round: RoundDto,
    onClose: () -> Unit,
    onChangeVotes: () -> Unit,
    onOpenItinerary: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val liked = round.places.filter { state.votes[it.item.placeId] == Vote.LIKE }
    Header(voteTitle(state), timeLeft(state, round), Symbols.close, onClose)
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            // Design: how_to_vote; not in the app's icon subset.
            Symbol(Symbols.doneAll, size = 48.dp, tint = scheme.primary)
        }
        Text(stringResource(Res.string.vote_waiting_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            stringResource(Res.string.vote_waiting_body, liked.size, round.places.size, state.ownerName ?: stringResource(Res.string.vote_owner_fallback)),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (liked.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.vote_waiting_liked), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                liked.take(3).forEach { place ->
                    Row(
                        Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainer).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PlaceTile(place.category, 40)
                        Text(place.item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Symbol(Symbols.favorite, size = 20.dp, filled = true, tint = IrisTheme.accents.magenta)
                    }
                }
                if (liked.size > 3) {
                    Text(stringResource(Res.string.vote_waiting_more, liked.size - 3), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onChangeVotes, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(Res.string.vote_change), style = MaterialTheme.typography.titleMedium)
        }
        Button(onClick = onOpenItinerary, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(Res.string.vote_back_to_trip), style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ── Owner result (VotingHasil) ──────────────────────────────────────────────

@Composable
private fun ColumnScope.OwnerResult(
    state: VoteUiState,
    round: RoundDto,
    onClose: () -> Unit,
    onToggle: (placeId: String) -> Unit,
    onDecide: () -> Unit,
    onStartVoting: () -> Unit,
    onOpenItinerary: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val decided = round.decidedAt != null
    val open = state.roundState == RoundState.OPEN
    val trip = state.destination.ifBlank { state.tripTitle }
    val tally = round.tally.orEmpty().associateBy { it.placeId }
    val voters = round.voters.orEmpty()
    Header(
        stringResource(Res.string.vote_result_title),
        if (open) stringResource(Res.string.vote_result_subtitle_open, trip, hoursLeft(state.now, round.closesAt))
        else stringResource(Res.string.vote_result_subtitle_closed, trip),
        Symbols.arrowBack,
        onClose,
    )
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (decided) {
            Notice(
                Symbols.checkCircle,
                if (round.chosenPlaceIds.isEmpty()) stringResource(Res.string.vote_closed_decided_none, state.ownerName ?: stringResource(Res.string.vote_owner_fallback))
                else stringResource(Res.string.vote_closed_decided, state.ownerName ?: stringResource(Res.string.vote_owner_fallback), round.chosenPlaceIds.size, round.places.size),
                success = true,
            )
        } else {
            VotersCard(voters, round.places.size)
        }
        if (open && !decided && state.votes.size < round.places.size) {
            TextButton(onClick = onStartVoting) { Text(stringResource(Res.string.vote_result_join), fontWeight = FontWeight.Bold) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.vote_result_sorted), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            if (!decided) Text(stringResource(Res.string.vote_result_selected, state.selected.size), style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        }
        state.ranked.forEach { place ->
            val id = place.item.placeId
            ResultRow(
                place = place,
                tally = tally[id],
                voters = voters,
                checked = if (decided) id in round.chosenPlaceIds else id in state.selected,
                enabled = !decided && !state.busy,
                onToggle = { onToggle(id) },
            )
        }
    }
    Column(
        Modifier.fillMaxWidth().background(scheme.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (decided) {
            Button(onClick = onOpenItinerary, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(Res.string.vote_see_itinerary), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(onClick = onDecide, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(
                    if (state.selected.isEmpty()) stringResource(Res.string.vote_result_close_none)
                    else stringResource(Res.string.vote_result_action, state.selected.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(stringResource(Res.string.vote_result_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun VotersCard(voters: List<VoterDto>, placeCount: Int) {
    val scheme = MaterialTheme.colorScheme
    val done = voters.filter { it.votedCount >= placeCount }
    val missing = voters.filter { it.votedCount < placeCount }
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            voters.take(5).forEachIndexed { index, voter ->
                MemberAvatar(
                    voter.displayName, voter.userId,
                    Modifier.offset(x = (index * 24).dp).alpha(if (voter.votedCount >= placeCount) 1f else 0.45f),
                    size = 32.dp,
                )
            }
            Box(Modifier.size(width = (32 + 24 * (minOf(voters.size, 5) - 1).coerceAtLeast(0)).dp, height = 32.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.vote_result_voted, done.size, voters.size), style = MaterialTheme.typography.titleSmall)
            Text(
                if (missing.isEmpty()) stringResource(Res.string.vote_result_all_voted)
                else stringResource(Res.string.vote_result_missing, missing.joinToString(", ") { it.displayName }),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultRow(
    place: RoundPlaceDto,
    tally: TallyDto?,
    voters: List<VoterDto>,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val likes = tally?.likes ?: 0
    val skips = tally?.skips ?: 0
    val counts = buildString {
        append(stringResource(place.category.itineraryLabel))
        append(" · ")
        append(stringResource(Res.string.vote_result_likes, likes))
        if (skips > 0) append(" · ").append(stringResource(Res.string.vote_result_skips, skips))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(scheme.surface)
            .border(2.dp, if (checked) scheme.primary else scheme.surface, IrisShape.lg)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = if (enabled) ({ onToggle() }) else null, enabled = enabled)
        PlaceTile(place.category, 48)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(place.item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(counts, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        val likedBy = tally?.likedBy.orEmpty().mapNotNull { id -> voters.firstOrNull { it.userId == id } }
        if (likedBy.isNotEmpty()) {
            Box(Modifier.padding(end = 8.dp)) {
                likedBy.take(3).forEachIndexed { index, voter ->
                    MemberAvatar(voter.displayName, voter.userId, Modifier.offset(x = (index * 16).dp), size = 22.dp)
                }
                Box(Modifier.size(width = (22 + 16 * (minOf(likedBy.size, 3) - 1)).dp, height = 22.dp))
            }
        }
    }
}

// ── Member, closed (VotingDitutupMenunggu / VotingDitutup) ──────────────────

@Composable
private fun ColumnScope.MemberClosed(state: VoteUiState, round: RoundDto, onClose: () -> Unit, onOpenItinerary: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val owner = state.ownerName ?: stringResource(Res.string.vote_owner_fallback)
    val tally = round.tally.orEmpty().associateBy { it.placeId }
    val decided = round.decidedAt != null
    var expanded by rememberSaveable { mutableStateOf(false) }
    Header(
        stringResource(Res.string.vote_closed_title),
        stringResource(Res.string.vote_subtitle_closed, state.destination.ifBlank { state.tripTitle }, round.createdByName),
        Symbols.arrowBack,
        onClose,
    )
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (decided) {
            Notice(
                Symbols.checkCircle,
                if (round.chosenPlaceIds.isEmpty()) stringResource(Res.string.vote_closed_decided_none, owner)
                else stringResource(Res.string.vote_closed_decided, owner, round.chosenPlaceIds.size, round.places.size),
                success = true,
            )
            val chosen = state.ranked.filter { it.item.placeId in round.chosenPlaceIds }
            val others = state.ranked.filter { it.item.placeId !in round.chosenPlaceIds }
            if (chosen.isNotEmpty()) {
                SectionLabel(stringResource(Res.string.vote_closed_chosen))
                chosen.forEach { ClosedRow(it, tally[it.item.placeId], round.memberCount, chosen = true, liked = state.votes[it.item.placeId] == Vote.LIKE) }
            }
            if (others.isNotEmpty()) {
                SectionLabel(stringResource(Res.string.vote_closed_not_chosen, others.size))
                val shown = if (expanded) others else others.take(1)
                shown.forEach { ClosedRow(it, tally[it.item.placeId], round.memberCount, chosen = false, liked = state.votes[it.item.placeId] == Vote.LIKE, faded = true) }
                if (!expanded && others.size > 1) {
                    TextButton(onClick = { expanded = true }) { Text(stringResource(Res.string.vote_show_more, others.size - 1), fontWeight = FontWeight.Bold) }
                }
            }
        } else {
            Notice(Symbols.schedule, stringResource(Res.string.vote_closed_waiting, owner), success = false)
            SectionLabel(stringResource(Res.string.vote_closed_header, round.finishedCount ?: 0, round.memberCount))
            val shown = if (expanded) state.ranked else state.ranked.take(4)
            shown.forEach { ClosedRow(it, tally[it.item.placeId], round.memberCount, chosen = false, liked = state.votes[it.item.placeId] == Vote.LIKE) }
            if (!expanded && state.ranked.size > 4) {
                TextButton(onClick = { expanded = true }) { Text(stringResource(Res.string.vote_show_more, state.ranked.size - 4), fontWeight = FontWeight.Bold) }
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(scheme.surface)) {
        HorizontalDivider(color = scheme.outlineVariant)
        Button(onClick = onOpenItinerary, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 52.dp)) {
            Text(stringResource(Res.string.vote_see_itinerary), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ClosedRow(place: RoundPlaceDto, tally: TallyDto?, members: Int, chosen: Boolean, liked: Boolean, faded: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().alpha(if (faded) 0.75f else 1f).clip(IrisShape.lg).background(scheme.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlaceTile(place.category, if (faded) 40 else 48)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(place.item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.vote_likes_of, tally?.likes ?: 0, members), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        when {
            chosen -> Symbol(Symbols.checkCircle, contentDescription = stringResource(Res.string.vote_chosen), size = 20.dp, filled = true, tint = scheme.primary)
            liked -> Row(
                Modifier.clip(IrisShape.xs).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Symbol(Symbols.favorite, size = 14.dp, filled = true, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(stringResource(Res.string.vote_you_liked), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
            }
        }
    }
}

// ── small parts ─────────────────────────────────────────────────────────────

@Composable
private fun Notice(symbol: String, text: String, success: Boolean) {
    val bg = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.md).background(bg).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(symbol, size = 18.dp, filled = true, tint = fg)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
}

/** The category colour tile used as a small place picture in the lists. */
@Composable
private fun PlaceTile(category: Category, size: Int) {
    Box(Modifier.size(size.dp).clip(IrisShape.md).background(category.tileColor()), contentAlignment = Alignment.Center) {
        Symbol(category.symbol, size = (size / 2).dp, filled = true, tint = IrisTheme.accents.onAccent)
    }
}

// ── previews ────────────────────────────────────────────────────────────────

private val previewNow = Clock.System.now()

private fun previewPlace(id: String, name: String, category: Category) =
    RoundPlaceDto(category, RecommendationItem(id, name, "Cocok dengan minat alam dan gaya santaimu.", 1, 90, -8.5, 115.2))

private val previewRound = RoundDto(
    id = "r1", tripId = "t1", createdByUserId = "u1", createdByName = "Rina",
    createdAt = previewNow - 6.hours, closesAt = previewNow + 18.hours, expiresAt = previewNow + 160.hours,
    places = listOf(
        previewPlace("a", "Air Terjun Tegenungan", Category.ATTRACTION),
        previewPlace("b", "Warung Babi Guling Ibu Oka", Category.FOOD),
        previewPlace("c", "Pura Tirta Empul", Category.ATTRACTION),
        previewPlace("d", "Pantai Melasti", Category.ATTRACTION),
    ),
    myVotes = mapOf("a" to Vote.LIKE, "b" to Vote.LIKE, "c" to Vote.SKIP, "d" to Vote.LIKE),
    memberCount = 4,
    finishedCount = 3,
    tally = listOf(TallyDto("a", 4, 0, listOf("u1", "u2", "u3", "u4")), TallyDto("b", 3, 1, listOf("u1", "u2", "u3")), TallyDto("c", 3, 0), TallyDto("d", 1, 2)),
    voters = listOf(VoterDto("u1", "Rina", 4), VoterDto("u2", "Bayu", 4), VoterDto("u3", "Ayu", 4), VoterDto("u4", "Dina", 1)),
)

private fun previewState(round: RoundDto, cursor: Int? = null) = VoteUiState(
    loading = false, round = round, tripTitle = "Bali 4 hari", destination = "Bali", ownerName = "Rina",
    cursor = cursor, votes = round.myVotes, selected = setOf("a", "b", "c"), now = previewNow,
)

@Composable
private fun PreviewContent(state: VoteUiState) = VoteContent(
    state, onClose = {}, onRetry = {}, onLike = {}, onSkip = {}, onUndo = {}, onChangeVotes = {}, onToggle = {},
    onDecide = {}, onStartVoting = {}, onOpenItinerary = {}, onOpenMembers = {}, onOpenDetail = {},
)

@Preview
@Composable
private fun VoteVotingPreview() = IrisPreview {
    PreviewContent(previewState(previewRound.copy(voters = null, tally = null, myVotes = emptyMap()), cursor = 0))
}

@Preview
@Composable
private fun VoteWaitingPreview() = IrisPreview {
    PreviewContent(previewState(previewRound.copy(voters = null, tally = null)))
}

@Preview
@Composable
private fun VoteOwnerResultPreview() = IrisPreview {
    PreviewContent(previewState(previewRound))
}

@Preview
@Composable
private fun VoteClosedWaitingPreview() = IrisPreview {
    PreviewContent(previewState(previewRound.copy(voters = null, closesAt = previewNow - 2.hours, createdAt = previewNow - 26.hours)))
}

@Preview
@Composable
private fun VoteClosedDecidedPreview() = IrisPreview {
    PreviewContent(
        previewState(
            previewRound.copy(voters = null, closesAt = previewNow - 2.hours, decidedAt = previewNow - 1.hours, chosenPlaceIds = listOf("a", "b", "c")),
        ),
    )
}
