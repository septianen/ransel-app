package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.domain.pollHoursLeft
import app.ransel.shared.dto.PollDto
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollStatus
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.date_poll_card_open
import ransel.composeapp.generated.resources.date_poll_card_pick
import ransel.composeapp.generated.resources.date_poll_card_title
import ransel.composeapp.generated.resources.date_poll_card_title_mine
import ransel.composeapp.generated.resources.date_poll_closes_days
import ransel.composeapp.generated.resources.date_poll_closes_hours
import ransel.composeapp.generated.resources.date_poll_options
import ransel.composeapp.generated.resources.poll_voted_summary

/**
 * The open DATE poll of a trip without dates (design ItineraryPolling, also on Ringkasan): "<name> minta kamu pilih
 * tanggal", "3 pilihan · tutup 2 hari lagi · 3 dari 5 sudah memilih" and "Pilih tanggal". [mine] = I made the poll.
 */
@Composable
fun DatePollCard(
    poll: PollDto,
    mine: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
) {
    val scheme = MaterialTheme.colorScheme
    val hours = pollHoursLeft(now, poll.closesAt)
    val closes = if (hours >= 48) {
        pluralStringResource(Res.plurals.date_poll_closes_days, (hours + 23) / 24, (hours + 23) / 24)
    } else {
        pluralStringResource(Res.plurals.date_poll_closes_hours, hours, hours)
    }
    val summary = listOf(
        pluralStringResource(Res.plurals.date_poll_options, poll.options.size, poll.options.size),
        closes,
        stringResource(Res.string.poll_voted_summary, poll.votedMemberIds.size, poll.memberCount),
    ).joinToString(" · ")
    val voted = poll.myVotes.isNotEmpty()
    Column(
        modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(scheme.secondaryContainer)
            .border(1.dp, scheme.secondary.copy(alpha = 0.3f), IrisShape.lg)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberAvatar(name = poll.createdByName, key = poll.createdByMemberId, isOwner = true)
            Column(Modifier.weight(1f)) {
                Text(
                    if (mine) stringResource(Res.string.date_poll_card_title_mine)
                    else stringResource(Res.string.date_poll_card_title, poll.createdByName),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSecondaryContainer,
                )
                Text(summary, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = scheme.secondary, contentColor = scheme.onSecondary),
        ) {
            Symbol(Symbols.eventAvailable, size = 20.dp, tint = scheme.onSecondary)
            Text(
                stringResource(if (voted) Res.string.date_poll_card_open else Res.string.date_poll_card_pick),
                Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview
@Composable
private fun DatePollCardPreview() = IrisPreview {
    val now = Instant.parse("2026-10-28T10:00:00Z")
    DatePollCard(
        poll = PollDto(
            id = "p", tripId = "t", kind = PollKind.DATE, question = "Kapan berangkat?", createdByMemberId = "m1",
            createdByName = "Septian", createdAt = now, closesAt = Instant.parse("2026-10-30T10:00:00Z"),
            status = PollStatus.OPEN, votedMemberIds = listOf("a", "b", "c"), memberCount = 5,
            options = List(3) { app.ransel.shared.dto.PollOptionDto(id = "$it", label = "") },
        ),
        mine = false,
        onOpen = {},
        now = now,
    )
}
