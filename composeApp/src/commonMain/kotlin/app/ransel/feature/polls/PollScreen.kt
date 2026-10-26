package app.ransel.feature.polls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.feature.comments.CommentButton
import app.ransel.feature.comments.ProvideComments
import app.ransel.shared.domain.PollAction
import app.ransel.shared.domain.actionFor
import app.ransel.shared.domain.stayEstimateMinor
import app.ransel.shared.model.PollPriceUnit
import app.ransel.ui.format.formatMoney
import app.ransel.shared.domain.pollHoursLeft
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.PollOptionDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.CommentTarget
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollStatus
import app.ransel.shared.model.Role as TripRole
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.poll_decide_estimate_no_price
import ransel.composeapp.generated.resources.poll_decide_estimate_total
import ransel.composeapp.generated.resources.poll_decide_estimate
import ransel.composeapp.generated.resources.poll_search_action
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.poll_action_close
import ransel.composeapp.generated.resources.poll_action_decide
import ransel.composeapp.generated.resources.poll_action_delete
import ransel.composeapp.generated.resources.poll_action_dismiss
import ransel.composeapp.generated.resources.poll_action_reopen
import ransel.composeapp.generated.resources.poll_add_option
import ransel.composeapp.generated.resources.poll_anonymous_badge
import ransel.composeapp.generated.resources.poll_by
import ransel.composeapp.generated.resources.poll_closed_notice
import ransel.composeapp.generated.resources.poll_decide_confirm
import ransel.composeapp.generated.resources.poll_decide_dates
import ransel.composeapp.generated.resources.poll_decide_destination
import ransel.composeapp.generated.resources.poll_decide_food
import ransel.composeapp.generated.resources.poll_decide_none
import ransel.composeapp.generated.resources.poll_decide_place
import ransel.composeapp.generated.resources.poll_decide_title
import ransel.composeapp.generated.resources.poll_delete_body
import ransel.composeapp.generated.resources.poll_delete_option_body
import ransel.composeapp.generated.resources.poll_delete_option_title
import ransel.composeapp.generated.resources.poll_delete_title
import ransel.composeapp.generated.resources.poll_dismiss_body
import ransel.composeapp.generated.resources.poll_dismiss_title
import ransel.composeapp.generated.resources.poll_error
import ransel.composeapp.generated.resources.poll_manage
import ransel.composeapp.generated.resources.poll_option_delete
import ransel.composeapp.generated.resources.poll_option_edit
import ransel.composeapp.generated.resources.poll_retry
import ransel.composeapp.generated.resources.poll_stale
import ransel.composeapp.generated.resources.poll_status_closed
import ransel.composeapp.generated.resources.poll_status_decided
import ransel.composeapp.generated.resources.poll_status_open
import ransel.composeapp.generated.resources.poll_status_tied
import ransel.composeapp.generated.resources.poll_tie_body
import ransel.composeapp.generated.resources.poll_tie_pick
import ransel.composeapp.generated.resources.poll_tie_title
import ransel.composeapp.generated.resources.poll_time_left_days
import ransel.composeapp.generated.resources.poll_time_left_hours
import ransel.composeapp.generated.resources.poll_title
import ransel.composeapp.generated.resources.poll_vote_error
import ransel.composeapp.generated.resources.poll_voted_summary
import ransel.composeapp.generated.resources.poll_votes_count
import ransel.composeapp.generated.resources.poll_winner_badge
import ransel.composeapp.generated.resources.poll_leading_pick

/** One poll: vote with a tap, see the counts, and (creator or owner) close, confirm the winner, reopen or delete. */
@Composable
fun PollScreen(
    tripId: String,
    pollId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PollViewModel = koinViewModel(key = "poll-$pollId") { parametersOf(tripId, pollId) },
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                PollEvent.Deleted -> onBack()
                is PollEvent.Notice -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(
                        getString(
                            when (event.notice) {
                                PollNotice.STALE -> Res.string.poll_stale
                                PollNotice.CLOSED -> Res.string.poll_closed_notice
                                PollNotice.VOTE_FAILED, PollNotice.ACTION_FAILED -> Res.string.poll_vote_error
                            },
                        ),
                    )
                }
            }
        }
    }

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        ProvideComments(tripId = tripId, kind = CommentTarget.POLL_OPTION, refreshKey = state.poll?.options?.size ?: 0) {
            PollContent(
                state = state,
                onBack = onBack,
                onRetry = viewModel::load,
                onToggle = viewModel::toggle,
                onAddOption = { viewModel.openOptionSheet(null) },
                onSearch = viewModel::openSearch,
                onEditOption = { viewModel.openOptionSheet(it) },
                onDeleteOption = { viewModel.ask(PollDialog.DeleteOption(it)) },
                onClose = viewModel::close,
                onDecide = { viewModel.ask(PollDialog.Decide(it)) },
                onReopen = viewModel::reopen,
                onDelete = { viewModel.ask(PollDialog.Delete) },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }

    val poll = state.poll
    state.optionSheet?.let { sheet ->
        if (poll != null) {
            PollOptionSheet(
                kind = poll.kind,
                currency = state.currency,
                initial = sheet.option,
                tripStart = null,
                tripEnd = null,
                onSave = viewModel::saveOption,
                onDismiss = viewModel::closeOptionSheet,
                onPreview = viewModel::previewLink,
            )
        }
    }
    state.search?.let { search ->
        if (poll != null) {
            PollSearchSheet(
                kind = poll.kind,
                state = search,
                currency = state.currency,
                destination = state.destination,
                onArea = viewModel::onSearchArea,
                onMin = viewModel::onSearchMin,
                onMax = viewModel::onSearchMax,
                onNeeds = viewModel::onSearchNeeds,
                onSearch = viewModel::runSearch,
                onToggle = viewModel::togglePicked,
                onAdd = viewModel::addPicked,
                onRetry = viewModel::backToSearchForm,
                onDismiss = viewModel::closeSearch,
            )
        }
    }
    when (val dialog = state.dialog) {
        is PollDialog.Decide -> if (poll != null) DecideDialog(poll, dialog, state, viewModel::confirmDialog, viewModel::dismissDialog)
        PollDialog.Delete -> ConfirmDialog(
            title = stringResource(Res.string.poll_delete_title),
            body = stringResource(Res.string.poll_delete_body),
            confirmLabel = stringResource(Res.string.poll_action_delete),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            destructive = true,
            symbol = Symbols.delete,
        )
        is PollDialog.DeleteOption -> ConfirmDialog(
            title = stringResource(Res.string.poll_delete_option_title, dialog.option.label),
            body = stringResource(Res.string.poll_delete_option_body),
            confirmLabel = stringResource(Res.string.poll_option_delete),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            destructive = true,
            symbol = Symbols.delete,
        )
        null -> Unit
    }
}

@Composable
private fun DecideDialog(poll: PollDto, dialog: PollDialog.Decide, state: PollUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val option = dialog.option
    if (option == null) {
        ConfirmDialog(
            title = stringResource(Res.string.poll_dismiss_title),
            body = stringResource(Res.string.poll_dismiss_body),
            confirmLabel = stringResource(Res.string.poll_action_dismiss),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = onConfirm, onDismiss = onDismiss,
        )
        return
    }
    val subtitle = pollOptionSubtitle(option, state.currency)
    // What the winner will do, in words: only when "apply winner" is on and the kind has an action.
    val consequence = when {
        !poll.applyWinner -> stringResource(Res.string.poll_decide_none)
        else -> when (actionFor(poll.kind)) {
            PollAction.SET_TRIP_DATES -> stringResource(Res.string.poll_decide_dates, subtitle ?: option.label)
            PollAction.SET_DESTINATION -> stringResource(Res.string.poll_decide_destination, option.label)
            PollAction.ADD_UNSCHEDULED_PLACE -> stringResource(Res.string.poll_decide_place, option.label)
            PollAction.ADD_ACTIVITY -> stringResource(Res.string.poll_decide_food, option.label)
            PollAction.ADD_ESTIMATE -> option.priceMinor?.takeIf { it > 0 }?.let { price ->
                val estimate = stayEstimateMinor(price, option.priceUnit, state.tripNights, state.members.size)
                if (option.priceUnit == null || option.priceUnit == PollPriceUnit.NIGHT) {
                    stringResource(Res.string.poll_decide_estimate, formatMoney(estimate, state.currency), state.tripNights.coerceAtLeast(1))
                } else {
                    stringResource(Res.string.poll_decide_estimate_total, formatMoney(estimate, state.currency))
                }
            } ?: stringResource(Res.string.poll_decide_estimate_no_price)
            PollAction.NONE -> stringResource(Res.string.poll_decide_none)
        }
    }
    ConfirmDialog(
        title = stringResource(Res.string.poll_decide_title, option.label),
        body = consequence,
        confirmLabel = stringResource(Res.string.poll_decide_confirm),
        dismissLabel = stringResource(Res.string.action_cancel),
        onConfirm = onConfirm, onDismiss = onDismiss,
    )
}

@Composable
fun PollContent(
    state: PollUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (PollOptionDto) -> Unit,
    onAddOption: () -> Unit,
    onEditOption: (PollOptionDto) -> Unit,
    onDeleteOption: (PollOptionDto) -> Unit,
    onClose: () -> Unit,
    onDecide: (PollOptionDto?) -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
    onSearch: () -> Unit = {},
) {
    val poll = state.poll
    Column(modifier.fillMaxSize()) {
        TopBar(poll, state, onBack, onClose, onDecide, onReopen, onDelete)
        when {
            poll == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            poll == null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(Res.string.poll_error), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.poll_retry)) }
            }
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(poll, now)
                if (poll.status == PollStatus.TIED && state.isManager) {
                    Notice(stringResource(Res.string.poll_tie_title), stringResource(Res.string.poll_tie_body))
                }
                val members = state.members.associateBy { it.memberId }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    poll.options.forEach { option ->
                        OptionRow(
                            poll = poll,
                            option = option,
                            currency = state.currency,
                            members = members,
                            selected = option.id in poll.myVotes,
                            winner = poll.decidedOptionId == option.id,
                            canEdit = state.canEditOptions,
                            canPickTie = state.isManager && poll.status == PollStatus.TIED && option.id in poll.tiedOptionIds,
                            canConfirm = state.isManager && poll.status == PollStatus.CLOSED && poll.leaders().singleOrNull()?.id == option.id,
                            enabled = !state.busy,
                            onToggle = { onToggle(option) },
                            onEdit = { onEditOption(option) },
                            onDelete = { onDeleteOption(option) },
                            onDecide = { onDecide(option) },
                        )
                    }
                }
                if (state.canEditOptions) {
                    TextButton(onClick = onAddOption) {
                        Symbol(Symbols.add, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(Res.string.poll_add_option), Modifier.padding(start = 8.dp))
                    }
                    if (state.canSearch) {
                        TextButton(onClick = onSearch) {
                            Symbol(Symbols.autoAwesome, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(Res.string.poll_search_action), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    poll: PollDto?, state: PollUiState, onBack: () -> Unit,
    onClose: () -> Unit, onDecide: (PollOptionDto?) -> Unit, onReopen: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
            Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
        }
        Text(stringResource(Res.string.poll_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
        if (poll != null && state.isManager) {
            Box {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }, contentAlignment = Alignment.Center) {
                    Symbol(Symbols.moreVert, contentDescription = stringResource(Res.string.poll_manage))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = IrisShape.md, containerColor = MaterialTheme.colorScheme.surface) {
                    if (poll.status == PollStatus.OPEN) MenuItem(Symbols.schedule, stringResource(Res.string.poll_action_close)) { menu = false; onClose() }
                    if (poll.status == PollStatus.CLOSED || poll.status == PollStatus.TIED) {
                        MenuItem(Symbols.refresh, stringResource(Res.string.poll_action_reopen)) { menu = false; onReopen() }
                    }
                    if (poll.status != PollStatus.DECIDED) {
                        MenuItem(Symbols.removeDone, stringResource(Res.string.poll_action_dismiss)) { menu = false; onDecide(null) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    MenuItem(Symbols.delete, stringResource(Res.string.poll_action_delete), danger = true) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(symbol: String, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(symbol, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun Header(poll: PollDto, now: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).clip(IrisShape.sm).background(poll.kind.tileColor()), contentAlignment = Alignment.Center) {
                Symbol(poll.kind.symbol, size = 18.dp, tint = IrisTheme.accents.onAccent)
            }
            Text(poll.kind.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Chip(statusLabel(poll.status), highlight = poll.status == PollStatus.OPEN)
            if (poll.anonymous) Chip(stringResource(Res.string.poll_anonymous_badge))
        }
        Text(poll.question, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        val left = if (poll.isOpen) timeLeft(poll.closesAt, now) else null
        Text(
            listOfNotNull(
                stringResource(Res.string.poll_by, poll.createdByName),
                stringResource(Res.string.poll_voted_summary, poll.votedMemberIds.size, poll.memberCount),
                left,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun timeLeft(closesAt: Instant, now: Instant): String {
    val hours = pollHoursLeft(now, closesAt)
    return if (hours >= 48) stringResource(Res.string.poll_time_left_days, (hours + 23) / 24) else stringResource(Res.string.poll_time_left_hours, hours)
}

@Composable
private fun statusLabel(status: PollStatus) = stringResource(
    when (status) {
        PollStatus.OPEN -> Res.string.poll_status_open
        PollStatus.CLOSED -> Res.string.poll_status_closed
        PollStatus.TIED -> Res.string.poll_status_tied
        PollStatus.DECIDED -> Res.string.poll_status_decided
    },
)

@Composable
private fun Chip(text: String, highlight: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Notice(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.md).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OptionRow(
    poll: PollDto,
    option: PollOptionDto,
    currency: String,
    members: Map<String, TripMemberDto>,
    selected: Boolean,
    winner: Boolean,
    canEdit: Boolean,
    canPickTie: Boolean,
    canConfirm: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDecide: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fraction = if (poll.memberCount <= 0) 0f else (option.voteCount.toFloat() / poll.memberCount).coerceIn(0f, 1f)
    val canVote = poll.isOpen && enabled
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .border(if (selected || winner) 2.dp else 1.dp, if (selected || winner) scheme.primary else scheme.outlineVariant, IrisShape.lg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = canVote, role = if (poll.multiChoice) Role.Checkbox else Role.RadioButton, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (poll.isOpen) {
                if (poll.multiChoice) Checkbox(checked = selected, onCheckedChange = null, enabled = canVote)
                else RadioButton(selected = selected, onClick = null, enabled = canVote)
            }
            Column(Modifier.weight(1f)) {
                Text(option.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                pollOptionSubtitle(option, currency)?.takeIf { it != option.label }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            if (winner) Chip(stringResource(Res.string.poll_winner_badge), highlight = true)
            CommentButton(option.id, option.label)
        }
        // counts bar
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(scheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (winner) scheme.primary else scheme.primary.copy(alpha = 0.6f)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(Res.plurals.poll_votes_count, option.voteCount, option.voteCount),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // names only when the poll is not anonymous (the server sends no voter ids otherwise)
            option.voterIds.take(5).forEachIndexed { index, id ->
                val member = members[id]
                MemberAvatar(member?.displayName ?: "?", id, Modifier.offset(x = (-6 * index).dp), isGuest = member?.isGuest == true, size = 24.dp)
            }
        }
        if (canEdit || canPickTie || canConfirm) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canPickTie) {
                    TextButton(onClick = onDecide, enabled = enabled) { Text(stringResource(Res.string.poll_tie_pick)) }
                }
                if (canConfirm) {
                    TextButton(onClick = onDecide, enabled = enabled) { Text(stringResource(Res.string.poll_leading_pick)) }
                }
                if (canEdit) {
                    TextButton(onClick = onEdit, enabled = enabled) { Text(stringResource(Res.string.poll_option_edit)) }
                    TextButton(onClick = onDelete, enabled = enabled) {
                        Text(stringResource(Res.string.poll_option_delete), color = scheme.error)
                    }
                }
            }
        }
    }
}

private val previewNow = Instant.parse("2026-10-26T10:00:00Z")

private val previewPoll = PollDto(
    id = "p", tripId = "t", kind = PollKind.STAY, question = "Menginap di mana?",
    createdByMemberId = "m1", createdByName = "Septian", createdAt = previewNow - 2.hours, closesAt = previewNow + 20.hours,
    status = PollStatus.OPEN, memberCount = 4, votedMemberIds = listOf("m1", "m2"), myVotes = listOf("o1"),
    options = listOf(
        PollOptionDto("o1", "Villa Ubud", priceMinor = 750_000, priceUnit = app.ransel.shared.model.PollPriceUnit.NIGHT, voteCount = 2, voterIds = listOf("m1", "m2")),
        PollOptionDto("o2", "Hotel Kuta", priceMinor = 500_000, priceUnit = app.ransel.shared.model.PollPriceUnit.NIGHT, voteCount = 0),
    ),
)

@Preview
@Composable
private fun PollOpenPreview() = IrisPreview {
    PollContent(
        state = PollUiState(loading = false, poll = previewPoll, myMemberId = "m1", myRole = TripRole.OWNER),
        onBack = {}, onRetry = {}, onToggle = {}, onAddOption = {}, onEditOption = {}, onDeleteOption = {},
        onClose = {}, onDecide = {}, onReopen = {}, onDelete = {}, now = previewNow,
    )
}

@Preview
@Composable
private fun PollTiedPreview() = IrisPreview {
    PollContent(
        state = PollUiState(
            loading = false, myMemberId = "m1", myRole = TripRole.OWNER,
            poll = previewPoll.copy(status = PollStatus.TIED, tiedOptionIds = listOf("o1", "o2"), anonymous = true),
        ),
        onBack = {}, onRetry = {}, onToggle = {}, onAddOption = {}, onEditOption = {}, onDeleteOption = {},
        onClose = {}, onDecide = {}, onReopen = {}, onDelete = {}, now = previewNow,
    )
}
