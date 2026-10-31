package app.ransel.feature.group

import app.ransel.content.stringResource
import app.ransel.ui.components.SectionHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import app.ransel.feature.expenses.balanceColors
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
import app.ransel.shared.model.SettlementStatus
import app.ransel.ui.components.HeroCard
import app.ransel.ui.components.StatusTag
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
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import app.ransel.feature.albums.AlbumPhoto
import app.ransel.feature.albums.displayTitle
import app.ransel.shared.dto.AlbumItemDto
import app.ransel.shared.dto.TripAlbumDto
import ransel.composeapp.generated.resources.album_more_photos
import ransel.composeapp.generated.resources.album_more_photos_plus
import ransel.composeapp.generated.resources.albums_summary
import ransel.composeapp.generated.resources.albums_title
import ransel.composeapp.generated.resources.group_documents
import ransel.composeapp.generated.resources.overview_invite_body
import ransel.composeapp.generated.resources.overview_invite_title
import ransel.composeapp.generated.resources.documents_title
import ransel.composeapp.generated.resources.group_countdown_finished
import ransel.composeapp.generated.resources.group_countdown_ongoing
import ransel.composeapp.generated.resources.group_guest_tag
import ransel.composeapp.generated.resources.group_members_title
import ransel.composeapp.generated.resources.group_needs_empty
import ransel.composeapp.generated.resources.group_need_confirm
import ransel.composeapp.generated.resources.group_need_pay
import ransel.composeapp.generated.resources.group_votings_ongoing
import ransel.composeapp.generated.resources.group_votings_ongoing_empty
import ransel.composeapp.generated.resources.vote_banner_history
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
import ransel.composeapp.generated.resources.group_date_poll_body
import ransel.composeapp.generated.resources.group_date_poll_start
import app.ransel.ui.components.DatePollCard
import ransel.composeapp.generated.resources.role_owner
import ransel.composeapp.generated.resources.role_viewer

/**
 * "Ringkasan", the first tab of every trip: its hub. A countdown, "Perlu tindakanmu" (votings and money that wait for
 * the user), every voting of the trip (polls and place-voting rounds; the other tabs show none), documents and the
 * members, with the ones added by name tagged "Tanpa akun". A solo trip shows only the countdown, documents and, for
 * the owner, "Ajak teman" (owner, 27 Oct 2026). [needs] draws what asks for the user, [hasNeeds] says whether there is
 * anything.
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
    /** "Riwayat voting": the finished votings on their own screen. */
    onOpenVotingHistory: () -> Unit = {},
    /** A money item in "Perlu tindakanmu" opens the Pengeluaran tab. */
    onOpenMoney: () -> Unit = {},
    onOpenDocuments: () -> Unit = {},
    /** The "Album trip" card. */
    onOpenAlbums: () -> Unit = {},
    /** "Ajak teman" on a solo trip (the OWNER); null = not offered. */
    onInvite: (() -> Unit)? = null,
    /** "Mulai polling tanggal" on a trip without dates and no open DATE poll (OWNER, EDITOR); null = not offered. */
    onStartDatePoll: (() -> Unit)? = null,
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
        onOpenVotingHistory = onOpenVotingHistory,
        modifier = modifier,
        owes = state.owes,
        claimsToConfirm = state.claimsToConfirm,
        currency = state.currency,
        onOpenMoney = onOpenMoney,
        onOpenDocuments = onOpenDocuments,
        documentCount = state.documentCount,
        albums = state.albums,
        albumPreview = state.albumPreview,
        albumPreviewMore = state.albumPreviewMore,
        onOpenAlbums = onOpenAlbums,
        solo = state.solo && trip.myRole == Role.OWNER,
        onInvite = onInvite,
        onStartDatePoll = onStartDatePoll,
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
    onOpenVotingHistory: () -> Unit = {},
    now: Instant = Clock.System.now(),
    owes: List<TransferDto> = emptyList(),
    claimsToConfirm: List<SettlementDto> = emptyList(),
    currency: String = trip.baseCurrency,
    onOpenMoney: () -> Unit = {},
    onOpenDocuments: () -> Unit = {},
    documentCount: Int? = null,
    albums: List<TripAlbumDto> = emptyList(),
    albumPreview: List<AlbumItemDto> = emptyList(),
    albumPreviewMore: Boolean = false,
    onOpenAlbums: () -> Unit = {},
    solo: Boolean = false,
    onInvite: (() -> Unit)? = null,
    onStartDatePoll: (() -> Unit)? = null,
) {
    val dates = rememberDateFormatter()
    // a trip without dates shows its open DATE poll as a card under the countdown (design ItineraryPolling), not twice
    val undated = trip.startDate == null || trip.endDate == null
    val datePoll = if (undated) polls.filter { it.kind == PollKind.DATE && it.isOpen }.maxByOrNull { it.createdAt } else null
    val listedPolls = polls.filter { it.id != datePoll?.id }
    val pollNeeds = listedPolls.mapNotNull { poll -> poll.needFor(myMemberId, isOwner)?.let { poll to it } }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        CountdownCard(countdown = countdownFor(trip, today), tripTitle = trip.title, dates = dates.tripRange(trip.startDate, trip.endDate))
        if (signedIn && datePoll != null) {
            DatePollCard(poll = datePoll, mine = datePoll.createdByMemberId == myMemberId, onOpen = { onOpenPoll(datePoll.id) })
        } else if (signedIn && undated && onStartDatePoll != null) {
            StartDatePollCard(onStartDatePoll)
        }

        if (!signedIn) {
            SignInCard()
            return@Column
        }

        if (solo) {
            // no voting, money or members on a trip of one; albums and documents stay, and friends can be invited
            if (albums.isNotEmpty()) Section(stringResource(Res.string.albums_title)) { TripAlbumsCard(albums, albumPreview, albumPreviewMore, onOpenAlbums) }
            Section(stringResource(Res.string.group_documents)) {
                LinkRow(Symbols.receiptLong, stringResource(Res.string.documents_title), documentCount?.toString(), onOpenDocuments)
            }
            if (onInvite != null) InviteCard(onInvite)
            return@Column
        }

        Section(stringResource(Res.string.group_needs_title)) {
            if (hasNeeds) needs()
            pollNeeds.forEach { (poll, need) -> PollNeedCard(poll, need) { onOpenPoll(poll.id) } }
            fun nameOf(id: String) = members.firstOrNull { it.memberId == id }?.displayName ?: "?"
            // Same colours as the Saldo card: money coming to me is green, money I owe is red.
            claimsToConfirm.forEach { claim ->
                MoneyNeedCard(
                    stringResource(Res.string.group_need_confirm, nameOf(claim.fromMemberId), formatMoney(claim.amountMinor, currency)),
                    balanceMinor = claim.amountMinor,
                    onClick = onOpenMoney,
                )
            }
            owes.forEach { t ->
                MoneyNeedCard(
                    stringResource(Res.string.group_need_pay, formatMoney(t.amountMinor, currency), nameOf(t.toMemberId)),
                    balanceMinor = -t.amountMinor,
                    onClick = onOpenMoney,
                )
            }
            if (!hasNeeds && pollNeeds.isEmpty() && owes.isEmpty() && claimsToConfirm.isEmpty()) Text(
                stringResource(Res.string.group_needs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Votings still running that wait for others (the ones waiting for me are in "Perlu tindakanmu" above);
        // finished ones only behind "Riwayat voting".
        val actionRounds = rounds.actionRounds(isOwner, now).toSet()
        val ongoing = votingEntries(
            listedPolls.filter { it.status != PollStatus.DECIDED && it.needFor(myMemberId, isOwner) == null },
            rounds.filter { roundState(now, it.closesAt, it.decidedAt) != RoundState.DECIDED && it !in actionRounds },
        )
        Section(stringResource(Res.string.group_votings_ongoing)) {
            if (ongoing.isEmpty()) {
                Text(
                    stringResource(Res.string.group_votings_ongoing_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ongoing.forEach { VotingEntryRow(it, isOwner, now, onOpenPoll, onOpenRound) }
                }
            }
            val finished = polls.count { it.status == PollStatus.DECIDED } +
                rounds.count { roundState(now, it.closesAt, it.decidedAt) == RoundState.DECIDED }
            LinkRow(Symbols.doneAll, stringResource(Res.string.vote_banner_history), finished.toString(), onOpenVotingHistory)
        }

        // an empty album section is left out: albums are added from the + button (owner, 31 Oct 2026)
        if (albums.isNotEmpty()) Section(stringResource(Res.string.albums_title)) { TripAlbumsCard(albums, albumPreview, albumPreviewMore, onOpenAlbums) }

        Section(stringResource(Res.string.group_documents)) {
            LinkRow(Symbols.receiptLong, stringResource(Res.string.documents_title), documentCount?.toString(), onOpenDocuments)
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

/** One voting in a list: a poll or a place-voting round. */
sealed interface VotingEntry {
    /** When it started (ongoing list) or ended (history), for the order. */
    val at: Instant

    data class Poll(val poll: PollDto, override val at: Instant) : VotingEntry
    data class Round(val round: RoundSummaryDto, override val at: Instant) : VotingEntry
}

/** Polls and place-voting rounds in one list, newest first. */
internal fun votingEntries(polls: List<PollDto>, rounds: List<RoundSummaryDto>, finished: Boolean = false): List<VotingEntry> =
    (
        polls.map { VotingEntry.Poll(it, if (finished) it.closesAt else it.createdAt) } +
            rounds.map { VotingEntry.Round(it, if (finished) it.decidedAt ?: it.closesAt else it.createdAt) }
        ).sortedByDescending { it.at }

@Composable
internal fun VotingEntryRow(
    entry: VotingEntry,
    isOwner: Boolean,
    now: Instant,
    onOpenPoll: (pollId: String) -> Unit,
    onOpenRound: (roundId: String, showResult: Boolean) -> Unit,
) {
    when (entry) {
        is VotingEntry.Poll -> PollRow(entry.poll) { onOpenPoll(entry.poll.id) }
        is VotingEntry.Round -> VoteRoundRow(
            entry.round,
            isOwner = isOwner,
            now = now,
            modifier = Modifier.heightIn(min = 64.dp),
            background = MaterialTheme.colorScheme.surfaceContainerLow,
        ) { onOpenRound(entry.round.id, entry.round.opensResult(isOwner, now)) }
    }
}

/** A row that opens another screen: "Dokumen trip 3 ›", "Riwayat voting 5 ›". [count] null = not known, only the arrow. */
@Composable
private fun LinkRow(symbol: String, title: String, count: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = SemanticsRole.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(symbol, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (count != null) Text(count, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Symbol(Symbols.chevronRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Design "RingkasanAlbum": a few photos of the newest Drive album (one large, three small, the last "+N"), the number of
 * albums and their names; it opens "Album trip".
 */
@Composable
internal fun TripAlbumsCard(albums: List<TripAlbumDto>, preview: List<AlbumItemDto>, more: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .clickable(role = SemanticsRole.Button, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (preview.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(148.dp).clip(IrisShape.md), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PreviewTile(preview.getOrNull(0), Modifier.weight(2f).fillMaxHeight())
                Column(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PreviewTile(preview.getOrNull(1), Modifier.weight(1f).fillMaxHeight())
                        PreviewTile(preview.getOrNull(2), Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PreviewTile(preview.getOrNull(3), Modifier.weight(1f).fillMaxHeight())
                        val rest = preview.size - 5
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            PreviewTile(preview.getOrNull(4), Modifier.fillMaxSize())
                            if (rest > 0 || (more && preview.size >= 5)) {
                                Box(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(if (more) Res.string.album_more_photos_plus else Res.string.album_more_photos, rest.coerceAtLeast(0)),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = IrisTheme.accents.onAccent,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.albums_summary, albums.size), style = MaterialTheme.typography.titleSmall)
                val names = albums.take(2).map { it.displayTitle() }
                Text(
                    (names + listOfNotNull((albums.size - 2).takeIf { it > 0 }?.let { stringResource(Res.string.album_more_photos_plus, it) })).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PreviewTile(item: AlbumItemDto?, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (item != null) AlbumPhoto(item, thumbnail = true, modifier = Modifier.fillMaxSize())
    }
}

/** "Ajak teman": a solo trip can become a group trip. */
@Composable
private fun InviteCard(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceContainerLow)
            .clickable(role = SemanticsRole.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            Symbol(Symbols.personAdd, tint = scheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.overview_invite_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.overview_invite_body), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
    }
}

/** Money that asks something of the user. [balanceMinor] picks the Saldo colours: below zero (I owe) is red. */
@Composable
private fun MoneyNeedCard(text: String, balanceMinor: Long, onClick: () -> Unit) {
    val (container, content) = balanceColors(balanceMinor)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(container)
            .clickable(role = SemanticsRole.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(Symbols.payments, filled = true, tint = content)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = content)
        Symbol(Symbols.chevronRight, tint = content)
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
internal fun PollRow(poll: PollDto, onClick: () -> Unit) {
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
        StatusTag(
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
private fun CountdownCard(countdown: Countdown, tripTitle: String, dates: String) {
    val label = when (countdown) {
        is Countdown.Upcoming -> countdownLabel(countdown.days)
        is Countdown.Ongoing -> stringResource(Res.string.group_countdown_ongoing, countdown.day, countdown.totalDays)
        Countdown.Finished -> stringResource(Res.string.group_countdown_finished)
        Countdown.NoDates -> countdownLabel(null)
    }
    HeroCard(label = tripTitle, value = label, symbol = Symbols.calendarMonth) {
        Text(dates, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** A trip without dates and no DATE poll yet (e.g. it could not be made offline): "Mulai polling tanggal". */
@Composable
private fun StartDatePollCard(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.group_date_poll_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Symbol(Symbols.eventAvailable, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimary)
            Text(stringResource(Res.string.group_date_poll_start), Modifier.padding(start = 8.dp))
        }
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
        SectionHeader(title, trailing = trailing)
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
            StatusTag(stringResource(Res.string.group_guest_tag))
        } else if (member.role != Role.EDITOR) {
            StatusTag(stringResource(if (member.role == Role.OWNER) Res.string.role_owner else Res.string.role_viewer))
        } else {
            StatusTag(stringResource(Res.string.role_editor))
        }
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

/** "Perlu tindakanmu" with money both ways: a payment to confirm (green) and a debt (red, like Saldo). */
@Preview
@Composable
private fun GroupTabMoneyNeedsPreview() = IrisPreview {
    GroupContent(
        previewTrip, LocalDate(2026, 11, 2), previewMembers, signedIn = true, myUserId = "u1", polls = emptyList(), myMemberId = "m1", isOwner = true,
        hasNeeds = false, needs = {}, onOpenMembers = {}, onOpenPoll = {},
        owes = listOf(TransferDto(fromMemberId = "m1", toMemberId = "m2", amountMinor = 150_000)),
        claimsToConfirm = listOf(
            SettlementDto(
                id = "s", tripId = "t", fromMemberId = "m3", toMemberId = "m1", amountMinor = 75_000,
                settledAt = Instant.parse("2026-11-01T00:00:00Z"), status = SettlementStatus.CLAIMED_PAID,
                createdAt = Instant.parse("2026-11-01T00:00:00Z"), updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
            ),
        ),
        now = Instant.parse("2026-11-02T00:00:00Z"),
    )
}

@Preview
@Composable
private fun GroupTabUndatedPreview() = IrisPreview {
    GroupContent(
        previewTrip.copy(startDate = null, endDate = null), LocalDate(2026, 11, 2), previewMembers.take(1), signedIn = true, myUserId = "u1",
        polls = emptyList(), myMemberId = "m1", isOwner = true,
        hasNeeds = false, needs = {}, onOpenMembers = {}, onOpenPoll = {}, solo = true, onInvite = {}, onStartDatePoll = {},
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
