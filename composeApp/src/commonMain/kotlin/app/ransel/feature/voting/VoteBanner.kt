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
 * The voting banner on the trip screens (design `VotingBanner`): "Rina meminta pilihanmu" while the user still has
 * cards to vote on, otherwise where the round stands (waiting, ready to decide, decided). [isOwner] changes the
 * wording for the one who decides. A tap opens the round.
 */
@Composable
fun VoteBanner(
    round: RoundSummaryDto,
    destination: String,
    isOwner: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** The signed-in user: the one who searched is asked to continue, not "asked for their picks" by themselves. */
    myUserId: String? = null,
    now: Instant = Clock.System.now(),
) {
    val scheme = MaterialTheme.colorScheme
    val state = roundState(now, round.closesAt, round.decidedAt)
    val hours = hoursLeft(now, round.closesAt)
    val needsVote = round.needsMyVote(now)
    val mine = myUserId != null && round.createdByUserId == myUserId
    val (title, body, action) = when {
        needsVote && mine -> Triple(
            stringResource(Res.string.vote_banner_own_request),
            stringResource(Res.string.vote_banner_own_request_body, round.myVoteCount, round.placeCount, hours),
            stringResource(Res.string.vote_banner_pick),
        )
        needsVote && isOwner -> Triple(
            stringResource(Res.string.vote_banner_owner_request, round.createdByName, round.placeCount),
            stringResource(Res.string.vote_banner_owner_request_body, hours),
            stringResource(Res.string.vote_banner_pick),
        )
        needsVote -> Triple(
            stringResource(Res.string.vote_banner_request, round.createdByName),
            stringResource(Res.string.vote_banner_request_body, round.placeCount, destination, hours),
            stringResource(Res.string.vote_banner_pick),
        )
        state == RoundState.DECIDED -> Triple(
            if ((round.chosenCount ?: 0) == 0) stringResource(Res.string.vote_banner_decided_none)
            else stringResource(Res.string.vote_banner_decided, round.chosenCount ?: 0),
            stringResource(Res.string.vote_banner_decided_body),
            stringResource(Res.string.vote_banner_see),
        )
        isOwner && state == RoundState.CLOSED -> Triple(
            stringResource(Res.string.vote_banner_ready),
            stringResource(Res.string.vote_banner_ready_body, round.placeCount, round.createdByName),
            stringResource(Res.string.vote_banner_see),
        )
        state == RoundState.CLOSED -> Triple(
            stringResource(Res.string.vote_closed_title),
            stringResource(Res.string.vote_banner_closed_body),
            stringResource(Res.string.vote_banner_see),
        )
        else -> Triple(
            stringResource(Res.string.vote_banner_waiting),
            stringResource(Res.string.vote_banner_open_body, round.createdByName, hours),
            stringResource(Res.string.vote_banner_see),
        )
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(scheme.secondaryContainer)
            .border(1.dp, scheme.secondary.copy(alpha = 0.3f), IrisShape.lg)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(IrisTheme.accents.magenta), contentAlignment = Alignment.Center) {
            // Design: how_to_vote; not in the app's icon subset.
            Symbol(if (state == RoundState.DECIDED) Symbols.checkCircle else Symbols.favorite, size = 22.dp, filled = true, tint = IrisTheme.accents.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.heightIn(min = 36.dp).clip(CircleShape).background(IrisTheme.accents.magenta).padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = IrisTheme.accents.onAccent, fontWeight = FontWeight.Bold)
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
        VoteBanner(round, "Bali", isOwner = false, onOpen = {}, now = now)
        VoteBanner(round.copy(createdByName = "Dinda"), "Bali", isOwner = true, onOpen = {}, now = now)
        VoteBanner(round.copy(myVoteCount = 4), "Bali", isOwner = true, onOpen = {}, myUserId = "u", now = now)
        VoteBanner(round.copy(myVoteCount = 12), "Bali", isOwner = false, onOpen = {}, now = now)
        VoteBanner(round.copy(closesAt = now - 1.hours), "Bali", isOwner = true, onOpen = {}, now = now)
        VoteBanner(round.copy(decidedAt = now, chosenCount = 3), "Bali", isOwner = false, onOpen = {}, now = now)
    }
}
