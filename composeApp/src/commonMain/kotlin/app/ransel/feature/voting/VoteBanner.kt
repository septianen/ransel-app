package app.ransel.feature.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.data.voting.needsMyVote
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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.*

/**
 * The voting banner on the trip screens (design `VotingBanner`): one banner, about the round that needs the user:
 * "Rina meminta pilihanmu" while they still have cards to vote on, or "ready to decide" for the owner of a closed
 * round. [moreActions] other rounds waiting for them are counted in the text. With no [round] it is a quiet link to
 * the history of [roundCount] rounds. A tap always opens the voting list.
 */
@Composable
fun VoteBanner(
    round: RoundSummaryDto?,
    roundCount: Int,
    destination: String,
    isOwner: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    moreActions: Int = 0,
    /** The signed-in user: the one who searched is asked to continue, not "asked for their picks" by themselves. */
    myUserId: String? = null,
    now: Instant = Clock.System.now(),
) {
    val scheme = MaterialTheme.colorScheme
    val hours = if (round != null) hoursLeft(now, round.closesAt) else 0
    val mine = round != null && myUserId != null && round.createdByUserId == myUserId
    val (title, bodyText, action) = when {
        round == null -> Triple(
            stringResource(Res.string.vote_banner_history),
            stringResource(Res.string.vote_banner_history_body, roundCount),
            stringResource(Res.string.vote_banner_see),
        )
        round.needsMyVote(now) && mine -> Triple(
            stringResource(Res.string.vote_banner_own_request),
            stringResource(Res.string.vote_banner_own_request_body, round.myVoteCount, round.placeCount, hours),
            stringResource(Res.string.vote_banner_pick),
        )
        round.needsMyVote(now) && isOwner -> Triple(
            stringResource(Res.string.vote_banner_owner_request, round.createdByName, round.placeCount),
            stringResource(Res.string.vote_banner_owner_request_body, hours),
            stringResource(Res.string.vote_banner_pick),
        )
        round.needsMyVote(now) -> Triple(
            stringResource(Res.string.vote_banner_request, round.createdByName),
            stringResource(Res.string.vote_banner_request_body, round.placeCount, destination, hours),
            stringResource(Res.string.vote_banner_pick),
        )
        else -> Triple(
            stringResource(Res.string.vote_banner_ready),
            stringResource(Res.string.vote_banner_ready_body, round.placeCount, round.createdByName),
            stringResource(Res.string.vote_banner_see),
        )
    }
    val body = if (moreActions > 0) bodyText + " · " + stringResource(Res.string.vote_banner_more, moreActions) else bodyText
    val quiet = round == null
    val state = if (round != null) roundState(now, round.closesAt, round.decidedAt) else RoundState.DECIDED
    Row(
        modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(if (quiet) scheme.surface else scheme.secondaryContainer)
            .border(1.dp, if (quiet) scheme.outlineVariant else scheme.secondary.copy(alpha = 0.3f), IrisShape.lg)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (quiet) scheme.surfaceContainerHigh else IrisTheme.accents.magenta), contentAlignment = Alignment.Center) {
            // Design: how_to_vote; not in the app's icon subset.
            Symbol(if (state == RoundState.DECIDED) Symbols.checkCircle else Symbols.favorite, size = 22.dp, filled = true, tint = if (quiet) scheme.onSurfaceVariant else IrisTheme.accents.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (quiet) scheme.surfaceContainerHigh else IrisTheme.accents.magenta).padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = if (quiet) scheme.onSurface else IrisTheme.accents.onAccent, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview
@Composable
private fun VoteBannerPreview() = IrisPreview {
    val now = Clock.System.now()
    val round = RoundSummaryDto(
        id = "r", tripId = "t", createdByUserId = "u", createdByName = "Rina", createdAt = now - 1.hours,
        closesAt = now + 23.hours, placeCount = 12, myVoteCount = 0,
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VoteBanner(round, 1, "Bali", isOwner = false, onOpen = {}, now = now)
        VoteBanner(round.copy(createdByName = "Dinda"), 2, "Bali", isOwner = true, onOpen = {}, now = now)
        VoteBanner(round.copy(myVoteCount = 4), 1, "Bali", isOwner = true, onOpen = {}, myUserId = "u", now = now)
        VoteBanner(round, 3, "Bali", isOwner = false, onOpen = {}, moreActions = 2, now = now)
        VoteBanner(round.copy(closesAt = now - 1.hours), 1, "Bali", isOwner = true, onOpen = {}, now = now)
        VoteBanner(null, 4, "Bali", isOwner = false, onOpen = {}, now = now)
    }
}
