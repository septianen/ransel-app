package app.ransel.data.notification

import app.ransel.ui.components.TripTab
import app.ransel.shared.model.NotificationType
import app.ransel.feature.notifications.notificationTab
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.domain.VoteStage
import app.ransel.shared.dto.NotificationsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * The last inbox page and the unread count of the signed-in user. Server data (online only); this just remembers
 * the last answer for the bell and the inbox. Refreshed on sign-in, when the app comes back and when a push arrives.
 */
class NotificationsStore {
    private val _inbox = MutableStateFlow<NotificationsDto?>(null)
    val inbox: StateFlow<NotificationsDto?> = _inbox.asStateFlow()

    fun clear() {
        _inbox.value = null
    }

    suspend fun refresh(api: RanselApi): ApiResult<NotificationsDto> =
        api.notifications().also { if (it is ApiResult.Success) _inbox.value = it.value }

    /** Marks one notification read on the server, then the local copy. */
    suspend fun markRead(api: RanselApi, id: String) {
        val before = _inbox.value?.items?.firstOrNull { it.id == id }
        if (before?.readAt != null) return
        if (api.markNotificationsRead(listOf(id)) is ApiResult.Success) {
            _inbox.value = _inbox.value?.let { current ->
                current.copy(
                    items = current.items.map { if (it.id == id) it.copy(readAt = kotlin.time.Clock.System.now()) else it },
                    unreadCount = (current.unreadCount - 1).coerceAtLeast(0),
                )
            }
        }
    }

    /** Marks everything read on the server, then the local copy. */
    suspend fun markAllRead(api: RanselApi) {
        if (api.markNotificationsRead(null) is ApiResult.Success) {
            _inbox.value = _inbox.value?.let { current ->
                current.copy(items = current.items.map { it.copy(readAt = it.readAt ?: kotlin.time.Clock.System.now()) }, unreadCount = 0)
            }
        }
    }
}

/**
 * A trip the user tapped a notification for, the tab its type belongs on (`notificationTab`), and the voting round
 * when it was about one. Held until the navigation graph has opened it.
 */
class NotificationTarget {
    private val _tripId = MutableStateFlow<String?>(null)
    val tripId: StateFlow<String?> = _tripId.asStateFlow()
    private val _roundId = MutableStateFlow<String?>(null)
    val roundId: StateFlow<String?> = _roundId.asStateFlow()
    private val _showResult = MutableStateFlow(false)
    /** The OWNER's "ready to decide" opens the result. */
    val showResult: StateFlow<Boolean> = _showResult.asStateFlow()
    private val _tab = MutableStateFlow<TripTab?>(null)
    val tab: StateFlow<TripTab?> = _tab.asStateFlow()

    /** [type] is the push's `type` (a `NotificationType` name); an unknown one opens the trip on its first tab. */
    fun offer(tripId: String, roundId: String? = null, stage: String? = null, type: String? = null) {
        _roundId.value = roundId?.takeIf { it.isNotBlank() }
        _tab.value = NotificationType.entries.firstOrNull { it.name == type }?.let(::notificationTab)
        _showResult.value = stage == VoteStage.READY
        _tripId.value = tripId.takeIf { it.isNotBlank() }
    }

    fun clear() {
        _tripId.value = null
        _roundId.value = null
        _showResult.value = false
        _tab.value = null
    }
}
