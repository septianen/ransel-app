package app.ransel.admin.feature.users

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.action_adjust_credits
import app.ransel.admin.generated.resources.action_balance
import app.ransel.admin.generated.resources.action_disable
import app.ransel.admin.generated.resources.action_enable
import app.ransel.admin.generated.resources.action_password_reset
import app.ransel.admin.generated.resources.action_password_reset_from
import app.ransel.admin.generated.resources.action_self_note
import app.ransel.admin.generated.resources.action_sign_out_all
import app.ransel.admin.generated.resources.actions_note
import app.ransel.admin.generated.resources.actions_title
import app.ransel.admin.generated.resources.adjust_done_add
import app.ransel.admin.generated.resources.adjust_done_subtract
import app.ransel.admin.generated.resources.audit_credits_adjust
import app.ransel.admin.generated.resources.audit_disable
import app.ransel.admin.generated.resources.audit_empty
import app.ransel.admin.generated.resources.audit_enable
import app.ransel.admin.generated.resources.audit_password_reset
import app.ransel.admin.generated.resources.audit_sign_out_all
import app.ransel.admin.generated.resources.audit_title
import app.ransel.admin.generated.resources.audit_settings_update
import app.ransel.admin.generated.resources.audit_user_create
import app.ransel.admin.generated.resources.audit_users_export
import app.ransel.admin.generated.resources.confirm_disable_body
import app.ransel.admin.generated.resources.confirm_disable_ok
import app.ransel.admin.generated.resources.confirm_disable_title
import app.ransel.admin.generated.resources.confirm_enable_body
import app.ransel.admin.generated.resources.confirm_enable_ok
import app.ransel.admin.generated.resources.confirm_enable_title
import app.ransel.admin.generated.resources.confirm_password_reset_body
import app.ransel.admin.generated.resources.confirm_password_reset_ok
import app.ransel.admin.generated.resources.confirm_password_reset_title
import app.ransel.admin.generated.resources.confirm_sign_out_all_body
import app.ransel.admin.generated.resources.confirm_sign_out_all_ok
import app.ransel.admin.generated.resources.confirm_sign_out_all_title
import app.ransel.admin.generated.resources.detail_back
import app.ransel.admin.generated.resources.detail_id
import app.ransel.admin.generated.resources.detail_joined
import app.ransel.admin.generated.resources.detail_last_active
import app.ransel.admin.generated.resources.detail_stat_balance
import app.ransel.admin.generated.resources.detail_stat_expenses
import app.ransel.admin.generated.resources.detail_stat_free_today
import app.ransel.admin.generated.resources.detail_stat_trips
import app.ransel.admin.generated.resources.detail_trips_empty
import app.ransel.admin.generated.resources.detail_trips_privacy
import app.ransel.admin.generated.resources.detail_trips_title
import app.ransel.admin.generated.resources.device_app_version
import app.ransel.admin.generated.resources.device_push
import app.ransel.admin.generated.resources.device_seen
import app.ransel.admin.generated.resources.device_session
import app.ransel.admin.generated.resources.devices_empty
import app.ransel.admin.generated.resources.devices_title
import app.ransel.admin.generated.resources.done_disable
import app.ransel.admin.generated.resources.done_enable
import app.ransel.admin.generated.resources.done_password_reset
import app.ransel.admin.generated.resources.done_sign_out_all
import app.ransel.admin.generated.resources.ledger_action_next_batch
import app.ransel.admin.generated.resources.ledger_action_place_detail
import app.ransel.admin.generated.resources.ledger_action_receipt
import app.ransel.admin.generated.resources.ledger_action_search
import app.ransel.admin.generated.resources.ledger_adjust
import app.ransel.admin.generated.resources.ledger_col_balance
import app.ransel.admin.generated.resources.ledger_col_delta
import app.ransel.admin.generated.resources.ledger_col_kind
import app.ransel.admin.generated.resources.ledger_col_note
import app.ransel.admin.generated.resources.ledger_col_time
import app.ransel.admin.generated.resources.ledger_empty
import app.ransel.admin.generated.resources.ledger_kind_adjustment
import app.ransel.admin.generated.resources.ledger_kind_refund
import app.ransel.admin.generated.resources.ledger_kind_signup
import app.ransel.admin.generated.resources.ledger_kind_spend
import app.ransel.admin.generated.resources.ledger_kind_topup
import app.ransel.admin.generated.resources.ledger_kind_voucher
import app.ransel.admin.generated.resources.ledger_more
import app.ransel.admin.generated.resources.ledger_source_free
import app.ransel.admin.generated.resources.ledger_source_wallet
import app.ransel.admin.generated.resources.ledger_title
import app.ransel.admin.generated.resources.role_editor
import app.ransel.admin.generated.resources.role_owner
import app.ransel.admin.generated.resources.role_viewer
import app.ransel.admin.generated.resources.time_never
import app.ransel.admin.generated.resources.trip_col_dates
import app.ransel.admin.generated.resources.trip_col_destination
import app.ransel.admin.generated.resources.trip_col_items
import app.ransel.admin.generated.resources.trip_col_members
import app.ransel.admin.generated.resources.trip_col_role
import app.ransel.admin.generated.resources.trip_col_status
import app.ransel.admin.generated.resources.trip_finished
import app.ransel.admin.generated.resources.trip_ongoing
import app.ransel.admin.generated.resources.trip_upcoming
import app.ransel.admin.platform.randomUuid
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.Avatar
import app.ransel.admin.ui.ConfirmDialog
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.Pill
import app.ransel.admin.ui.failureMessage
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.admin.ui.todayWib
import app.ransel.shared.dto.AdminAuditEntryDto
import app.ransel.shared.dto.AdminDeviceDto
import app.ransel.shared.dto.AdminMeDto
import app.ransel.shared.dto.AdminTripDto
import app.ransel.shared.dto.AdminUserDetailDto
import app.ransel.shared.dto.CreditLedgerEntryDto
import app.ransel.shared.model.AdminAction
import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.CreditDirection
import app.ransel.shared.model.CreditLedgerKind
import app.ransel.shared.model.CreditSource
import app.ransel.shared.model.DevicePlatform
import app.ransel.shared.model.Role
import app.ransel.shared.model.UserStatus
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Below this content width the actions column goes above the tables. */
private val TwoColumns = 840.dp

@Composable
fun UserDetailScreen(
    api: AdminApi,
    me: AdminMeDto,
    userId: String,
    snackbar: SnackbarHostState,
    onAccessLost: suspend (ApiFailure) -> Unit,
    onBack: () -> Unit,
) {
    val presenter = rememberPresenter(userId) { UserDetailPresenter(api, it, userId, ::randomUuid, onAccessLost) }
    val state by presenter.state.collectAsState()
    val format = LocalAdminFormat.current
    LaunchedEffect(presenter) {
        presenter.events.collect { event ->
            val text = when (event) {
                is UserDetailEvent.ActionDone -> when (event.action) {
                    UserAction.SIGN_OUT_ALL -> getString(Res.string.done_sign_out_all, event.revokedSessions)
                    UserAction.DISABLE -> getString(Res.string.done_disable)
                    UserAction.ENABLE -> getString(Res.string.done_enable)
                    UserAction.PASSWORD_RESET -> getString(Res.string.done_password_reset)
                }
                is UserDetailEvent.CreditsAdjusted -> getString(
                    if (event.direction == CreditDirection.ADD) Res.string.adjust_done_add else Res.string.adjust_done_subtract,
                    format.number(event.amount),
                    format.number(event.balance),
                )
            }
            snackbar.showSnackbar(text)
        }
    }
    UserDetailContent(
        state = state,
        me = me,
        onBack = onBack,
        onRetry = presenter::reload,
        onMoreLedger = presenter::loadMoreLedger,
        onAsk = presenter::ask,
        onAdjust = presenter::openAdjust,
    )
    val detail = state.detail
    state.confirm?.let { confirm ->
        if (detail != null) ConfirmActionDialog(detail, confirm, presenter::confirm, presenter::dismissConfirm)
    }
    state.adjust?.let { adjust ->
        if (detail != null) {
            AdjustCreditsDialog(
                userName = detail.user.displayName,
                userEmail = detail.user.email,
                credits = detail.credits,
                state = adjust,
                onEdit = presenter::editAdjust,
                onSave = presenter::submitAdjust,
                onDismiss = presenter::closeAdjust,
            )
        }
    }
}

@Composable
fun UserDetailContent(
    state: UserDetailUiState,
    me: AdminMeDto,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMoreLedger: () -> Unit,
    onAsk: (UserAction) -> Unit,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 44.dp)) {
            Symbol(Symbols.arrowBack, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.width(6.dp))
            Text(stringResource(Res.string.detail_back), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val detail = state.detail
        when {
            detail == null && state.error != null -> AdminCard { ErrorPanel(state.error, onRetry) }
            detail == null -> LoadingPanel()
            else -> {
                ProfileCard(detail)
                val main: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TripsCard(detail.trips)
                        LedgerCard(state, onMoreLedger, onAdjust)
                    }
                }
                val aside: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActionsCard(detail, me, onAsk, onAdjust)
                        DevicesCard(detail.devices)
                        AuditCard(state.audit)
                    }
                }
                // Not FlowRow: it measures intrinsically, which the tables' BoxWithConstraints cannot answer.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= TwoColumns) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            main(Modifier.weight(2f))
                            aside(Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            aside(Modifier.fillMaxWidth())
                            main(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(detail: AdminUserDetailDto) {
    val user = detail.user
    val format = LocalAdminFormat.current
    val scheme = MaterialTheme.colorScheme
    AdminCard(Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user.displayName, user.id, size = 72.dp)
            Column(Modifier.weight(1f).widthIn(min = 260.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(user.displayName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    StatusPill(user.status)
                }
                Text(
                    user.email + " · " + stringResource(Res.string.detail_id, user.id),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    stringResource(Res.string.detail_joined, format.date(user.joinedAt)) + " · " +
                        stringResource(Res.string.detail_last_active, user.lastActiveAt?.let { format.relative(it) } ?: stringResource(Res.string.time_never)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(format.number(user.tripCount), stringResource(Res.string.detail_stat_trips))
                Stat(format.number(detail.expenseCount), stringResource(Res.string.detail_stat_expenses))
                Stat(
                    format.number(detail.credits.balance),
                    stringResource(Res.string.detail_stat_balance),
                    color = if (detail.credits.available < detail.credits.costs.search) scheme.error else scheme.onSurface,
                )
                Stat(
                    "${format.number(detail.credits.dailyFreeLeft)}/${format.number(detail.credits.dailyFreeLimit)}",
                    stringResource(Res.string.detail_stat_free_today),
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}

/** A simple table: a header row and data rows with weighted columns, scrolling sideways below [minWidth]. */
@Composable
private fun Table(
    headers: List<String>,
    weights: List<Float>,
    endAligned: Set<Int>,
    minWidth: Dp,
    rows: List<List<@Composable () -> Unit>>,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = if (maxWidth > minWidth) maxWidth else minWidth
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(width).padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    headers.forEachIndexed { i, header ->
                        TableCell(weights[i], i in endAligned) {
                            Text(header, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                rows.forEach { cells ->
                    HorizontalDivider(color = scheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        cells.forEachIndexed { i, cell -> TableCell(weights[i], i in endAligned) { cell() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TableCell(weight: Float, end: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.weight(weight).padding(horizontal = 12.dp), contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) {
        content()
    }
}

@Composable
private fun CardWithTable(content: @Composable ColumnScope.() -> Unit) {
    AdminCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Column(Modifier.padding(top = 20.dp, bottom = 8.dp), content = content)
    }
}

@Composable
private fun TripsCard(trips: List<AdminTripDto>) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val body = MaterialTheme.typography.bodyMedium
    val today = todayWib()
    CardWithTable {
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionTitle(stringResource(Res.string.detail_trips_title))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Symbol(Symbols.lock, size = 16.dp, tint = scheme.onSurfaceVariant)
                Text(stringResource(Res.string.detail_trips_privacy), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        if (trips.isEmpty()) {
            Text(
                stringResource(Res.string.detail_trips_empty),
                style = body,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            return@CardWithTable
        }
        Box(Modifier.padding(top = 10.dp)) {
            Table(
                headers = listOf(
                    stringResource(Res.string.trip_col_destination), stringResource(Res.string.trip_col_dates),
                    stringResource(Res.string.trip_col_role), stringResource(Res.string.trip_col_members),
                    stringResource(Res.string.trip_col_items), stringResource(Res.string.trip_col_status),
                ),
                weights = listOf(1.6f, 1.6f, 1f, 0.8f, 0.7f, 1.2f),
                endAligned = setOf(3, 4),
                minWidth = 620.dp,
                rows = trips.map { trip ->
                    listOf<@Composable () -> Unit>(
                        { Text(trip.destination, style = body, fontWeight = FontWeight.Bold) },
                        {
                            // a trip whose dates are still being polled has none: "—"
                            val start = trip.startDate
                            val end = trip.endDate
                            Text(if (start != null && end != null) format.dateRange(start, end) else "—", style = body, color = scheme.onSurfaceVariant)
                        },
                        { Text(roleLabel(trip.role), style = body) },
                        { Text(format.number(trip.memberCount), style = body) },
                        { Text(format.number(trip.itemCount), style = body) },
                        {
                            val start = trip.startDate
                            val end = trip.endDate
                            when {
                                // no dates yet = still being planned
                                start == null || end == null || today < start -> Pill(stringResource(Res.string.trip_upcoming), scheme.tertiaryContainer, scheme.onTertiaryContainer)
                                today > end -> Pill(stringResource(Res.string.trip_finished), scheme.surfaceContainer, scheme.onSurfaceVariant)
                                else -> Pill(stringResource(Res.string.trip_ongoing), scheme.primaryContainer, scheme.onPrimaryContainer)
                            }
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun roleLabel(role: Role): String = stringResource(
    when (role) {
        Role.OWNER -> Res.string.role_owner
        Role.EDITOR -> Res.string.role_editor
        Role.VIEWER -> Res.string.role_viewer
    },
)

@Composable
private fun LedgerCard(state: UserDetailUiState, onMore: () -> Unit, onAdjust: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val body = MaterialTheme.typography.bodyMedium
    CardWithTable {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(Res.string.ledger_title), Modifier.weight(1f))
            TextButton(onClick = onAdjust, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(Res.string.ledger_adjust)) }
        }
        if (state.ledger.isEmpty()) {
            Box(Modifier.padding(20.dp)) {
                if (state.ledgerLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.ledger_empty), style = body, color = scheme.onSurfaceVariant)
            }
            return@CardWithTable
        }
        Box(Modifier.padding(top = 4.dp)) {
            Table(
                headers = listOf(
                    stringResource(Res.string.ledger_col_time), stringResource(Res.string.ledger_col_kind),
                    stringResource(Res.string.ledger_col_note), stringResource(Res.string.ledger_col_delta),
                    stringResource(Res.string.ledger_col_balance),
                ),
                weights = listOf(1.2f, 1.2f, 2.6f, 0.8f, 0.7f),
                endAligned = setOf(3, 4),
                minWidth = 640.dp,
                rows = state.ledger.map { entry ->
                    listOf<@Composable () -> Unit>(
                        { Text(format.dateTime(entry.createdAt), style = body, color = scheme.onSurfaceVariant) },
                        { LedgerKindPill(entry.kind) },
                        { Text(ledgerNote(entry), style = body, color = scheme.onSurfaceVariant) },
                        {
                            Text(
                                format.signed(entry.delta),
                                style = body,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.delta > 0) scheme.primary else scheme.onSurface,
                            )
                        },
                        { Text(entry.balanceAfter?.let { format.number(it) } ?: "—", style = body) },
                    )
                },
            )
        }
        if (state.olderLedgerEntries > 0) {
            HorizontalDivider(color = scheme.outlineVariant)
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMore, enabled = !state.ledgerLoading, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(stringResource(Res.string.ledger_more, format.number(state.olderLedgerEntries)))
                }
                if (state.ledgerLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun LedgerKindPill(kind: CreditLedgerKind) {
    val scheme = MaterialTheme.colorScheme
    val (label, colors) = when (kind) {
        CreditLedgerKind.SPEND -> Res.string.ledger_kind_spend to (scheme.surfaceContainer to scheme.onSurfaceVariant)
        CreditLedgerKind.VOUCHER -> Res.string.ledger_kind_voucher to (scheme.primaryContainer to scheme.onPrimaryContainer)
        CreditLedgerKind.TOPUP -> Res.string.ledger_kind_topup to (scheme.primaryContainer to scheme.onPrimaryContainer)
        CreditLedgerKind.SIGNUP_GRANT -> Res.string.ledger_kind_signup to (scheme.tertiaryContainer to scheme.onTertiaryContainer)
        CreditLedgerKind.REFUND -> Res.string.ledger_kind_refund to (scheme.secondaryContainer to scheme.onSecondaryContainer)
        CreditLedgerKind.ADJUSTMENT -> Res.string.ledger_kind_adjustment to (IrisTheme.accents.yellow.copy(alpha = 0.22f) to scheme.onSurface)
    }
    Pill(stringResource(label), colors.first, colors.second)
}

/** Details column: the admin's reason for an adjustment, what a spend paid for, the voucher code. Never trip content. */
@Composable
private fun ledgerNote(entry: CreditLedgerEntryDto): String = when (entry.kind) {
    CreditLedgerKind.ADJUSTMENT -> entry.note.orEmpty()
    CreditLedgerKind.SPEND -> {
        val action = stringResource(
            when (entry.action) {
                CreditAction.NEXT_BATCH -> Res.string.ledger_action_next_batch
                CreditAction.PLACE_DETAIL -> Res.string.ledger_action_place_detail
                CreditAction.RECEIPT -> Res.string.ledger_action_receipt
                CreditAction.SEARCH, null -> Res.string.ledger_action_search
            },
        )
        val source = when (entry.source) {
            CreditSource.FREE_DAILY -> stringResource(Res.string.ledger_source_free)
            CreditSource.WALLET -> stringResource(Res.string.ledger_source_wallet)
            null -> null
        }
        if (source != null) "$action ($source)" else action
    }
    CreditLedgerKind.VOUCHER -> entry.ref.orEmpty()
    else -> entry.note.orEmpty()
}

@Composable
private fun ActionsCard(detail: AdminUserDetailDto, me: AdminMeDto, onAsk: (UserAction) -> Unit, onAdjust: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val self = detail.user.id == me.id
    AdminCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(Res.string.actions_title), Modifier.padding(bottom = 4.dp))
            ActionButton(
                symbol = Symbols.savings,
                tint = scheme.primary,
                label = stringResource(Res.string.action_adjust_credits),
                trailing = stringResource(Res.string.action_balance, format.number(detail.credits.balance)),
                onClick = onAdjust,
            )
            // Hidden until an email provider is connected (admin-spec "Still open").
            if (me.passwordResetEmailEnabled && detail.user.status != UserStatus.DELETED) {
                ActionButton(
                    symbol = Symbols.mail,
                    tint = IrisTheme.accents.blue,
                    label = stringResource(Res.string.action_password_reset),
                    caption = stringResource(Res.string.action_password_reset_from),
                    onClick = { onAsk(UserAction.PASSWORD_RESET) },
                )
            }
            ActionButton(
                symbol = Symbols.logout,
                tint = scheme.onSurfaceVariant,
                label = stringResource(Res.string.action_sign_out_all),
                onClick = { onAsk(UserAction.SIGN_OUT_ALL) },
            )
            when {
                detail.user.status == UserStatus.DISABLED -> ActionButton(
                    symbol = Symbols.checkCircle,
                    tint = scheme.primary,
                    label = stringResource(Res.string.action_enable),
                    onClick = { onAsk(UserAction.ENABLE) },
                )
                !self && detail.user.status == UserStatus.ACTIVE -> ActionButton(
                    symbol = Symbols.noAccounts,
                    tint = scheme.onErrorContainer,
                    label = stringResource(Res.string.action_disable),
                    container = scheme.errorContainer,
                    content = scheme.onErrorContainer,
                    onClick = { onAsk(UserAction.DISABLE) },
                )
            }
            Text(
                stringResource(if (self) Res.string.action_self_note else Res.string.actions_note),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ActionButton(
    symbol: String,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null,
    caption: String? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onClick,
        shape = IrisShape.md,
        border = BorderStroke(1.dp, if (container == MaterialTheme.colorScheme.surface) MaterialTheme.colorScheme.outlineVariant else container),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = container, contentColor = content),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Symbol(symbol, size = 20.dp, tint = tint)
        Box(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DevicesCard(devices: List<AdminDeviceDto>) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    AdminCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(Res.string.devices_title))
            if (devices.isEmpty()) {
                Text(stringResource(Res.string.devices_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            devices.forEach { device ->
                val platform = device.platform?.let { if (it == DevicePlatform.IOS) "iOS" else "Android" }
                val title = device.name
                    ?: platform?.let { stringResource(Res.string.device_push, it) }
                    ?: stringResource(Res.string.device_session)
                val caption = listOfNotNull(
                    device.appVersion?.let { stringResource(Res.string.device_app_version, it) },
                    stringResource(Res.string.device_seen, format.relative(device.lastSeenAt)),
                ).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Symbol(if (device.platform == null) Symbols.language else Symbols.notifications, size = 22.dp, tint = scheme.onSurfaceVariant)
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(caption, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditCard(entries: List<AdminAuditEntryDto>) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    AdminCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(Res.string.audit_title))
            if (entries.isEmpty()) {
                Text(stringResource(Res.string.audit_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = scheme.outlineVariant)
                Column {
                    Text(auditLabel(entry.action), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        entry.adminEmail + " · " + format.dateTime(entry.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun auditLabel(action: AdminAction): String = stringResource(
    when (action) {
        AdminAction.CREDITS_ADJUST -> Res.string.audit_credits_adjust
        AdminAction.USER_SIGN_OUT_ALL -> Res.string.audit_sign_out_all
        AdminAction.USER_DISABLE -> Res.string.audit_disable
        AdminAction.USER_ENABLE -> Res.string.audit_enable
        AdminAction.PASSWORD_RESET_SEND -> Res.string.audit_password_reset
        AdminAction.USERS_EXPORT -> Res.string.audit_users_export
        AdminAction.USER_CREATE -> Res.string.audit_user_create
        AdminAction.SETTINGS_UPDATE -> Res.string.audit_settings_update
    },
)

@Composable
private fun ConfirmActionDialog(detail: AdminUserDetailDto, confirm: ConfirmState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = detail.user.displayName
    val (title, body, ok) = when (confirm.action) {
        UserAction.SIGN_OUT_ALL -> Triple(Res.string.confirm_sign_out_all_title, Res.string.confirm_sign_out_all_body, Res.string.confirm_sign_out_all_ok)
        UserAction.DISABLE -> Triple(Res.string.confirm_disable_title, Res.string.confirm_disable_body, Res.string.confirm_disable_ok)
        UserAction.ENABLE -> Triple(Res.string.confirm_enable_title, Res.string.confirm_enable_body, Res.string.confirm_enable_ok)
        UserAction.PASSWORD_RESET -> Triple(Res.string.confirm_password_reset_title, Res.string.confirm_password_reset_body, Res.string.confirm_password_reset_ok)
    }
    ConfirmDialog(
        title = stringResource(title),
        body = stringResource(body, if (confirm.action == UserAction.PASSWORD_RESET) detail.user.email else name),
        confirmLabel = stringResource(ok),
        destructive = confirm.action == UserAction.DISABLE || confirm.action == UserAction.SIGN_OUT_ALL,
        busy = confirm.busy,
        error = confirm.error?.let { failureText(it) },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
