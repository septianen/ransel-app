package app.ransel.feature.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.domain.RoundState
import app.ransel.shared.domain.hoursLeft
import app.ransel.shared.domain.roundState
import app.ransel.shared.dto.RoundSummaryDto
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.components.SectionHeader
import app.ransel.ui.components.StatusTag
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.*

/** The OWNER opens a closed, undecided round on its result; everyone else opens the cards. */
fun RoundSummaryDto.opensResult(isOwner: Boolean, now: Instant): Boolean =
    isOwner && roundState(now, closesAt, decidedAt) == RoundState.CLOSED

/** One voting round: who searched, how many places, and where it stands. Also used in the Group tab's "Voting" list. */
@Composable
fun VoteRoundRow(
    round: RoundSummaryDto,
    isOwner: Boolean,
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
            .heightIn(min = 64.dp)
            .clip(IrisShape.lg)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // like a poll row: the tile says what kind of voting it is (Explore AI places), the tag where it stands
        Box(Modifier.size(40.dp).clip(IrisShape.md).background(IrisTheme.accents.magenta), contentAlignment = Alignment.Center) {
            Symbol(Symbols.autoAwesome, size = 22.dp, filled = true, tint = IrisTheme.accents.onAccent)
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
        StatusTag(
            stringResource(
                when (roundState) {
                    RoundState.OPEN -> Res.string.poll_status_open
                    RoundState.CLOSED -> Res.string.poll_status_closed
                    RoundState.DECIDED -> Res.string.poll_status_decided
                },
            ),
        )
    }
}

@Preview
@Composable
private fun VoteRoundRowPreview() = IrisPreview {
    val now = Clock.System.now()
    val round = RoundSummaryDto(
        id = "r", tripId = "t", createdByUserId = "u", createdByName = "Rina", createdAt = now - 1.hours,
        closesAt = now + 23.hours, placeCount = 12, myVoteCount = 0,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VoteRoundRow(round, isOwner = true, now = now) {}
        VoteRoundRow(round.copy(closesAt = now - 30.hours, decidedAt = now - 2.hours, chosenCount = 3), isOwner = true, now = now) {}
    }
}
