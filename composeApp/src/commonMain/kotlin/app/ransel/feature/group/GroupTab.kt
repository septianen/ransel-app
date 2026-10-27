package app.ransel.feature.group

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.feature.trips.countdownLabel
import app.ransel.feature.polls.PollNeed
import app.ransel.data.voting.actionRounds
import app.ransel.feature.voting.VoteRoundRow
import app.ransel.feature.voting.opensResult
import app.ransel.shared.domain.RoundState
import app.ransel.shared.domain.roundState
import app.ransel.shared.dto.RoundSummaryDto
import kotlin.time.Clock
import app.ransel.feature.polls.needFor
import app.ransel.feature.polls.symbol
import app.ransel.feature.polls.tileColor
import app.ransel.shared.dto.PollDto
import app.ransel.ui.format.formatMoney
import app.ransel.shared.dto.TransferDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollStatus
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.theme.IrisTheme
import androidx.compose.foundation.layout.heightIn
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.today
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.group_documents
import ransel.composeapp.generated.resources.documents_title
import ransel.composeapp.generated.resources.group_documents_all
import ransel.composeapp.generated.resources.group_countdown_finished
import ransel.composeapp.generated.resources.group_countdown_ongoing
import ransel.composeapp.generated.resources.group_guest_tag
import ransel.composeapp.generated.resources.group_members_title
import ransel.composeapp.generated.resources.group_needs_empty
import ransel.composeapp.generated.resources.group_need_confirm
import ransel.composeapp.generated.resources.group_need_pay
import ransel.composeapp.generated.resources.group_polls_empty
import ransel.composeapp.generated.resources.group_polls_title
import ransel.composeapp.generated.resources.poll_need_decide
import ransel.composeapp.generated.resources.poll_need_vote
import ransel.composeapp.generated.resources.poll_status_closed
import ransel.composeapp.generated.resources.poll_status_decided
import ransel.composeapp.generated.resources.poll_status_open
import ransel.composeapp.generated.resources.poll_status_tied
import ransel.composeapp.generated.resources.poll_voted_summary
import ransel.composeapp.generated.resources.group_needs_title
import ransel.composeapp.generated.resources.group_signin_body
import ransel.composeapp.generated.resources.group_signin_title
import ransel.composeapp.generated.resources.members_count
import ransel.composeapp.generated.resources.members_you
import ransel.composeapp.generated.resources.role_editor
import ransel.composeapp.generated.resources.role_owner
import ransel.composeapp.generated.resources.role_viewer

/**
 * The first tab of a trip: its hub. A countdown, "Perlu tindakanmu" (votings and money that wait for the user), every
 * voting of the trip (polls and place-voting rounds; the other tabs show none), documents and the
 * members, with the ones added by name tagged "Tanpa akun". [needs] draws what asks for the user, [hasNeeds] says whether
 * there is anything.
 */
@Composable
fun GroupTab(
    trip: TripDto,
    signedIn: Boolean,
    hasNeeds: Boolean,
    needs: @Composable () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenPoll: (pollId: String) -> Unit,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    /** The trip's place-voting rounds (Explore on a group trip), listed with the polls. */
    rounds: List<RoundSummaryDto> = emptyList(),
    onOpenRound: (roundId: String, showResult: Boolean) -> Unit = { _, _ -> },
    /** A money item in "Perlu tindakanmu" opens the Pengeluaran tab. */
    onOpenMoney: () -> Unit = {},
    onOpenDocuments: () -> Unit = {},
    viewModel: GroupViewModel = koinViewModel(key = "group-${trip.id}") { parametersOf(trip.id) },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) viewModel.load() }
    GroupContent(
        trip = trip,
        today = today(),
        members = state.members,
        signedIn = signedIn,
        myUserId = viewModel.myUserId,
        polls = state.polls,
        myMemberId = viewModel.myMemberId,
        isOwner = trip.myRole == Role.OWNER,
        hasNeeds = hasNeeds,
        needs = needs,
        onOpenMembers = onOpenMembers,
        onOpenPoll = onOpenPoll,
        rounds = rounds,
        onOpenRound = onOpenRound,
        modifier = modifier,
        owes = state.owes,
        claimsToConfirm = state.claimsToConfirm,
        currency = state.currency,
        onOpenMoney = onOpenMoney,
        onOpenDocuments = onOpenDocuments,
    )
}

@Composable
fun GroupContent(
    trip: TripDto,
    today: LocalDate,
    members: List<TripMemberDto>,
    signedIn: Boolean,
    myUserId: String?,
    polls: List<PollDto>,
    myMemberId: String?,
    isOwner: Boolean,
    hasNeeds: Boolean,
    needs: @Composable () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenPoll: (pollId: String) -> Unit,
    modifier: Modifier = Modifier,
    rounds: List<RoundSummaryDto> = emptyList(),
    onOpenRound: (roundId: String, showResult: Boolean) -> Unit = { _, _ -> },
    now: Instant = Clock.System.now(),
    owes: List<TransferDto> = emptyList(),
    claimsToConfirm: List<SettlementDto> = emptyList(),
    currency: String = trip.baseCurrency,
    onOpenMoney: () -> Unit = {},
    onOpenDocuments: () -> Unit = {},
) {
    val dates = rememberDateFormatter()
    val pollNeeds = polls.mapNotNull { poll -> poll.needFor(myMemberId, isOwner)?.let { poll to it } }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        CountdownCard(countdown = countdownFor(trip, today), dates = dates.range(trip.startDate, trip.endDate))

        if (!signedIn) {
            SignInCard()
            return@Column
        }

        Section(stringResource(Res.string.group_needs_title)) {
            if (hasNeeds) needs()
            pollNeeds.forEach { (poll, need) -> PollNeedCard(poll, need) { onOpenPoll(poll.id) } }
            fun nameOf(id: String) = members.firstOrNull { it.memberId == id }?.displayName ?: "?"
            claimsToConfirm.forEach { claim ->
                MoneyNeedCard(stringResource(Res.string.group_need_confirm, nameOf(claim.fromMemberId), formatMoney(claim.amountMinor, currency)), onOpenMoney)
            }
            owes.forEach { t ->
                MoneyNeedCard(stringResource(Res.string.group_need_pay, formatMoney(t.amountMinor, currency), nameOf(t.toMemberId)), onOpenMoney)
            }
            if (!hasNeeds && pollNeeds.isEmpty() && owes.isEmpty() && claimsToConfirm.isEmpty()) Text(
                stringResource(Res.string.group_needs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Every voting of the trip lives here: polls and place-voting rounds, ongoing first, then newest first.
        Section(stringResource(Res.string.group_polls_title)) {
            val votings = votingEntries(polls, rounds, now)
            if (votings.isEmpty()) {
                Text(
                    stringResource(Res.string.group_polls_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val actionRounds = rounds.actionRounds(isOwner, now).toSet()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    votings.forEach { entry ->
                        when (entry) {
                            is VotingEntry.Poll -> PollRow(entry.poll) { onOpenPoll(entry.poll.id) }
                            is VotingEntry.Round -> VoteRoundRow(
                                entry.round,
                                isOwner = isOwner,
                                highlight = entry.round in actionRounds,
                                now = now,
                                modifier = Modifier.heightIn(min = 64.dp),
                                background = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) { onOpenRound(entry.round.id, entry.round.opensResult(isOwner, now)) }
                        }
                    }
                }
            }
        }

        Section(stringResource(Res.string.group_documents)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(IrisShape.lg)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(role = SemanticsRole.Button, onClick = onOpenDocuments).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Symbol(Symbols.receiptLong, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.documents_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(stringResource(Res.string.group_documents_all), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Symbol(Symbols.chevronRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Section(
            title = stringResource(Res.string.group_members_title),
            trailing = pluralStringResource(Res.plurals.members_count, members.size, members.size),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(IrisShape.lg).clickable(role = SemanticsRole.Button, onClick = onOpenMembers),
            ) {
                members.forEach { MemberLine(it, isMe = it.userId != null && it.userId == myUserId) }
            }
        }
    }
}

private sealed interface VotingEntry {
    val ongoing: Boolean
    val createdAt: Instant

    data class Poll(val poll: PollDto) : VotingEntry {
        override val ongoing get() = poll.status == PollStatus.OPEN
        override val createdAt get() = poll.createdAt
    }

    data class Round(val round: RoundSummaryDto, override val ongoing: Boolean) : VotingEntry {
        override val createdAt get() = round.createdAt
    }
}

private fun votingEntries(polls: List<PollDto>, rounds: List<RoundSummaryDto>, now: Instant): List<VotingEntry> =
    (polls.map { VotingEntry.Poll(it) } + rounds.map { VotingEntry.Round(it, roundState(now, it.closesAt, it.decidedAt) != RoundState.DECIDED) })
        .sortedWith(compareByDescending<VotingEntry> { it.ongoing }.thenByDescending { it.createdAt })

/** A poll that asks something of the user: vote, or (creator and owner) confirm the result. */
@Composable
private fun MoneyNeedCard(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(role = SemanticsRole.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(Symbols.payments, filled = true, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Symbol(Symbols.chevronRight, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun PollNeedCard(poll: PollDto, need: PollNeed, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(role = SemanticsRole.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PollTile(poll.kind)
        Column(Modifier.weight(1f)) {
            Text(poll.question, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (need == PollNeed.VOTE) Res.string.poll_need_vote else Res.string.poll_need_decide),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun PollRow(poll: PollDto, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = SemanticsRole.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PollTile(poll.kind)
        Column(Modifier.weight(1f)) {
            Text(poll.question, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(Res.string.poll_voted_summary, poll.votedMemberIds.size, poll.memberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Tag(
            stringResource(
                when (poll.status) {
                    PollStatus.OPEN -> Res.string.poll_status_open
                    PollStatus.CLOSED -> Res.string.poll_status_closed
                    PollStatus.TIED -> Res.string.poll_status_tied
                    PollStatus.DECIDED -> Res.string.poll_status_decided
                },
            ),
        )
    }
}

@Composable
private fun PollTile(kind: PollKind) {
    Box(Modifier.size(40.dp).clip(IrisShape.md).background(kind.tileColor()), contentAlignment = Alignment.Center) {
        Symbol(kind.symbol, size = 22.dp, tint = IrisTheme.accents.onAccent)
    }
}

@Composable
private fun CountdownCard(countdown: Countdown, dates: String) {
    val label = when (countdown) {
        is Countdown.Upcoming -> countdownLabel(countdown.days)
        is Countdown.Ongoing -> stringResource(Res.string.group_countdown_ongoing, countdown.day, countdown.totalDays)
        Countdown.Finished -> stringResource(Res.string.group_countdown_finished)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(dates, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun SignInCard() {
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(Res.string.group_signin_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.group_signin_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun MemberLine(member: TripMemberDto, isMe: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MemberAvatar(member.displayName, member.memberId, isOwner = member.role == Role.OWNER, isGuest = member.isGuest, size = 40.dp)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                member.displayName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isMe) {
                Text(
                    " " + stringResource(Res.string.members_you),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (member.isGuest) {
            Tag(stringResource(Res.string.group_guest_tag))
        } else if (member.role != Role.EDITOR) {
            Tag(stringResource(if (member.role == Role.OWNER) Res.string.role_owner else Res.string.role_viewer))
        } else {
            Tag(stringResource(Res.string.role_editor))
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val previewTrip = TripDto(
    id = "t", ownerId = "u1", title = "Bali 4 hari", destination = "Bali",
    startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
    myRole = Role.OWNER, createdAt = Instant.parse("2026-10-01T00:00:00Z"), updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
)

private val previewMembers = listOf(
    TripMemberDto("t", "m1", "u1", "Septian", null, Role.OWNER, Instant.parse("2026-10-01T00:00:00Z")),
    TripMemberDto("t", "m2", "u2", "Dinda", null, Role.EDITOR, Instant.parse("2026-10-02T00:00:00Z")),
    TripMemberDto("t", "m3", null, "Rina", null, Role.VIEWER, Instant.parse("2026-10-03T00:00:00Z")),
)

private val previewPolls = listOf(
    PollDto(
        id = "p1", tripId = "t", kind = PollKind.DATE, question = "Kapan berangkat?", createdByMemberId = "m2", createdByName = "Dinda",
        createdAt = Instant.parse("2026-10-25T00:00:00Z"), closesAt = Instant.parse("2026-10-27T00:00:00Z"),
        status = PollStatus.OPEN, memberCount = 3, votedMemberIds = listOf("m2"),
    ),
    PollDto(
        id = "p2", tripId = "t", kind = PollKind.FOOD, question = "Makan malam di mana?", createdByMemberId = "m1", createdByName = "Septian",
        createdAt = Instant.parse("2026-10-24T00:00:00Z"), closesAt = Instant.parse("2026-10-25T00:00:00Z"),
        status = PollStatus.DECIDED, memberCount = 3, votedMemberIds = listOf("m1", "m2", "m3"), myVotes = listOf("o"),
    ),
)

@Preview
@Composable
private fun GroupTabPreview() = IrisPreview {
    GroupContent(
        previewTrip, LocalDate(2026, 11, 2), previewMembers, signedIn = true, myUserId = "u1", polls = previewPolls, myMemberId = "m1", isOwner = true,
        hasNeeds = false, needs = {}, onOpenMembers = {}, onOpenPoll = {},
        rounds = listOf(
            RoundSummaryDto(
                id = "r", tripId = "t", createdByUserId = "u2", createdByName = "Dinda", createdAt = Instant.parse("2026-10-23T00:00:00Z"),
                closesAt = Instant.parse("2026-10-24T00:00:00Z"), placeCount = 12, myVoteCount = 12,
                decidedAt = Instant.parse("2026-10-24T02:00:00Z"), chosenCount = 3,
            ),
        ),
        now = Instant.parse("2026-11-02T00:00:00Z"),
    )
}

@Preview
@Composable
private fun GroupTabGuestPreview() = IrisPreview {
    GroupContent(
        previewTrip, LocalDate(2026, 11, 22), emptyList(), signedIn = false, myUserId = null, polls = emptyList(), myMemberId = null, isOwner = false,
        hasNeeds = false, needs = {}, onOpenMembers = {}, onOpenPoll = {},
    )
}
