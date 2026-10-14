package app.ransel.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.ExpenseRepository
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

class ExpensesViewModel(
    private val trip: TripDto,
    private val repository: ExpenseRepository,
) : ViewModel() {
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
        viewModelScope.launch { repository.save(trip.id, input) }
    }

    fun delete(expenseId: String) {
        viewModelScope.launch { repository.delete(trip.id, expenseId) }
    }
}
