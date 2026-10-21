package app.ransel.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.NotificationDto
import app.ransel.shared.dto.NotificationsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val loading: Boolean = true,
    /** Load failure with nothing to show: the full-screen error state. */
    val failure: ApiFailure? = null,
    val inbox: NotificationsDto? = null,
) {
    val unread: List<NotificationDto> get() = inbox?.items.orEmpty().filter { it.readAt == null }
    val earlier: List<NotificationDto> get() = inbox?.items.orEmpty().filter { it.readAt != null }
}

/** The inbox behind the Home bell (designs `Notifikasi`, `KosongNotifikasi`). Server data, shared through [NotificationsStore]. */
class NotificationsViewModel(
    private val api: RanselApi,
    private val store: NotificationsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState(inbox = store.inbox.value, loading = store.inbox.value == null))
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { store.inbox.collect { inbox -> if (inbox != null) _state.update { it.copy(inbox = inbox) } } }
        load()
    }

    fun load() {
        _state.update { it.copy(failure = null, loading = it.inbox == null) }
        viewModelScope.launch {
            when (val result = store.refresh(api)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, inbox = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = if (it.inbox == null) result.error else null) }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch { store.markAllRead(api) }
    }

    /** Opening one marks only that one read. */
    fun markRead(notification: NotificationDto) {
        viewModelScope.launch { store.markRead(api, notification.id) }
    }
}
