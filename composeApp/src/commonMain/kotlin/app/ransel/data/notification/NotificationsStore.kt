package app.ransel.data.notification

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
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

    /** Marks everything read on the server, then the local copy. */
    suspend fun markAllRead(api: RanselApi) {
        if (api.markNotificationsRead(null) is ApiResult.Success) {
            _inbox.value = _inbox.value?.let { current ->
                current.copy(items = current.items.map { it.copy(readAt = it.readAt ?: kotlin.time.Clock.System.now()) }, unreadCount = 0)
            }
        }
    }
}

/** A trip the user tapped a notification for. Held until the navigation graph has opened it. */
class NotificationTarget {
    private val _tripId = MutableStateFlow<String?>(null)
    val tripId: StateFlow<String?> = _tripId.asStateFlow()

    fun offer(tripId: String) {
        _tripId.value = tripId.takeIf { it.isNotBlank() }
    }

    fun clear() {
        _tripId.value = null
    }
}
