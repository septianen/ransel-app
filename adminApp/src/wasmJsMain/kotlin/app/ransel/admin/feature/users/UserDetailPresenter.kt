package app.ransel.admin.feature.users

import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AdminAuditEntryDto
import app.ransel.shared.dto.AdminUserDetailDto
import app.ransel.shared.dto.CreditLedgerEntryDto
import app.ransel.shared.model.CreditDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot account actions; each asks for confirmation first. */
enum class UserAction { SIGN_OUT_ALL, DISABLE, ENABLE, PASSWORD_RESET }

data class ConfirmState(val action: UserAction, val busy: Boolean = false, val error: ApiFailure? = null)

data class AdjustState(
    val form: AdjustCreditsForm,
    val busy: Boolean = false,
    val error: ApiFailure? = null,
    /** The server answered EXCEEDS_BALANCE (the balance dropped since the page loaded). */
    val exceeded: Boolean = false,
) {
    val showsExceeds: Boolean get() = exceeded || form.exceedsBalance
}

data class UserDetailUiState(
    val loading: Boolean = true,
    val detail: AdminUserDetailDto? = null,
    val error: ApiFailure? = null,
    val ledger: List<CreditLedgerEntryDto> = emptyList(),
    val ledgerTotal: Int = 0,
    val ledgerPage: Int = 0,
    val ledgerLoading: Boolean = false,
    val audit: List<AdminAuditEntryDto> = emptyList(),
    val confirm: ConfirmState? = null,
    val adjust: AdjustState? = null,
) {
    val olderLedgerEntries: Int get() = (ledgerTotal - ledger.size).coerceAtLeast(0)
}

sealed interface UserDetailEvent {
    data class ActionDone(val action: UserAction, val revokedSessions: Int) : UserDetailEvent
    data class CreditsAdjusted(val direction: CreditDirection, val amount: Int, val balance: Int) : UserDetailEvent
}

/** `UserDetailScreen`: profile, trips metadata, credit history, devices, admin log and the account actions. */
class UserDetailPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    private val userId: String,
    /** Makes the `requestId` for each opening of the adjust dialog. */
    private val newRequestId: () -> String,
    private val onAccessLost: suspend (ApiFailure) -> Unit,
) {
    private val _state = MutableStateFlow(UserDetailUiState())
    val state: StateFlow<UserDetailUiState> = _state.asStateFlow()

    private val _events = Channel<UserDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        reload()
    }

    /** Profile, the first ledger page and the admin log, together. */
    fun reload() {
        _state.update { it.copy(loading = it.detail == null, error = null) }
        scope.launch {
            when (val result = api.user(userId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, detail = result.value) }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { it.copy(loading = false, error = result.error) }
                    return@launch
                }
            }
            loadLedger(1)
            loadAudit()
        }
    }

    fun loadMoreLedger() {
        if (_state.value.ledgerLoading || _state.value.olderLedgerEntries == 0) return
        scope.launch { loadLedger(_state.value.ledgerPage + 1) }
    }

    private suspend fun loadLedger(page: Int) {
        _state.update { it.copy(ledgerLoading = true) }
        when (val result = api.userLedger(userId, page)) {
            is ApiResult.Success -> _state.update {
                it.copy(
                    ledger = if (page == 1) result.value.items else it.ledger + result.value.items,
                    ledgerTotal = result.value.meta.total,
                    ledgerPage = page,
                    ledgerLoading = false,
                )
            }
            is ApiResult.Failure -> _state.update { it.copy(ledgerLoading = false) }
        }
    }

    private suspend fun loadAudit() {
        val result = api.audit(targetId = userId)
        if (result is ApiResult.Success) _state.update { it.copy(audit = result.value.items) }
    }

    // ── confirmations ───────────────────────────────────────────────────────
    fun ask(action: UserAction) = _state.update { it.copy(confirm = ConfirmState(action)) }

    fun dismissConfirm() = _state.update { if (it.confirm?.busy == true) it else it.copy(confirm = null) }

    fun confirm() {
        val current = _state.value.confirm ?: return
        if (current.busy) return
        _state.update { it.copy(confirm = current.copy(busy = true, error = null)) }
        scope.launch {
            val result: ApiResult<Int> = when (current.action) {
                UserAction.SIGN_OUT_ALL -> api.signOutAll(userId).revoked()
                UserAction.DISABLE -> api.disable(userId).revoked()
                UserAction.ENABLE -> api.enable(userId).revoked()
                UserAction.PASSWORD_RESET -> when (val r = api.sendPasswordReset(userId)) {
                    is ApiResult.Success -> ApiResult.Success(0)
                    is ApiResult.Failure -> r
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(confirm = null) }
                    _events.send(UserDetailEvent.ActionDone(current.action, result.value))
                    reload()
                }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { it.copy(confirm = current.copy(busy = false, error = result.error)) }
                }
            }
        }
    }

    private fun ApiResult<app.ransel.shared.dto.AdminActionResult>.revoked(): ApiResult<Int> = when (this) {
        is ApiResult.Success -> {
            val user = value.user
            _state.update { s -> s.copy(detail = s.detail?.copy(user = user)) }
            ApiResult.Success(value.revokedSessions)
        }
        is ApiResult.Failure -> this
    }

    // ── adjust credits ──────────────────────────────────────────────────────
    /** Opens the dialog with a fresh `requestId`; retries inside this opening reuse it. */
    fun openAdjust() {
        val balance = _state.value.detail?.credits?.balance ?: return
        _state.update { it.copy(adjust = AdjustState(AdjustCreditsForm(requestId = newRequestId(), balance = balance))) }
    }

    fun closeAdjust() = _state.update { if (it.adjust?.busy == true) it else it.copy(adjust = null) }

    fun editAdjust(change: (AdjustCreditsForm) -> AdjustCreditsForm) =
        _state.update { s -> s.adjust?.let { s.copy(adjust = it.copy(form = change(it.form), error = null, exceeded = false)) } ?: s }

    fun submitAdjust() {
        val current = _state.value.adjust ?: return
        if (current.busy || !current.form.canSave) return
        val request = current.form.request()
        _state.update { it.copy(adjust = current.copy(busy = true, error = null)) }
        scope.launch {
            when (val result = api.adjustCredits(userId, request)) {
                is ApiResult.Success -> {
                    _state.update { s ->
                        s.copy(adjust = null, detail = s.detail?.copy(credits = result.value, user = s.detail.user.copy(balance = result.value.balance)))
                    }
                    _events.send(UserDetailEvent.CreditsAdjusted(request.direction, request.amount, result.value.balance))
                    loadLedger(1)
                    loadAudit()
                }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    val exceeds = result.error.code == ErrorCode.VALIDATION_FAILED && result.error.details?.get("amount") == "EXCEEDS_BALANCE"
                    if (exceeds) {
                        // Someone spent credits since the page loaded: show the new ceiling, then refresh the numbers.
                        _state.update { it.copy(adjust = current.copy(busy = false, exceeded = true)) }
                        refreshBalance()
                    } else {
                        _state.update { it.copy(adjust = current.copy(busy = false, error = result.error)) }
                    }
                }
            }
        }
    }

    private suspend fun refreshBalance() {
        val result = api.user(userId)
        if (result is ApiResult.Success) {
            _state.update { s ->
                s.copy(
                    detail = result.value,
                    adjust = s.adjust?.let { it.copy(form = it.form.copy(balance = result.value.credits.balance)) },
                )
            }
        }
    }
}
