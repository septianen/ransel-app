package app.ransel.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.content.stringResource
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.voting.VotingStore
import app.ransel.feature.explore.IconBox
import app.ransel.shared.domain.RoundState
import app.ransel.shared.domain.roundState
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.RoundSummaryDto
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollStatus
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.vote_banner_history
import ransel.composeapp.generated.resources.voting_history_empty

data class VotingHistoryUiState(
    val loading: Boolean = true,
    /** Decided polls and decided place-voting rounds, the most recently ended first. */
    val entries: List<VotingEntry> = emptyList(),
)

/** "Riwayat voting": every voting of the trip that is over (decided), opened from the Group tab. */
class VotingHistoryViewModel(
    private val tripId: String,
    private val api: RanselApi,
    private val voting: VotingStore,
) : ViewModel() {
    private val polls = MutableStateFlow<List<PollDto>?>(null)

    val state: StateFlow<VotingHistoryUiState> = combine(polls, voting.rounds) { polls, rounds ->
        val now = Clock.System.now()
        VotingHistoryUiState(
            loading = polls == null,
            entries = finishedVotings(polls.orEmpty(), rounds[tripId].orEmpty(), now),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VotingHistoryUiState())

    init {
        viewModelScope.launch {
            launch { voting.refresh(api, tripId) }
            polls.value = (api.polls(tripId) as? ApiResult.Success)?.value.orEmpty()
        }
    }
}

/** Only what is over: a poll whose result was confirmed, a round the owner decided. */
internal fun finishedVotings(polls: List<PollDto>, rounds: List<RoundSummaryDto>, now: Instant): List<VotingEntry> =
    votingEntries(
        polls.filter { it.status == PollStatus.DECIDED },
        rounds.filter { roundState(now, it.closesAt, it.decidedAt) == RoundState.DECIDED },
        finished = true,
    )

@Composable
fun VotingHistoryScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpenPoll: (pollId: String) -> Unit,
    onOpenRound: (roundId: String, showResult: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VotingHistoryViewModel = koinViewModel(key = "voting-history-$tripId") { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()
    VotingHistoryContent(state, onBack, onOpenPoll, onOpenRound, modifier)
}

@Composable
fun VotingHistoryContent(
    state: VotingHistoryUiState,
    onBack: () -> Unit,
    onOpenPoll: (pollId: String) -> Unit,
    onOpenRound: (roundId: String, showResult: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(scheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(Symbols.arrowBack, stringResource(Res.string.action_back), onBack)
            Text(stringResource(Res.string.vote_banner_history), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.voting_history_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // decided rounds and polls never open a result to decide, so the role does not matter here
                val now = Clock.System.now()
                state.entries.forEach { VotingEntryRow(it, isOwner = false, now = now, onOpenPoll = onOpenPoll, onOpenRound = onOpenRound) }
            }
        }
    }
}

@Preview
@Composable
private fun VotingHistoryPreview() = IrisPreview {
    val now = Clock.System.now()
    val poll = PollDto(
        id = "p", tripId = "t", kind = PollKind.FOOD, question = "Makan malam di mana?", createdByMemberId = "m", createdByName = "Dinda",
        createdAt = now - 48.hours, closesAt = now - 24.hours, status = PollStatus.DECIDED, memberCount = 3, votedMemberIds = listOf("m"),
    )
    val round = RoundSummaryDto(
        id = "r", tripId = "t", createdByUserId = "u", createdByName = "Rina", createdAt = now - 72.hours,
        closesAt = now - 48.hours, decidedAt = now - 30.hours, placeCount = 12, myVoteCount = 12, chosenCount = 3,
    )
    VotingHistoryContent(VotingHistoryUiState(loading = false, entries = finishedVotings(listOf(poll), listOf(round), now)), {}, {}, { _, _ -> })
}

@Preview
@Composable
private fun VotingHistoryEmptyPreview() = IrisPreview {
    VotingHistoryContent(VotingHistoryUiState(loading = false), {}, {}, { _, _ -> })
}
