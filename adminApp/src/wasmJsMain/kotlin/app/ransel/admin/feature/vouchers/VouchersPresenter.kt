package app.ransel.admin.feature.vouchers

import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.admin.data.Page
import app.ransel.shared.dto.AdminVoucherDto
import app.ransel.shared.dto.CreateVoucherRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The new-voucher form. Numbers stay text while typing; [request] is null until they are all in range. */
data class VoucherForm(
    val credits: String = "",
    val maxUses: String = "100",
    val validDays: String = "30",
    val note: String = "",
    val busy: Boolean = false,
    val showErrors: Boolean = false,
    val error: ApiFailure? = null,
) {
    val creditsValid get() = credits.toIntOrNull() in 1..10_000
    val maxUsesValid get() = maxUses.toIntOrNull() in 1..100_000
    val validDaysValid get() = validDays.toIntOrNull() in 1..3_650

    val request: CreateVoucherRequest?
        get() = if (creditsValid && maxUsesValid && validDaysValid) {
            CreateVoucherRequest(credits.toInt(), maxUses.toInt(), validDays.toInt(), note.trim().ifEmpty { null })
        } else null
}

data class VouchersUiState(
    val page: Int = 1,
    val loading: Boolean = true,
    val result: Page<AdminVoucherDto>? = null,
    val error: ApiFailure? = null,
    /** Non-null while the dialog is open. */
    val form: VoucherForm? = null,
    /** The voucher just made, shown once with its code. */
    val created: AdminVoucherDto? = null,
    val revoking: AdminVoucherDto? = null,
    val revokeBusy: Boolean = false,
    val revokeError: ApiFailure? = null,
    /** The code of the last revoke that worked, for the snackbar. */
    val revoked: String? = null,
)

/** `VouchersScreen`: paged list, create and revoke. */
class VouchersPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    /** 401/403: let the session decide (signed out, or no longer an admin). */
    private val onAccessLost: suspend (ApiFailure) -> Unit,
) {
    private val _state = MutableStateFlow(VouchersUiState())
    val state: StateFlow<VouchersUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(1)
    }

    fun retry() = load(_state.value.page)
    fun nextPage() { if (_state.value.result?.hasNext == true) load(_state.value.page + 1) }
    fun previousPage() { if (_state.value.page > 1) load(_state.value.page - 1) }

    private fun load(page: Int) {
        loadJob?.cancel()
        _state.update { it.copy(page = page, loading = true, error = null) }
        loadJob = scope.launch {
            when (val result = api.vouchers(page)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, result = result.value) }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { it.copy(loading = false, error = result.error) }
                }
            }
        }
    }

    fun openForm() = _state.update { it.copy(form = VoucherForm()) }
    fun closeForm() = _state.update { it.copy(form = null) }
    fun editForm(change: (VoucherForm) -> VoucherForm) = _state.update { s -> s.copy(form = s.form?.let(change)?.copy(error = null)) }
    fun closeCreated() = _state.update { it.copy(created = null) }

    fun create() {
        val form = _state.value.form ?: return
        if (form.busy) return
        val request = form.request
        if (request == null) {
            _state.update { it.copy(form = form.copy(showErrors = true)) }
            return
        }
        _state.update { it.copy(form = form.copy(busy = true, error = null)) }
        scope.launch {
            when (val result = api.createVoucher(request)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(form = null, created = result.value) }
                    load(1)
                }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { s -> s.copy(form = s.form?.copy(busy = false, error = result.error)) }
                }
            }
        }
    }

    fun askRevoke(voucher: AdminVoucherDto) = _state.update { it.copy(revoking = voucher, revokeError = null) }
    fun cancelRevoke() = _state.update { it.copy(revoking = null, revokeError = null) }
    fun revokedShown() = _state.update { it.copy(revoked = null) }

    fun revoke() {
        val voucher = _state.value.revoking ?: return
        if (_state.value.revokeBusy) return
        _state.update { it.copy(revokeBusy = true, revokeError = null) }
        scope.launch {
            when (val result = api.revokeVoucher(voucher.code)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(revokeBusy = false, revoking = null, revoked = voucher.code) }
                    load(_state.value.page)
                }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { it.copy(revokeBusy = false, revokeError = result.error) }
                }
            }
        }
    }
}
