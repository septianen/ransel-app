package app.ransel.feature.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.feature.expenses.receipt.AddExpenseChooserSheet
import app.ransel.feature.expenses.receipt.ReceiptFlow
import app.ransel.feature.trip.zone
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.ui.category.expenseLabel
import app.ransel.ui.category.expenseSymbol
import app.ransel.ui.category.label
import app.ransel.ui.category.tileColor
import app.ransel.ui.clickableSemantics
import app.ransel.ui.components.BudgetProgress
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.IconFab
import app.ransel.ui.components.MoneyText
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.formatMoneyCompact
import app.ransel.ui.format.formatTime
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.expenses_estimated_line
import ransel.composeapp.generated.resources.expense_estimate_chip
import ransel.composeapp.generated.resources.money_event_claimed
import ransel.composeapp.generated.resources.money_event_confirmed
import ransel.composeapp.generated.resources.money_event_failed
import ransel.composeapp.generated.resources.money_event_rejected
import ransel.composeapp.generated.resources.ic_expenses_empty
import ransel.composeapp.generated.resources.expenses_by_category
import ransel.composeapp.generated.resources.expenses_donut_description
import ransel.composeapp.generated.resources.expenses_before_trip
import ransel.composeapp.generated.resources.expenses_empty_body
import ransel.composeapp.generated.resources.expenses_empty_title
import ransel.composeapp.generated.resources.expenses_record
import ransel.composeapp.generated.resources.expenses_transactions
import ransel.composeapp.generated.resources.money_unit_billion
import ransel.composeapp.generated.resources.money_unit_million
import ransel.composeapp.generated.resources.money_unit_thousand
import ransel.composeapp.generated.resources.shared_paid_by_line
import ransel.composeapp.generated.resources.shared_people
import ransel.composeapp.generated.resources.shared_personal
import ransel.composeapp.generated.resources.shared_you_lower
import ransel.composeapp.generated.resources.shared_your_share
import ransel.composeapp.generated.resources.trips_spent_label

@Composable
fun ExpensesTab(
    trip: TripDto,
    modifier: Modifier = Modifier,
    /** Bumped by the trip screen's pull to refresh: members and balances are asked for again. */
    refreshKey: Int = 0,
    /** Account → Info pembayaran, from the Saldo view. */
    onOpenPaymentMethods: () -> Unit = {},
    viewModel: ExpensesViewModel = koinViewModel(key = "expenses-${trip.id}") { parametersOf(trip) },
) {
    LaunchedEffect(refreshKey) { if (refreshKey > 0) viewModel.loadMembers() }
    val state by viewModel.state.collectAsState()
    val members by viewModel.members.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val money by viewModel.money.collectAsState()
    val shared = members.size > 1
    val canEdit = trip.myRole.atLeast(app.ransel.shared.model.Role.EDITOR)
    val isOwner = trip.myRole == app.ransel.shared.model.Role.OWNER
    val myMemberId = viewModel.myMemberId
    var sharedTab by rememberSaveable { mutableStateOf(SharedTab.SUMMARY) }
    var sheet by remember { mutableStateOf<ExpenseSheetTarget?>(null) }
    var remind by remember { mutableStateOf<app.ransel.shared.dto.TransferDto?>(null) }
    var chooser by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf(false) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val dates = rememberDateFormatter()
    val tz = remember(trip.timeZone) { trip.zone() }
    val eventTexts = MoneyEventTexts.load()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> snackbar.showSnackbar(eventTexts.of(event)) }
    }
    val myName = members.firstOrNull { it.memberId == myMemberId }?.displayName.orEmpty()

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> Unit
            state.isEmpty -> FullScreenState(
                title = stringResource(Res.string.expenses_empty_title),
                body = stringResource(Res.string.expenses_empty_body, trip.title),
                illustration = painterResource(Res.drawable.ic_expenses_empty)
            )
            else -> {
                val summary = state.summary!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (shared) SharedTabSwitch(sharedTab, onSelect = { sharedTab = it })
                    if (shared && sharedTab == SharedTab.BALANCES) {
                        LaunchedEffect(Unit) { viewModel.loadMyMethods() }
                        SaldoContent(
                            balances = balances,
                            money = money,
                            members = members,
                            myMemberId = myMemberId,
                            onRetry = viewModel::loadBalances,
                            onSettle = viewModel::settle,
                            onRemind = { t -> remind = t },
                            onPay = viewModel::openPayTo,
                            onConfirm = { viewModel.confirm(it.id) },
                            onReject = { viewModel.reject(it.id) },
                            onOpenPayInfo = onOpenPaymentMethods,
                        )
                        return@Column
                    }
                    BudgetProgress(
                        spentMinor = summary.spentMinor,
                        budgetMinor = trip.budgetMinor,
                        currency = trip.baseCurrency,
                    )
                    if (summary.estimatedMinor > 0) {
                        Text(
                            stringResource(Res.string.expenses_estimated_line, formatMoney(summary.estimatedMinor, trip.baseCurrency)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // no "Posisimu" card here: the Saldo tab says the same (owner, 27 Oct 2026)
                    CategoryBreakdown(summary, trip.baseCurrency)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.expenses_transactions), style = MaterialTheme.typography.titleLarge)
                        state.groups.forEach { group ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    if (group.date < trip.startDate) {
                                        stringResource(Res.string.expenses_before_trip)
                                    } else {
                                        dates.fullWeekdayDayMonth(group.date)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MoneyText(
                                    group.totalMinor, trip.baseCurrency,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            group.items.forEach { expense ->
                                ExpenseRow(
                                    expense, tz, members.takeIf { shared }, myMemberId,
                                    onClick = if (canEdit) ({ sheet = ExpenseSheetTarget.Edit(expense.id) }) else null,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (canEdit && !state.loading) {
            IconFab(
                Symbols.add,
                label = stringResource(Res.string.expenses_record),
                // signed in: "Foto struk" or "Catat manual"; a guest can only type it
                onClick = { if (myMemberId != null) chooser = true else sheet = ExpenseSheetTarget.New },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        androidx.compose.material3.SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }

    if (chooser) {
        AddExpenseChooserSheet(
            tripTitle = trip.title,
            onReceipt = {
                chooser = false
                receipt = true
            },
            onManual = {
                chooser = false
                sheet = ExpenseSheetTarget.New
            },
            onDismiss = { chooser = false },
        )
    }
    if (receipt) {
        ReceiptFlow(
            trip = trip,
            members = members,
            myMemberId = myMemberId,
            onSave = {
                viewModel.save(it)
                receipt = false
            },
            onDismiss = { receipt = false },
            onManual = {
                receipt = false
                sheet = ExpenseSheetTarget.New
            },
        )
    }
    remind?.let { transfer ->
        val member = members.firstOrNull { it.memberId == transfer.fromMemberId }
        RemindSheet(
            transfer = transfer,
            member = member,
            myName = myName,
            tripTitle = trip.title,
            currency = trip.baseCurrency,
            myMethods = money.myMethods,
            includePayInfo = money.includePayInfo,
            onIncludePayInfo = viewModel::setIncludePayInfo,
            onDismiss = { remind = null },
        )
    }
    money.payTo?.let { payTo ->
        PayToSheet(
            state = payTo,
            members = members,
            currency = trip.baseCurrency,
            busy = money.busy,
            onClaim = { viewModel.claim(payTo.transfer) },
            onDismiss = viewModel::closePayTo,
        )
    }
    sheet?.let { target ->
        val existing = (target as? ExpenseSheetTarget.Edit)?.let { edit ->
            state.groups.flatMap { it.items }.firstOrNull { it.id == edit.id }
        }
        if (target is ExpenseSheetTarget.New || existing != null) {
            ExpenseSheet(
                trip = trip,
                existing = existing,
                onSave = {
                    viewModel.save(it)
                    sheet = null
                },
                onDelete = existing?.let {
                    {
                        viewModel.delete(it.id)
                        sheet = null
                    }
                },
                onDismiss = { sheet = null },
                members = members,
                myMemberId = myMemberId,
            )
        }
    }
}

sealed interface ExpenseSheetTarget {
    data object New : ExpenseSheetTarget
    data class Edit(val id: String) : ExpenseSheetTarget
}


/** Snackbar texts for [MoneyEvent], read once per composition. */
class MoneyEventTexts(
    private val claimed: String,
    private val confirmed: String,
    private val rejected: String,
    private val failed: String,
) {
    fun of(event: MoneyEvent): String = when (event) {
        MoneyEvent.Claimed -> claimed
        MoneyEvent.Confirmed -> confirmed
        is MoneyEvent.Failed -> if (event.code == ExpensesViewModel.REJECTED) rejected else failed.replace("%1\$s", event.code)
    }

    companion object {
        @Composable
        fun load() = MoneyEventTexts(
            stringResource(Res.string.money_event_claimed),
            stringResource(Res.string.money_event_confirmed),
            stringResource(Res.string.money_event_rejected),
            stringResource(Res.string.money_event_failed, "%1\$s"),
        )
    }
}

@Composable
private fun CategoryBreakdown(
    summary: app.ransel.shared.dto.ExpenseSummaryDto,
    currency: String,
) {
    val slices = summary.byCategory.filter { it.amountMinor > 0 }.sortedByDescending { it.amountMinor }
    if (slices.isEmpty()) return
    val colors = slices.map { it.category.tileColor() }
    val total = summary.spentMinor.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.expenses_by_category), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            ExpenseDonut(
                slices = slices.mapIndexed { i, s -> DonutSlice(s.amountMinor, colors[i]) },
                description = stringResource(Res.string.expenses_donut_description),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatMoneyCompact(
                            summary.spentMinor, currency,
                            thousand = stringResource(Res.string.money_unit_thousand),
                            million = stringResource(Res.string.money_unit_million),
                            billion = stringResource(Res.string.money_unit_billion),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(Res.string.trips_spent_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slices.forEachIndexed { i, slice ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                        Text(
                            stringResource(slice.category.expenseLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(slice.amountMinor * 100 / total)}%",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: ExpenseDto,
    tz: TimeZone,
    members: List<TripMemberDto>?,
    myMemberId: String?,
    onClick: (() -> Unit)?,
) {
    val time = expense.spentAt.toLocalDateTime(tz).time
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(IrisShape.md).background(expense.category.tileColor()),
            contentAlignment = Alignment.Center,
        ) { Symbol(expense.category.expenseSymbol, size = 22.dp, filled = true, tint = IrisTheme.accents.onAccent) }
        Column(Modifier.weight(1f)) {
            Text(
                expense.note?.takeIf { it.isNotBlank() } ?: stringResource(expense.category.expenseLabel),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = if (members == null) {
                formatTime(time) + " · " + stringResource(expense.category.expenseLabel)
            } else {
                // Shared trip: who paid and how many people share it ("Dibayar kamu · 3 orang").
                val you = stringResource(Res.string.shared_you_lower)
                fun nameOf(memberId: String) = if (memberId == myMemberId) you else members.firstOrNull { it.memberId == memberId }?.displayName ?: "?"
                // several payers: "Rina, kamu"; one payer: just that name
                val payer = if (expense.payers.size > 1) expense.payers.joinToString(", ") { nameOf(it.memberId) } else nameOf(expense.paidByMemberId)
                val people = if (expense.splits.isEmpty()) stringResource(Res.string.shared_personal)
                else pluralStringResource(Res.plurals.shared_people, expense.splits.size, expense.splits.size)
                stringResource(Res.string.shared_paid_by_line, payer) + " · " + people
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expense.isEstimate) {
            Text(
                stringResource(Res.string.expense_estimate_chip),
                Modifier
                    .clip(IrisShape.sm)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        app.ransel.ui.components.PendingMarker(expense.id)
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(expense.amountMinor, expense.currency), style = MaterialTheme.typography.titleSmall)
            val myShare = if (members == null) null else
                (expense.splits.firstOrNull { it.memberId == myMemberId }?.shareMinor
                    ?: if (expense.splits.isEmpty() && expense.paidByMemberId == myMemberId) expense.amountMinor else 0L)
            Text(
                if (myShare != null) stringResource(Res.string.shared_your_share, formatMoney(myShare, expense.currency))
                else stringResource(expense.paymentMethod.label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
