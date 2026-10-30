package app.ransel.feature.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.explore.IconBox
import app.ransel.feature.trip.zone
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.Role
import app.ransel.shared.model.SettlementStatus
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.MoneyText
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.formatTime
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.expenses_before_trip
import ransel.composeapp.generated.resources.expenses_empty_body
import ransel.composeapp.generated.resources.ic_expenses_empty
import ransel.composeapp.generated.resources.shared_settled
import ransel.composeapp.generated.resources.shared_you_lower
import ransel.composeapp.generated.resources.transactions_count
import ransel.composeapp.generated.resources.transactions_empty_filtered
import ransel.composeapp.generated.resources.transactions_empty_title
import ransel.composeapp.generated.resources.transactions_filter_all
import ransel.composeapp.generated.resources.transactions_filter_expenses
import ransel.composeapp.generated.resources.transactions_filter_mine
import ransel.composeapp.generated.resources.transactions_filter_payments
import ransel.composeapp.generated.resources.transactions_payment_label
import ransel.composeapp.generated.resources.transactions_payment_title
import ransel.composeapp.generated.resources.transactions_payments_offline
import ransel.composeapp.generated.resources.transactions_retry
import ransel.composeapp.generated.resources.transactions_title
import ransel.composeapp.generated.resources.transactions_total_mine
import ransel.composeapp.generated.resources.transactions_total_paid
import ransel.composeapp.generated.resources.transactions_total_spent
import ransel.composeapp.generated.resources.transactions_waiting
import ransel.composeapp.generated.resources.transactions_you

/**
 * "Semua transaksi" (not designed yet). Tapping an expense opens the same [ExpenseSheet] as the Biaya tab (EDITOR+);
 * payments are read-only here, confirming or rejecting a claim stays on the Saldo view.
 */
@Composable
fun AllTransactionsScreen(
    tripId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** A payment row: back to the trip's Saldo view (owner, 29 Oct 2026). */
    onOpenBalances: (() -> Unit)? = null,
    viewModel: AllTransactionsViewModel = koinViewModel(key = "all-transactions-$tripId") { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }
    val trip = state.trip
    val canEdit = trip?.myRole?.atLeast(Role.EDITOR) == true
    AllTransactionsContent(
        state = state,
        onBack = onBack,
        onFilter = viewModel::setFilter,
        onOpenExpense = if (canEdit) ({ editing = it.id }) else null,
        onRetry = viewModel::refresh,
        onOpenPayment = onOpenBalances,
        modifier = modifier,
    )
    val existing = editing?.let { id ->
        state.days.asSequence().flatMap { it.items }.filterIsInstance<TransactionItem.Expense>().firstOrNull { it.id == id }?.expense
    }
    if (trip != null && existing != null) {
        ExpenseSheet(
            trip = trip,
            existing = existing,
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = {
                viewModel.delete(existing.id)
                editing = null
            },
            onDismiss = { editing = null },
            members = state.members,
            myMemberId = state.myMemberId,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTransactionsContent(
    state: AllTransactionsUiState,
    onBack: () -> Unit,
    onFilter: (TransactionFilter) -> Unit,
    /** Null = read-only (VIEWER). */
    onOpenExpense: ((ExpenseDto) -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null = payment rows are read-only. */
    onOpenPayment: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val trip = state.trip
    Column(modifier.fillMaxSize().background(scheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(Symbols.arrowBack, stringResource(Res.string.action_back), onBack)
            Text(stringResource(Res.string.transactions_title), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
        }
        if (state.loading || trip == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        // a solo trip has only expenses: no chips
        if (state.shared) FilterChips(state.filter, onFilter)
        val tz = remember(trip.timeZone) { trip.zone() }
        when {
            state.isEmpty && state.filter == TransactionFilter.ALL -> FullScreenState(
                title = stringResource(Res.string.transactions_empty_title),
                body = stringResource(Res.string.expenses_empty_body, trip.title),
                illustration = painterResource(Res.drawable.ic_expenses_empty),
                modifier = Modifier.weight(1f),
            )
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.paymentsUnavailable && state.filter != TransactionFilter.EXPENSES) {
                    OfflineNote(onRetry)
                }
                if (state.isEmpty) {
                    Text(
                        stringResource(Res.string.transactions_empty_filtered),
                        Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Totals(state, trip.baseCurrency)
                    state.days.forEach { day -> DaySection(day, state, trip, tz, onOpenExpense, onOpenPayment) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChips(selected: TransactionFilter, onFilter: (TransactionFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransactionFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilter(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                TransactionFilter.ALL -> Res.string.transactions_filter_all
                                TransactionFilter.EXPENSES -> Res.string.transactions_filter_expenses
                                TransactionFilter.PAYMENTS -> Res.string.transactions_filter_payments
                                TransactionFilter.MINE -> Res.string.transactions_filter_mine
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun OfflineNote(onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceContainerLow).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(Symbols.cloudOff, size = 20.dp, tint = scheme.onSurfaceVariant)
        Text(
            stringResource(Res.string.transactions_payments_offline),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.transactions_retry)) }
    }
}

/** "12 transaksi" and the total in the trip's base currency (payments under the Pembayaran chip, else expenses). */
@Composable
private fun Totals(state: AllTransactionsUiState, currency: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceContainerLow).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(Res.plurals.transactions_count, state.count, state.count),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Text(
            when (state.filter) {
                TransactionFilter.PAYMENTS -> stringResource(Res.string.transactions_total_paid, formatMoney(state.paidMinor, currency))
                TransactionFilter.MINE -> stringResource(Res.string.transactions_total_mine, formatMoney(state.spentMinor, currency))
                else -> stringResource(Res.string.transactions_total_spent, formatMoney(state.spentMinor, currency))
            },
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun DaySection(
    day: TransactionDay,
    state: AllTransactionsUiState,
    trip: TripDto,
    tz: TimeZone,
    onOpenExpense: ((ExpenseDto) -> Unit)?,
    onOpenPayment: (() -> Unit)?,
) {
    val dates = rememberDateFormatter()
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (trip.startDate?.let { day.date < it } == true) stringResource(Res.string.expenses_before_trip)
            else dates.fullWeekdayDayMonth(day.date),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
        // the day's spending; under Pembayaran (or on a day with only payments) the payments
        val total = if (state.filter == TransactionFilter.PAYMENTS || day.spentMinor == 0L && day.paidMinor > 0) day.paidMinor else day.spentMinor
        MoneyText(total, trip.baseCurrency, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
    day.items.forEach { item ->
        when (item) {
            is TransactionItem.Expense -> ExpenseRow(
                item.expense, tz, state.members.takeIf { state.shared }, state.myMemberId,
                onClick = onOpenExpense?.let { open -> { open(item.expense) } },
            )
            is TransactionItem.Payment -> PaymentRow(item.settlement, state.members, state.myMemberId, trip.baseCurrency, tz, onOpenPayment)
        }
    }
}

/** A payment between members, laid out like an expense row. A tap opens Saldo, where claims are confirmed. */
@Composable
private fun PaymentRow(
    settlement: SettlementDto,
    members: List<TripMemberDto>,
    myMemberId: String?,
    currency: String,
    tz: TimeZone,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val you = stringResource(Res.string.transactions_you)
    val youLower = stringResource(Res.string.shared_you_lower)
    fun nameOf(memberId: String, start: Boolean) =
        if (memberId == myMemberId) (if (start) you else youLower) else members.firstOrNull { it.memberId == memberId }?.displayName ?: "?"
    val waiting = settlement.status == SettlementStatus.CLAIMED_PAID
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(IrisShape.md).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.payments, size = 22.dp, filled = true, tint = scheme.onPrimaryContainer) }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    Res.string.transactions_payment_title,
                    nameOf(settlement.fromMemberId, start = true),
                    nameOf(settlement.toMemberId, start = false),
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatTime(settlement.settledAt.toLocalDateTime(tz).time) + " · " + stringResource(Res.string.transactions_payment_label),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(settlement.amountMinor, currency), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (waiting) Res.string.transactions_waiting else Res.string.shared_settled),
                style = MaterialTheme.typography.bodySmall,
                color = if (waiting) scheme.onSurfaceVariant else scheme.primary,
            )
        }
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

private val previewStamp = Instant.parse("2026-11-20T00:00:00Z")
private val previewTrip = TripDto(
    id = "t", ownerId = "u1", title = "Bali 4 hari", destination = "Bali",
    startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
    createdAt = previewStamp, updatedAt = previewStamp,
)
private val previewMembers = listOf(
    TripMemberDto(tripId = "t", memberId = "m-ayu", userId = "u1", displayName = "Ayu", role = Role.OWNER, joinedAt = previewStamp),
    TripMemberDto(tripId = "t", memberId = "m-dinda", userId = "u2", displayName = "Dinda", role = Role.EDITOR, joinedAt = previewStamp),
)

private fun previewExpense(id: String, at: String, amount: Long, category: Category, note: String?, payer: String = "m-ayu") = ExpenseDto(
    id = id, tripId = "t", amountMinor = amount, currency = "IDR", category = category, note = note,
    spentAt = Instant.parse(at), paidByMemberId = payer,
    splits = listOf(ExpenseSplitDto("m-ayu", amount / 2), ExpenseSplitDto("m-dinda", amount - amount / 2)),
    createdAt = previewStamp, updatedAt = previewStamp,
)

private val previewExpenses = listOf(
    previewExpense("e1", "2026-11-20T05:30:00Z", 85_000, Category.FOOD, "Makan siang di warung"),
    previewExpense("e2", "2026-11-20T02:00:00Z", 450_000, Category.TRANSPORT, "Sewa mobil", payer = "m-dinda"),
    previewExpense("e3", "2026-11-21T12:00:00Z", 1_200_000, Category.LODGING, null),
)
private val previewSettlements = listOf(
    SettlementDto(
        id = "s1", tripId = "t", fromMemberId = "m-ayu", toMemberId = "m-dinda", amountMinor = 182_500,
        settledAt = Instant.parse("2026-11-21T14:00:00Z"), createdAt = previewStamp, updatedAt = previewStamp,
    ),
    SettlementDto(
        id = "s2", tripId = "t", fromMemberId = "m-dinda", toMemberId = "m-ayu", amountMinor = 50_000,
        settledAt = Instant.parse("2026-11-21T15:00:00Z"), status = SettlementStatus.CLAIMED_PAID,
        createdAt = previewStamp, updatedAt = previewStamp,
    ),
)

private fun previewState(filter: TransactionFilter) = AllTransactionsUiState(
    loading = false,
    trip = previewTrip,
    filter = filter,
    members = previewMembers,
    myMemberId = "m-ayu",
    days = transactionDays(previewExpenses, previewSettlements, filter, "m-ayu", "IDR", TimeZone.of("Asia/Makassar")),
)

@Preview
@Composable
private fun AllTransactionsPreview() = IrisPreview {
    AllTransactionsContent(previewState(TransactionFilter.ALL), {}, {}, {}, {})
}

@Preview
@Composable
private fun AllTransactionsPaymentsPreview() = IrisPreview {
    AllTransactionsContent(previewState(TransactionFilter.PAYMENTS).copy(paymentsUnavailable = false), {}, {}, {}, {})
}

@Preview
@Composable
private fun AllTransactionsOfflinePreview() = IrisPreview {
    AllTransactionsContent(
        previewState(TransactionFilter.ALL).copy(
            days = transactionDays(previewExpenses, emptyList(), TransactionFilter.ALL, "m-ayu", "IDR", TimeZone.of("Asia/Makassar")),
            paymentsUnavailable = true,
        ),
        {}, {}, {}, {},
    )
}

@Preview
@Composable
private fun AllTransactionsEmptyPreview() = IrisPreview {
    AllTransactionsContent(AllTransactionsUiState(loading = false, trip = previewTrip), {}, {}, {}, {})
}
