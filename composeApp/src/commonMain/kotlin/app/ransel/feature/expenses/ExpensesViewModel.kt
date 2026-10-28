package app.ransel.feature.expenses

import app.ransel.shared.domain.memberIdFor
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ExpenseRepository
import app.ransel.shared.dto.BalancesDto
import app.ransel.shared.dto.ClaimSettlementRequest
import app.ransel.shared.dto.CreateSettlementRequest
import app.ransel.shared.dto.PayInfoDto
import app.ransel.shared.dto.PaymentMethodDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.model.SettlementStatus
import app.ransel.data.repository.PreferencesRepository
import app.ransel.shared.dto.TransferDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.util.newId
import app.ransel.feature.trip.zone
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import app.ransel.shared.domain.convertMinor
import app.ransel.shared.domain.summarizeExpenses
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ExpenseSummaryDto
import app.ransel.shared.dto.TripDto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Expenses of one calendar day (device time zone), newest first; `total` is in the trip base currency. */
data class ExpenseDayGroup(val date: LocalDate, val totalMinor: Long, val items: List<ExpenseDto>)

/** How many transactions the Expenses tab shows before "Lihat semua" (owner, 28 Oct 2026). */
const val RECENT_TRANSACTIONS = 5

/** The newest [limit] expenses, still grouped by day; a day keeps its full total even when some of its items are cut. */
fun List<ExpenseDayGroup>.latest(limit: Int): List<ExpenseDayGroup> {
    var left = limit
    return mapNotNull { group ->
        if (left <= 0) return@mapNotNull null
        val items = group.items.take(left)
        left -= items.size
        group.copy(items = items)
    }
}

data class ExpensesUiState(
    val loading: Boolean = true,
    val summary: ExpenseSummaryDto? = null,
    val groups: List<ExpenseDayGroup> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && groups.isEmpty()
}

/** Balances tab (server computed, base currency). */
data class BalancesUiState(
    val loading: Boolean = true,
    val balances: BalancesDto? = null,
    val failure: ApiFailure? = null,
    /** Transfers marked as paid in this visit; shown as "Lunas" until the screen is left. */
    val settledNow: List<TransferDto> = emptyList(),
    /** A "Tandai lunas" request is in flight. */
    val settling: Boolean = false,
)

/** Open claims and the "Bayar ke" sheet (server data, signed-in only). */
data class MoneyUiState(
    /** Settlements of the trip; the CLAIMED_PAID ones wait for their receiver. */
    val settlements: List<SettlementDto> = emptyList(),
    /** A claim or confirmation request is in flight. */
    val busy: Boolean = false,
    /** "Bayar ke": the receiver's info for the open transfer, while the sheet loads or shows it. */
    val payTo: PayToState? = null,
    /** The caller's own payment info, for "Ingatkan" messages; null until asked for. */
    val myMethods: List<PaymentMethodDto>? = null,
    /** "Sertakan di pengingat bayar" (per device). */
    val includePayInfo: Boolean = true,
) {
    /** Claims that wait for [memberId] to confirm. */
    fun claimsTo(memberId: String?) = settlements.filter { it.status == SettlementStatus.CLAIMED_PAID && it.toMemberId == memberId }

    /** Claims [memberId] made that are not confirmed yet. */
    fun claimsFrom(memberId: String?) = settlements.filter { it.status == SettlementStatus.CLAIMED_PAID && it.fromMemberId == memberId }
}

data class PayToState(
    val transfer: TransferDto,
    val loading: Boolean = true,
    val info: PayInfoDto? = null,
    val failure: ApiFailure? = null,
)

/** One-off results of money actions, shown as a snackbar. */
sealed interface MoneyEvent {
    data class Failed(val code: String) : MoneyEvent
    data object Claimed : MoneyEvent
    data object Confirmed : MoneyEvent
}

class ExpensesViewModel(
    private val trip: TripDto,
    private val repository: ExpenseRepository,
    private val api: RanselApi,
    private val session: SessionManager,
    private val preferences: PreferencesRepository? = null,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    /** Members of the trip; empty for guests, offline, or before the first answer (the tab then behaves as solo). */
    private val _members = MutableStateFlow<List<TripMemberDto>>(emptyList())
    val members: StateFlow<List<TripMemberDto>> = _members.asStateFlow()

    /** More than one member: the tab shows paid-by, split, the position card and the balances. */
    /** My member in this trip (the id the apps compute offline); null for guests. */
    val myMemberId: String? get() = session.user?.id?.let { memberIdFor(trip.id, it) }

    private val _balances = MutableStateFlow(BalancesUiState())
    val balances: StateFlow<BalancesUiState> = _balances.asStateFlow()

    private val _problems = Channel<String>(Channel.BUFFERED)
    /** `ErrorCode` of a failed "Tandai lunas". */
    val problems = _problems.receiveAsFlow()

    private val _money = MutableStateFlow(MoneyUiState())
    val money: StateFlow<MoneyUiState> = _money.asStateFlow()

    private val _events = Channel<MoneyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadMembers()
        viewModelScope.launch {
            val saved = preferences?.getString(PAY_INFO_IN_REMINDERS)
            _money.update { it.copy(includePayInfo = saved != "false") }
        }
    }

    fun loadMembers() {
        if (session.user == null) return
        viewModelScope.launch {
            val result = api.members(trip.id)
            if (result is ApiResult.Success) {
                _members.value = result.value
                if (result.value.size > 1) {
                    loadBalances()
                    loadMoney()
                }
            }
        }
    }

    /** Settlements, for the Saldo view. */
    fun loadMoney() {
        if (session.user == null) return
        viewModelScope.launch {
            (api.settlements(trip.id) as? ApiResult.Success)?.let { r -> _money.update { it.copy(settlements = r.value) } }
        }
    }

    private fun refreshAfterMoneyChange() {
        loadBalances()
        loadMoney()
    }

    private fun moneyAction(block: suspend () -> ApiResult<*>, done: MoneyEvent, after: () -> Unit = {}) {
        if (_money.value.busy) return
        _money.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Success -> {
                    _events.send(done)
                    after()
                }
                is ApiResult.Failure -> _events.send(MoneyEvent.Failed(result.error.code))
            }
            _money.update { it.copy(busy = false) }
            refreshAfterMoneyChange()
        }
    }

    /** "Saya sudah bayar" for my open transfer; it counts once the receiver confirms. */
    fun claim(transfer: TransferDto) = moneyAction(
        { api.claimSettlement(trip.id, ClaimSettlementRequest(newId(), transfer.toMemberId, transfer.amountMinor)) },
        MoneyEvent.Claimed,
    ) {
        analytics.event(AnalyticsEvent.SettlementClaimed)
        _money.update { it.copy(payTo = null) }
    }

    fun confirm(settlementId: String) = moneyAction(
        { api.confirmSettlement(trip.id, settlementId) },
        MoneyEvent.Confirmed,
    ) { analytics.event(AnalyticsEvent.SettlementConfirmed) }

    /** The receiver did not get the money: the claim goes away. */
    fun reject(settlementId: String) = moneyAction({ api.deleteSettlement(trip.id, settlementId) }, MoneyEvent.Failed(REJECTED))

    /** Opens "Bayar ke" for [transfer] (which must be mine) and loads the receiver's payment info. */
    fun openPayTo(transfer: TransferDto) {
        _money.update { it.copy(payTo = PayToState(transfer)) }
        viewModelScope.launch {
            val result = api.payInfo(trip.id, transfer.fromMemberId, transfer.toMemberId)
            _money.update { state ->
                if (state.payTo?.transfer != transfer) return@update state
                when (result) {
                    is ApiResult.Success -> state.copy(payTo = state.payTo.copy(loading = false, info = result.value))
                    is ApiResult.Failure -> state.copy(payTo = state.payTo.copy(loading = false, failure = result.error))
                }
            }
        }
    }

    fun closePayTo() = _money.update { it.copy(payTo = null) }

    /** My own payment info, for the reminder message. */
    fun loadMyMethods() {
        if (session.user == null || _money.value.myMethods != null) return
        viewModelScope.launch {
            (api.paymentMethods() as? ApiResult.Success)?.let { r -> _money.update { it.copy(myMethods = r.value) } }
        }
    }

    fun setIncludePayInfo(include: Boolean) {
        _money.update { it.copy(includePayInfo = include) }
        viewModelScope.launch { preferences?.putString(PAY_INFO_IN_REMINDERS, include.toString()) }
    }

    fun loadBalances() {
        if (session.user == null) return
        _balances.update { it.copy(loading = it.balances == null, failure = null) }
        viewModelScope.launch {
            when (val result = api.balances(trip.id)) {
                is ApiResult.Success -> _balances.update { it.copy(loading = false, balances = result.value) }
                is ApiResult.Failure -> _balances.update { it.copy(loading = false, failure = result.error) }
            }
        }
    }

    /** "Tandai lunas": records the payment for [transfer], then refreshes the balances. */
    fun settle(transfer: TransferDto) {
        if (_balances.value.settling) return
        _balances.update { it.copy(settling = true) }
        viewModelScope.launch {
            val request = CreateSettlementRequest(
                id = newId(),
                fromMemberId = transfer.fromMemberId,
                toMemberId = transfer.toMemberId,
                amountMinor = transfer.amountMinor,
            )
            when (val result = api.createSettlement(trip.id, request)) {
                is ApiResult.Success -> {
                    analytics.event(AnalyticsEvent.BalanceSettled)
                    _balances.update { it.copy(settledNow = it.settledNow + transfer) }
                }
                is ApiResult.Failure -> _problems.send(result.error.code)
            }
            _balances.update { it.copy(settling = false) }
            loadBalances()
        }
    }

    val state: StateFlow<ExpensesUiState> = repository.observe(trip.id)
        .map { list ->
            val tz = trip.zone()
            ExpensesUiState(
                loading = false,
                summary = summarizeExpenses(trip.baseCurrency, trip.budgetMinor, list, tz),
                groups = list.groupBy { it.spentAt.toLocalDateTime(tz).date }
                    .map { (date, items) ->
                        ExpenseDayGroup(
                            date = date,
                            totalMinor = items.sumOf {
                                convertMinor(it.amountMinor, it.currency, trip.baseCurrency, it.fxRateToBase)
                            },
                            items = items.sortedByDescending { it.spentAt },
                        )
                    }
                    .sortedByDescending { it.date },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    fun save(input: ExpenseInput) {
        val before = state.value.groups.flatMap { it.items }.firstOrNull { it.id == input.id }
        viewModelScope.launch {
            repository.save(trip.id, input)
            // the balances move when a paid estimate becomes a payment
            if (before?.isEstimate == true) refreshAfterMoneyChange()
        }
        if (before == null) analytics.event(AnalyticsEvent.ExpenseAdded(input.category, shared = input.splits.isNotEmpty()))
    }

    companion object {
        /** Per-device preference behind "Sertakan di pengingat bayar". */
        const val PAY_INFO_IN_REMINDERS = "pay_info_in_reminders"

        /** Not an error: the claim was turned down by its receiver. */
        const val REJECTED = "REJECTED"
    }

    fun delete(expenseId: String) {
        viewModelScope.launch { repository.delete(trip.id, expenseId) }
    }
}
