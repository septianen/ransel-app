package app.ransel.feature.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.feature.explore.IconBox
import app.ransel.shared.domain.RoundState
import app.ransel.shared.domain.hoursLeft
import app.ransel.shared.domain.roundState
import app.ransel.shared.dto.RoundSummaryDto
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*

/**
 * Every voting round of a trip, in three groups (needs you, waiting, done). Opened from the Group tab's banner; a row
 * opens its round (the OWNER goes to the result when the round is closed and not decided).
 */
@Composable
fun VoteListScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpenRound: (roundId: String, showResult: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoteListViewModel = koinViewModel(key = "vote-list-$tripId") { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()
    VoteListContent(state, onBack, onOpenRound, modifier)
}

@Composable
fun VoteListContent(
    state: VoteListUiState,
    onBack: () -> Unit,
    onOpenRound: (roundId: String, showResult: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(scheme.surfaceContainer).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(scheme.surface).heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBox(Symbols.arrowBack, stringResource(Res.string.action_back), onBack)
            Text(stringResource(Res.string.vote_list_title), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
        }
        if (state.isEmpty) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.vote_list_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Section(Res.string.vote_list_section_action, state.action, state, highlight = true, onOpenRound = onOpenRound)
                Section(Res.string.vote_list_section_waiting, state.waiting, state, highlight = false, onOpenRound = onOpenRound)
                Section(Res.string.vote_list_section_done, state.done, state, highlight = false, onOpenRound = onOpenRound)
            }
        }
    }
}

@Composable
private fun Section(
    title: StringResource,
    rounds: List<RoundSummaryDto>,
    state: VoteListUiState,
    highlight: Boolean,
    onOpenRound: (roundId: String, showResult: Boolean) -> Unit,
) {
    if (rounds.isEmpty()) return
    Text(
        stringResource(title),
        Modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rounds.forEach { round ->
        VoteRoundRow(round, state.isOwner, highlight, state.now) { onOpenRound(round.id, round.opensResult(state.isOwner, state.now)) }
    }
}

/** The OWNER opens a closed, undecided round on its result; everyone else opens the cards. */
fun RoundSummaryDto.opensResult(isOwner: Boolean, now: Instant): Boolean =
    isOwner && roundState(now, closesAt, decidedAt) == RoundState.CLOSED

/** One voting round: who searched, how many places, and where it stands. Also used in the Group tab's "Voting" list. */
@Composable
fun VoteRoundRow(
    round: RoundSummaryDto,
    isOwner: Boolean,
    highlight: Boolean,
    now: Instant,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val roundState = roundState(now, round.closesAt, round.decidedAt)
    val status = when {
        roundState == RoundState.OPEN && round.myVoteCount < round.placeCount ->
            stringResource(Res.string.vote_list_row_open, hoursLeft(now, round.closesAt))
        roundState == RoundState.OPEN -> stringResource(Res.string.vote_banner_waiting)
        roundState == RoundState.CLOSED && isOwner -> stringResource(Res.string.vote_banner_ready)
        roundState == RoundState.CLOSED -> stringResource(Res.string.vote_banner_closed_body)
        (round.chosenCount ?: 0) == 0 -> stringResource(Res.string.vote_banner_decided_none)
        else -> stringResource(Res.string.vote_banner_decided, round.chosenCount ?: 0)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (highlight) IrisTheme.accents.magenta else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(
                if (roundState == RoundState.DECIDED) Symbols.checkCircle else Symbols.favorite,
                size = 22.dp,
                filled = true,
                tint = if (highlight) IrisTheme.accents.onAccent else scheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.vote_list_row, round.createdByName, round.placeCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Preview
@Composable
private fun VoteListPreview() = IrisPreview {
    val now = Clock.System.now()
    val round = RoundSummaryDto(
        id = "r", tripId = "t", createdByUserId = "u", createdByName = "Rina", createdAt = now - 1.hours,
        closesAt = now + 23.hours, placeCount = 12, myVoteCount = 0,
    )
    VoteListContent(
        VoteListUiState(
            action = listOf(round, round.copy(id = "r2", createdByName = "Dinda", closesAt = now - 1.hours)),
            waiting = listOf(round.copy(id = "r3", myVoteCount = 12)),
            done = listOf(round.copy(id = "r4", closesAt = now - 30.hours, decidedAt = now - 2.hours, chosenCount = 3)),
            isOwner = true,
            now = now,
        ),
        onBack = {},
        onOpenRound = { _, _ -> },
    )
}
