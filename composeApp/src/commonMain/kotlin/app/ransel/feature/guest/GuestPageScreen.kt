package app.ransel.feature.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.ransel.content.getString
import app.ransel.content.stringResource
import app.ransel.feature.expenses.balanceColors
import app.ransel.shared.dto.GuestMemberDto
import app.ransel.shared.dto.GuestMoneyDto
import app.ransel.shared.dto.GuestPageDto
import app.ransel.shared.dto.GuestTransferDto
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.PollOptionDto
import app.ransel.shared.model.PackingCategory
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollStatus
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.format.deadlineText
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.RanselLogoTile
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*
import kotlin.time.Instant

/**
 * The guest page (web, no account): a poll link (`/p/<code>`), or an invite link opened without signing in
 * ([invite] = true: "Gabung dengan akun" leads to sign-in, the rest is "Lihat tanpa akun").
 */
@Composable
fun GuestPageScreen(
    code: String,
    invite: Boolean,
    onSignIn: () -> Unit,
    onOpenPoll: (tripId: String, pollId: String) -> Unit,
    viewModel: GuestPageViewModel = koinViewModel(key = "guest-$code") { parametersOf(code) },
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.noticeShown()
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(
            getString(
                when (notice) {
                    GuestNotice.NAME_TAKEN -> Res.string.guest_name_taken
                    GuestNotice.STALE -> Res.string.guest_stale
                    GuestNotice.CLOSED -> Res.string.guest_closed
                    GuestNotice.SENT -> Res.string.guest_sent
                    GuestNotice.FAILED -> Res.string.guest_failed
                },
            ),
        )
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        GuestPageContent(
            state = state,
            invite = invite,
            actions = GuestActions(
                onSignIn = onSignIn,
                onOpenPoll = { state.memberPoll?.let { (trip, poll) -> onOpenPoll(trip, poll) } },
                onRetry = viewModel::load,
                onPick = viewModel::pick,
                onStartWriting = viewModel::startWriting,
                onName = viewModel::onName,
                onSubmitName = viewModel::submitName,
                onChange = viewModel::startChanging,
                onCancelChange = viewModel::cancelChanging,
                onToggle = viewModel::toggle,
                onSend = viewModel::send,
                onTick = viewModel::tick,
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { UndoSnackbar(message = it.visuals.message) }
    }
}

/** Everything the page can do, so the content stays a plain function of its state. */
class GuestActions(
    val onSignIn: () -> Unit = {},
    val onOpenPoll: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPick: (String) -> Unit = {},
    val onStartWriting: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onSubmitName: () -> Unit = {},
    val onChange: () -> Unit = {},
    val onCancelChange: () -> Unit = {},
    val onToggle: (PollDto, String) -> Unit = { _, _ -> },
    val onSend: (PollDto) -> Unit = {},
    val onTick: (PersonalItemDto) -> Unit = {},
)

@Composable
fun GuestPageContent(state: GuestPageUiState, invite: Boolean, actions: GuestActions, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize()) {
        // top bar: brand and "Masuk"
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RanselLogoTile(size = 32.dp)
            Text("RANSEL", style = MaterialTheme.typography.titleMedium, color = scheme.primary, modifier = Modifier.padding(start = 8.dp).weight(1f))
            TextButton(onClick = actions.onSignIn) { Text(stringResource(Res.string.guest_sign_in)) }
        }
        val page = state.page
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            page == null -> LinkOff(actions.onRetry, offline = state.failure != app.ransel.shared.api.ErrorCode.NOT_FOUND)
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TripHeader(page, invite)
                if (invite) {
                    Button(onClick = actions.onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.guest_join_account))
                    }
                    Text(stringResource(Res.string.guest_view_without), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                }
                if (state.memberPoll != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.primaryContainer).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Res.string.guest_open_app), style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                        Button(onClick = actions.onOpenPoll) { Text(stringResource(Res.string.guest_open_app_button)) }
                    }
                }
                if (state.choosingName) NameChooser(state, actions) else state.me?.let { me -> VotingAs(me, actions.onChange) }
                val polls = page.polls
                if (polls.isEmpty() && !invite) {
                    Text(stringResource(Res.string.guest_no_polls), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                polls.forEach { poll ->
                    GuestPollCard(poll, page.members, state.selections[poll.id].orEmpty(), canVote = state.me != null && !state.choosingName, busy = state.busy, actions)
                }
                val money = page.myMoney
                val me = state.me
                if (money != null && me != null && !state.choosingName) GuestMoneyCard(money, me.memberId, page.ownerName)
                if (page.packing.isNotEmpty() && !state.choosingName) GuestPacking(page.ownerName, page.packing, actions.onTick)
                TextButton(onClick = actions.onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.guest_footer))
                }
            }
        }
    }
}

@Composable
private fun LinkOff(onRetry: () -> Unit, offline: Boolean) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(if (offline) Res.string.guest_failed else Res.string.guest_link_off_title), style = MaterialTheme.typography.titleLarge)
        if (!offline) {
            Text(stringResource(Res.string.guest_link_off_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.guest_retry)) }
        }
    }
}

@Composable
private fun TripHeader(page: GuestPageDto, invite: Boolean) {
    val dates = rememberDateFormatter()
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (invite) stringResource(Res.string.guest_invite_title, page.ownerName)
            else stringResource(Res.string.guest_poll_asks, page.ownerName, page.tripTitle),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        if (invite) Text(page.tripTitle, style = MaterialTheme.typography.headlineMedium)
        Text(listOf(dates.tripRange(page.startDate, page.endDate), page.destination).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

/** "Kamu yang mana?": members without an account, then "Nama saya tidak ada". */
@Composable
private fun NameChooser(state: GuestPageUiState, actions: GuestActions) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.guest_who_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.guest_who_body), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        state.pickable.forEach { member ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(IrisShape.md)
                    .clickable(enabled = !member.taken && !state.busy, role = Role.Button) { actions.onPick(member.memberId) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MemberAvatar(member.name, member.memberId, isGuest = member.taken, size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(member.name, style = MaterialTheme.typography.bodyLarge, color = if (member.taken) scheme.onSurfaceVariant else scheme.onSurface)
                    if (member.taken) Text(stringResource(Res.string.guest_taken), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (!member.taken) Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
            }
        }
        val same = state.sameName
        when {
            same != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.guest_same_name, same.name), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.onPick(same.memberId) }, enabled = !same.taken) { Text(stringResource(Res.string.guest_same_name_yes)) }
                    OutlinedButton(onClick = actions.onStartWriting) { Text(stringResource(Res.string.guest_same_name_no)) }
                }
            }
            state.writing -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IrisTextField(
                    label = stringResource(Res.string.guest_name_label),
                    value = state.name,
                    onValueChange = actions.onName,
                    error = state.nameError?.let { nameError(it) },
                )
                LoadingButton(onClick = actions.onSubmitName, loading = state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.guest_name_continue))
                }
            }
            else -> OutlinedButton(onClick = actions.onStartWriting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Symbol(Symbols.personAdd)
                Text(stringResource(Res.string.guest_not_listed), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.me != null) TextButton(onClick = actions.onCancelChange) { Text(stringResource(Res.string.guest_cancel)) }
    }
}

@Composable
private fun nameError(code: String) = stringResource(if (code == "TOO_LONG") Res.string.guest_name_error_TOO_LONG else Res.string.guest_name_error_REQUIRED)

/** "Memilih sebagai Bima · Bukan Bima? Ganti". */
@Composable
private fun VotingAs(me: GuestMemberDto, onChange: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MemberAvatar(me.name, me.memberId, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.guest_voting_as, me.name), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.guest_not_me, me.name), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        TextButton(onClick = onChange) { Text(stringResource(Res.string.guest_change)) }
    }
}

@Composable
private fun GuestPollCard(poll: PollDto, members: List<GuestMemberDto>, selected: Set<String>, canVote: Boolean, busy: Boolean, actions: GuestActions) {
    val scheme = MaterialTheme.colorScheme
    val open = poll.status == PollStatus.OPEN
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(poll.question, style = MaterialTheme.typography.titleLarge)
        Text(
            if (open) stringResource(Res.string.guest_closes, deadlineText(poll.closesAt, TimeZone.currentSystemDefault()), poll.votedMemberIds.size, poll.memberCount)
            else stringResource(Res.string.guest_poll_over, poll.votedMemberIds.size, poll.memberCount),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        poll.options.forEach { option ->
            GuestOption(option, poll, members, on = option.id in selected, enabled = open && canVote) { actions.onToggle(poll, option.id) }
        }
        if (open) {
            LoadingButton(
                onClick = { actions.onSend(poll) },
                loading = busy,
                enabled = canVote && selected != poll.myVotes.toSet(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(if (canVote) Res.string.guest_send else Res.string.guest_pick_first)) }
        }
    }
}

@Composable
private fun GuestOption(option: PollOptionDto, poll: PollDto, members: List<GuestMemberDto>, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(IrisShape.md)
            .background(if (on) scheme.primaryContainer else scheme.surface)
            .border(if (on) 2.dp else 1.dp, if (on) scheme.primary else scheme.outlineVariant, IrisShape.md)
            .clickable(enabled = enabled, role = if (poll.multiChoice) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(if (poll.multiChoice) IrisShape.xs else CircleShape)
                .then(if (on) Modifier.background(scheme.primary) else Modifier.border(2.dp, scheme.outline, if (poll.multiChoice) IrisShape.xs else CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (on) Symbol(Symbols.check, size = 16.dp, tint = scheme.onPrimary) }
        Column(Modifier.weight(1f)) {
            val start = option.startDate
            val end = option.endDate
            Text(if (start != null && end != null) dates.range(start, end) else option.label, style = MaterialTheme.typography.titleSmall)
            val voters = option.voterIds.mapNotNull { id -> members.firstOrNull { it.memberId == id } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.guest_votes, option.voteCount), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                voters.take(5).forEach { MemberAvatar(it.name, it.memberId, size = 20.dp) }
            }
        }
    }
}

/**
 * "Bagianmu": the guest's own share, what they paid and their balance (coloured like the Saldo card: green when money
 * comes back, red when they owe), plus the "Supaya lunas" transfers that involve them. Read-only: a guest has no
 * account to record a payment with. No design yet (needs a design review).
 */
@Composable
internal fun GuestMoneyCard(money: GuestMoneyDto, myMemberId: String, ownerName: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val currency = money.baseCurrency
    Column(
        modifier.fillMaxWidth().clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.guest_money_title), style = MaterialTheme.typography.titleMedium)
        MoneyLine(stringResource(Res.string.guest_money_share), formatMoney(money.shareMinor, currency))
        MoneyLine(stringResource(Res.string.guest_money_paid), formatMoney(money.paidMinor, currency))
        val (container, content) = balanceColors(money.balanceMinor)
        val amount = formatMoney(kotlin.math.abs(money.balanceMinor), currency)
        Row(
            Modifier.fillMaxWidth().clip(IrisShape.md).background(container).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Symbol(Symbols.payments, filled = true, tint = content)
            Text(
                when {
                    money.balanceMinor > 0 -> stringResource(Res.string.guest_money_collect, amount)
                    money.balanceMinor < 0 -> stringResource(Res.string.guest_money_owe, amount)
                    else -> stringResource(Res.string.guest_money_even)
                },
                style = MaterialTheme.typography.titleSmall,
                color = content,
            )
        }
        money.transfers.forEach { t ->
            val value = formatMoney(t.amountMinor, currency)
            Text(
                if (t.fromMemberId == myMemberId) stringResource(Res.string.guest_money_pay_to, value, t.toName)
                else stringResource(Res.string.guest_money_get_from, t.fromName, value),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (money.transfers.any { it.fromMemberId == myMemberId }) {
            Text(stringResource(Res.string.guest_money_note, ownerName), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MoneyLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** The guest's lines from the owner's list (they only tick them). */
@Composable
private fun GuestPacking(ownerName: String, items: List<PersonalItemDto>, onTick: (PersonalItemDto) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(Res.string.guest_packing_title, ownerName), style = MaterialTheme.typography.titleMedium)
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { onTick(item) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(22.dp).clip(IrisShape.xs)
                        .then(if (item.isPacked) Modifier.background(scheme.primary) else Modifier.border(2.dp, scheme.outline, IrisShape.xs)),
                    contentAlignment = Alignment.Center,
                ) { if (item.isPacked) Symbol(Symbols.check, size = 16.dp, tint = scheme.onPrimary) }
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (item.isPacked) TextDecoration.LineThrough else TextDecoration.None),
                    modifier = Modifier.weight(1f),
                )
                if (item.required) {
                    Text(
                        stringResource(Res.string.packing_required),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onErrorContainer,
                        modifier = Modifier.clip(IrisShape.xs).background(scheme.errorContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (item.quantity > 1) Text("×${item.quantity}", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

private val previewStamp = Instant.parse("2026-11-10T14:00:00Z")

private fun previewPage(me: GuestMemberDto?) = GuestPageDto(
    code = "8QX2AB", tripId = "t", tripTitle = "Bali 4 hari", destination = "Bali",
    startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23), ownerName = "Septian",
    members = listOf(
        GuestMemberDto("s", "Septian", hasAccount = true), GuestMemberDto("d", "Dinda", hasAccount = true),
        GuestMemberDto("b", "Bima", hasAccount = false), GuestMemberDto("r", "Rina", hasAccount = false, taken = true),
    ),
    me = me,
    focusPollId = "p",
    polls = listOf(
        PollDto(
            id = "p", tripId = "t", kind = PollKind.STAY, question = "Mau nginep di mana?", createdByMemberId = "s", createdByName = "Septian",
            createdAt = previewStamp, closesAt = previewStamp, status = PollStatus.OPEN, memberCount = 4, votedMemberIds = listOf("s", "d"),
            options = listOf(
                PollOptionDto(id = "a", label = "Villa Kayu Seminyak", version = 1, voteCount = 2, voterIds = listOf("s", "d")),
                PollOptionDto(id = "b", label = "Guesthouse Canggu", version = 1, voteCount = 0),
            ),
        ),
    ),
    packing = if (me == null) emptyList() else listOf(
        PersonalItemDto("i", "t", "b", PackingCategory.CLOTHING, "Jaket", sourceItemId = "l", required = true, createdAt = previewStamp, updatedAt = previewStamp),
    ),
)

@Preview
@Composable
private fun GuestPagePickNamePreview() = IrisPreview {
    GuestPageContent(GuestPageUiState(loading = false, page = previewPage(null)), invite = false, actions = GuestActions())
}

@Preview
@Composable
private fun GuestPageVotingPreview() = IrisPreview {
    val me = GuestMemberDto("b", "Bima", hasAccount = false)
    GuestPageContent(GuestPageUiState(loading = false, page = previewPage(me), selections = mapOf("p" to setOf("a"))), invite = false, actions = GuestActions())
}

@Preview
@Composable
private fun GuestMoneyOwesPreview() = IrisPreview {
    GuestMoneyCard(
        GuestMoneyDto("IDR", paidMinor = 0, shareMinor = 450_000, balanceMinor = -450_000, transfers = listOf(GuestTransferDto("b", "Bima", "s", "Septian", 450_000))),
        myMemberId = "b",
        ownerName = "Septian",
    )
}

@Preview
@Composable
private fun GuestMoneyGetsBackPreview() = IrisPreview {
    GuestMoneyCard(
        GuestMoneyDto(
            "IDR", paidMinor = 900_000, shareMinor = 300_000, balanceMinor = 600_000,
            transfers = listOf(GuestTransferDto("r", "Rina", "b", "Bima", 300_000), GuestTransferDto("d", "Dinda", "b", "Bima", 300_000)),
        ),
        myMemberId = "b",
        ownerName = "Septian",
    )
}

@Preview
@Composable
private fun GuestMoneySettledPreview() = IrisPreview {
    GuestMoneyCard(GuestMoneyDto("IDR", paidMinor = 300_000, shareMinor = 300_000, balanceMinor = 0), myMemberId = "b", ownerName = "Septian")
}
