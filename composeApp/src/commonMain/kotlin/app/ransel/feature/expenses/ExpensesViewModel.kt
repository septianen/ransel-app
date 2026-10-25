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
import app.ransel.shared.dto.CreateSettlementRequest
import app.ransel.shared.dto.TransferDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.util.newId
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

class ExpensesViewModel(
    private val trip: TripDto,
    private val repository: ExpenseRepository,
    private val api: RanselApi,
    private val session: SessionManager,
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

    init {
        loadMembers()
    }

    fun loadMembers() {
        if (session.user == null) return
        viewModelScope.launch {
            val result = api.members(trip.id)
            if (result is ApiResult.Success) {
                _members.value = result.value
                if (result.value.size > 1) loadBalances()
            }
        }
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
            val tz = TimeZone.currentSystemDefault()
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
        val isNew = state.value.groups.none { group -> group.items.any { it.id == input.id } }
        viewModelScope.launch { repository.save(trip.id, input) }
        if (isNew) analytics.event(AnalyticsEvent.ExpenseAdded(input.category, shared = input.splits.isNotEmpty()))
    }

    fun delete(expenseId: String) {
        viewModelScope.launch { repository.delete(trip.id, expenseId) }
    }
}
