package app.ransel.admin.feature.users

import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.admin.data.Page
import app.ransel.admin.data.UserFilter
import app.ransel.shared.dto.AdminUserDto
import app.ransel.shared.model.UserStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** "Bergabung" filter: the last N days in WIB, or any time. */
enum class JoinedRange(val days: Int?) { ANY(null), DAYS_7(7), DAYS_30(30), DAYS_90(90) }

data class UsersUiState(
    /** What is typed in the search field; [filter] follows it after a short pause. */
    val queryText: String = "",
    val joined: JoinedRange = JoinedRange.ANY,
    val filter: UserFilter = UserFilter(),
    val page: Int = 1,
    val loading: Boolean = true,
    val result: Page<AdminUserDto>? = null,
    val error: ApiFailure? = null,
    val exporting: Boolean = false,
)

sealed interface UsersEvent {
    data object Exported : UsersEvent
    data class Failed(val error: ApiFailure) : UsersEvent
}

/** `UsersScreen`: search, filters, server paging (25 per page) and the audited CSV export. */
class UsersPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate,
    private val download: (fileName: String, csv: String) -> Unit,
    /** 401/403 on a list call: let the session decide (signed out, or no longer an admin). */
    private val onAccessLost: suspend (ApiFailure) -> Unit,
) {
    private val _state = MutableStateFlow(UsersUiState())
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    private val _events = Channel<UsersEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        load()
    }

    fun setQuery(text: String) {
        _state.update { it.copy(queryText = text) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            applyFilter { it.copy(query = text.trim()) }
        }
    }

    fun setStatus(status: UserStatus?) = applyFilter { it.copy(status = status) }

    fun setPlatform(platform: String?) = applyFilter { it.copy(platform = platform) }

    fun setJoined(range: JoinedRange) {
        _state.update { it.copy(joined = range) }
        applyFilter { it.copy(joinedFrom = range.days?.let { days -> today().minus(DatePeriod(days = days - 1)) }) }
    }

    fun toggleNoCredits() = applyFilter { it.copy(noCredits = !it.noCredits) }

    fun nextPage() {
        if (_state.value.result?.hasNext == true) load(_state.value.page + 1)
    }

    fun previousPage() {
        if (_state.value.page > 1) load(_state.value.page - 1)
    }

    fun retry() = load(_state.value.page)

    fun export() {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true) }
        scope.launch {
            when (val result = api.exportUsers(_state.value.filter)) {
                is ApiResult.Success -> {
                    download(result.value.second, result.value.first)
                    _events.send(UsersEvent.Exported)
                }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _events.send(UsersEvent.Failed(result.error))
                }
            }
            _state.update { it.copy(exporting = false) }
        }
    }

    private fun applyFilter(change: (UserFilter) -> UserFilter) {
        val next = change(_state.value.filter)
        if (next == _state.value.filter) return
        _state.update { it.copy(filter = next) }
        load(1)
    }

    private fun load(page: Int = 1) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null, page = page) }
        loadJob = scope.launch {
            when (val result = api.users(_state.value.filter, page)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, result = result.value) }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { it.copy(loading = false, error = result.error) }
                }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
