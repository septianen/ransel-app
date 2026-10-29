package app.ransel.admin.feature.monitoring

import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.admin.data.Page
import app.ransel.shared.dto.AdminSearchDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** One monitoring page: what it asks for ([query]: a range or a month), and the last answer. */
data class MonitorUiState<Q, T>(
    val query: Q,
    val loading: Boolean = true,
    val data: T? = null,
    val error: ApiFailure? = null,
)

/**
 * Loads a read-only monitoring page for a [MonitorUiState.query] and keeps the last good answer on screen while a
 * new one loads ("Muat ulang", another range). Shared by Ringkasan, Jelajah AI and Sistem.
 */
class MonitorPresenter<Q, T>(
    private val scope: CoroutineScope,
    initial: Q,
    private val onAccessLost: suspend (ApiFailure) -> Unit,
    private val load: suspend (Q) -> ApiResult<T>,
) {
    private val _state = MutableStateFlow(MonitorUiState<Q, T>(initial))
    val state: StateFlow<MonitorUiState<Q, T>> = _state.asStateFlow()

    private var job: Job? = null

    init {
        reload()
    }

    fun select(query: Q) {
        if (query == _state.value.query && _state.value.error == null) return
        _state.update { it.copy(query = query) }
        reload()
    }

    fun reload() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        val query = _state.value.query
        job = scope.launch {
            when (val result = load(query)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, data = result.value) }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { it.copy(loading = false, error = result.error) }
                }
            }
        }
    }
}

data class SearchesUiState(
    val page: Int = 1,
    val loading: Boolean = true,
    val result: Page<AdminSearchDto>? = null,
    val error: ApiFailure? = null,
)

/** "Pencarian terbaru": 25 per page, newest first; [userId] = one user's searches (the user page). */
class SearchesPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    private val onAccessLost: suspend (ApiFailure) -> Unit,
    private val userId: String? = null,
) {
    private val _state = MutableStateFlow(SearchesUiState())
    val state: StateFlow<SearchesUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load(1)
    }

    fun nextPage() {
        if (_state.value.result?.hasNext == true) load(_state.value.page + 1)
    }

    fun previousPage() {
        if (_state.value.page > 1) load(_state.value.page - 1)
    }

    fun reload() = load(_state.value.page)

    private fun load(page: Int) {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null, page = page) }
        job = scope.launch {
            when (val result = api.searches(page, userId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, result = result.value) }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { it.copy(loading = false, error = result.error) }
                }
            }
        }
    }
}

/** `YYYY-MM` [months] away from [month]. */
fun shiftMonth(month: String, months: Int): String {
    val (year, number) = month.split('-').map { it.toInt() }
    val index = year * 12 + (number - 1) + months
    return "${index / 12}-${(index % 12 + 1).toString().padStart(2, '0')}"
}

fun monthOf(date: LocalDate): String = "${date.year}-${date.month.ordinal.plus(1).toString().padStart(2, '0')}"

/**
 * The day the AI budget runs out at this month's pace ("anggaran habis sekitar 26 Okt"): null when there is no
 * budget, nothing spent yet, or the pace stays within it until the month ends.
 */
fun budgetRunsOutOn(spentMicroUsd: Long, budgetMicroUsd: Long?, today: LocalDate): LocalDate? {
    if (budgetMicroUsd == null || budgetMicroUsd <= 0 || spentMicroUsd <= 0) return null
    val perDay = spentMicroUsd.toDouble() / today.day
    val day = kotlin.math.ceil(budgetMicroUsd / perDay).toInt().coerceAtLeast(1)
    val first = LocalDate(today.year, today.month, 1)
    val length = (first.plus(DatePeriod(months = 1)).toEpochDays() - first.toEpochDays()).toInt()
    return if (day > length) null else first.plus(DatePeriod(days = day - 1))
}
