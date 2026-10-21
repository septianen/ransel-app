package app.ransel.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.notification.PushRegistrar
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.NotificationSettingsDto
import app.ransel.shared.dto.UpdateNotificationSettingsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotificationKind { TRIP_ACTIVITY, EXPENSES, REMINDERS }

data class NotificationSettingsUiState(
    val loading: Boolean = true,
    val failure: ApiFailure? = null,
    val settings: NotificationSettingsDto? = null,
    /** The phone's notification permission is off (design `PengaturanNotifikasiMati`). */
    val permissionOff: Boolean = false,
    /** The last change could not be saved and was put back. */
    val saveFailed: Boolean = false,
)

class NotificationSettingsViewModel(
    private val api: RanselApi,
    private val registrar: PushRegistrar,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationSettingsUiState(permissionOff = permissionOff()))
    val state: StateFlow<NotificationSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun permissionOff() = registrar.available && !registrar.hasPermission()

    /** Called when the screen comes back from the phone's settings. */
    fun refreshPermission() = _state.update { it.copy(permissionOff = permissionOff()) }

    fun load() {
        _state.update { it.copy(loading = it.settings == null, failure = null) }
        viewModelScope.launch {
            when (val result = api.notificationSettings()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, settings = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = if (it.settings == null) result.error else null) }
            }
        }
    }

    /** The switch moves at once; a failed save puts it back. */
    fun set(kind: NotificationKind, on: Boolean) {
        val before = _state.value.settings ?: return
        _state.update { it.copy(settings = before.with(kind, on), saveFailed = false) }
        viewModelScope.launch {
            val request = when (kind) {
                NotificationKind.TRIP_ACTIVITY -> UpdateNotificationSettingsRequest(tripActivity = on)
                NotificationKind.EXPENSES -> UpdateNotificationSettingsRequest(expenses = on)
                NotificationKind.REMINDERS -> UpdateNotificationSettingsRequest(reminders = on)
            }
            when (val result = api.updateNotificationSettings(request)) {
                is ApiResult.Success -> _state.update { it.copy(settings = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(settings = _state.value.settings?.with(kind, !on), saveFailed = true) }
            }
        }
    }

    fun openSystemSettings() = registrar.openSystemSettings()
}

private fun NotificationSettingsDto.with(kind: NotificationKind, on: Boolean) = when (kind) {
    NotificationKind.TRIP_ACTIVITY -> copy(tripActivity = on)
    NotificationKind.EXPENSES -> copy(expenses = on)
    NotificationKind.REMINDERS -> copy(reminders = on)
}
