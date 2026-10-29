package app.ransel.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.trip.zone
import app.ransel.shared.domain.convertMinor
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripMemberDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The chips of "Semua transaksi". [PAYMENTS] and [MINE] only show on a shared trip. [MINE] is "Bagian saya": the
 * expenses I have a share of, counted at my share, and the payments I made or received (owner, 29 Oct 2026).
 */
enum class TransactionFilter { ALL, EXPENSES, PAYMENTS, MINE }

/** One row of "Semua transaksi": an expense (from the device or the web repository) or a settlement (server). */
sealed interface TransactionItem {
    val id: String
    val at: Instant

    data class Expense(val expense: ExpenseDto) : TransactionItem {
        override val id get() = expense.id
        override val at get() = expense.spentAt
    }

    data class Payment(val settlement: SettlementDto) : TransactionItem {
        override val id get() = settlement.id
        override val at get() = settlement.settledAt
    }
}

/**
 * The transactions of one calendar day (trip time zone), newest first. [spentMinor] = the day's expenses in the trip's
 * base currency, [paidMinor] = the day's payments between members (settlements are always in the base currency).
 */
data class TransactionDay(
    val date: LocalDate,
    val spentMinor: Long,
    val paidMinor: Long,
    val items: List<TransactionItem>,
)

data class AllTransactionsUiState(
    val loading: Boolean = true,
    val trip: TripDto? = null,
    val filter: TransactionFilter = TransactionFilter.ALL,
    /** Server members; empty for guests, offline or a solo trip (then the rows show no payer and no chips). */
    val members: List<TripMemberDto> = emptyList(),
    val myMemberId: String? = null,
    val days: List<TransactionDay> = emptyList(),
    /** A shared trip whose settlements could not be loaded (offline): only expenses are listed. */
    val paymentsUnavailable: Boolean = false,
) {
    val shared: Boolean get() = members.size > 1
    val count: Int get() = days.sumOf { it.items.size }
    val spentMinor: Long get() = days.sumOf { it.spentMinor }
    val paidMinor: Long get() = days.sumOf { it.paidMinor }
    val isEmpty: Boolean get() = !loading && days.isEmpty()
}

/** Server data for a shared trip; loaded once, again on [AllTransactionsViewModel.refresh]. */
private data class RemoteMoney(
    val members: List<TripMemberDto> = emptyList(),
    val settlements: List<SettlementDto> = emptyList(),
    val settlementsFailed: Boolean = false,
)

/**
 * "Semua transaksi" (not designed yet; built from the Pengeluaran list): every expense and every payment between
 * members of one trip, newest first, grouped by day, with filter chips. Expenses come from [ExpenseRepository] (Room on
 * the phone, so offline works; the API on the web); settlements and members only from the server when signed in.
 */
class AllTransactionsViewModel(
    private val tripId: String,
    trips: TripRepository,
    private val expenses: ExpenseRepository,
    private val api: RanselApi,
    private val session: SessionManager,
) : ViewModel() {
    private val filter = MutableStateFlow(TransactionFilter.ALL)
    private val remote = MutableStateFlow(RemoteMoney())

    val myMemberId: String? get() = session.user?.id?.let { memberIdFor(tripId, it) }

    val state: StateFlow<AllTransactionsUiState> =
        combine(trips.observeTrip(tripId), expenses.observe(tripId), remote, filter) { trip, list, money, filter ->
            val shared = money.members.size > 1
            // the chips that need members fall back to "Semua" when the trip turns out to be solo (or offline)
            val applied = if (!shared && filter != TransactionFilter.EXPENSES) TransactionFilter.ALL else filter
            AllTransactionsUiState(
                loading = trip == null,
                trip = trip,
                filter = applied,
                members = money.members,
                myMemberId = myMemberId,
                days = if (trip == null) emptyList() else transactionDays(
                    expenses = list,
                    settlements = if (shared) money.settlements else emptyList(),
                    filter = applied,
                    myMemberId = myMemberId,
                    baseCurrency = trip.baseCurrency,
                    timeZone = trip.zone(),
                ),
                paymentsUnavailable = shared && money.settlementsFailed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllTransactionsUiState())

    init {
        refresh()
    }

    fun setFilter(value: TransactionFilter) {
        filter.value = value
    }

    /** Members and settlements again (pull to refresh, back online). Guests have neither. */
    fun refresh() {
        if (session.user == null) return
        viewModelScope.launch {
            val members = (api.members(tripId) as? ApiResult.Success)?.value ?: return@launch
            remote.update { it.copy(members = members) }
            if (members.size < 2) return@launch
            when (val result = api.settlements(tripId)) {
                is ApiResult.Success -> remote.update { it.copy(settlements = result.value, settlementsFailed = false) }
                is ApiResult.Failure -> remote.update { it.copy(settlementsFailed = true) }
            }
        }
    }

    fun save(input: ExpenseInput) {
        viewModelScope.launch { expenses.save(tripId, input) }
    }

    fun delete(expenseId: String) {
        viewModelScope.launch { expenses.delete(tripId, expenseId) }
    }
}

/** [memberId]'s share of [this] expense in its own currency; an expense without splits is carried by its payer. */
internal fun ExpenseDto.shareOf(memberId: String?): Long =
    splits.firstOrNull { it.memberId == memberId }?.shareMinor
        ?: if (splits.isEmpty() && paidByMemberId == memberId) amountMinor else 0L

internal fun SettlementDto.involves(myMemberId: String): Boolean = fromMemberId == myMemberId || toMemberId == myMemberId

/**
 * Filters [expenses] and [settlements] by [filter] and groups them by day in [timeZone], newest day and newest row
 * first. Deleted settlements are left out. Under [TransactionFilter.MINE] an expense counts only [myMemberId]'s share
 * in [TransactionDay.spentMinor]; without a member id it keeps everything at full price.
 */
internal fun transactionDays(
    expenses: List<ExpenseDto>,
    settlements: List<SettlementDto>,
    filter: TransactionFilter,
    myMemberId: String?,
    baseCurrency: String,
    timeZone: TimeZone,
): List<TransactionDay> {
    val mine = filter == TransactionFilter.MINE && myMemberId != null
    val shownExpenses = when (filter) {
        TransactionFilter.PAYMENTS -> emptyList()
        else -> expenses.filter { it.deletedAt == null && (!mine || it.shareOf(myMemberId) > 0) }
    }
    val shownPayments = when (filter) {
        TransactionFilter.EXPENSES -> emptyList()
        else -> settlements.filter { it.deletedAt == null && (!mine || it.involves(myMemberId!!)) }
    }
    val items: List<TransactionItem> =
        shownExpenses.map { TransactionItem.Expense(it) } + shownPayments.map { TransactionItem.Payment(it) }
    return items
        .groupBy { it.at.toLocalDateTime(timeZone).date }
        .map { (date, dayItems) ->
            TransactionDay(
                date = date,
                spentMinor = dayItems.filterIsInstance<TransactionItem.Expense>().sumOf {
                    val amount = if (mine) it.expense.shareOf(myMemberId) else it.expense.amountMinor
                    convertMinor(amount, it.expense.currency, baseCurrency, it.expense.fxRateToBase)
                },
                paidMinor = dayItems.filterIsInstance<TransactionItem.Payment>().sumOf { it.settlement.amountMinor },
                items = dayItems.sortedByDescending { it.at },
            )
        }
        .sortedByDescending { it.date }
}
