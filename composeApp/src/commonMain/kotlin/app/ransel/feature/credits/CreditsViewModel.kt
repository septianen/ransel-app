package app.ransel.feature.credits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.credit.CreditsStore
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.CreditLedgerEntryDto
import app.ransel.shared.dto.CreditPackagesResponse
import app.ransel.shared.dto.CreditsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreditsUiState(
    val loading: Boolean = true,
    /** Load failure of the balance: shows the full-screen error state. */
    val failure: ApiFailure? = null,
    val credits: CreditsDto? = null,
    val packages: CreditPackagesResponse? = null,
    val ledger: List<CreditLedgerEntryDto> = emptyList(),
    val code: String = "",
    val redeeming: Boolean = false,
    /** An `ErrorCode` of the last failed redeem, shown under the field. */
    val redeemError: String? = null,
    /** Credits added by the last successful redeem, shown once. */
    val redeemed: Int? = null,
)

class CreditsViewModel(
    private val api: RanselApi,
    private val store: CreditsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(CreditsUiState(credits = store.state.value, loading = store.state.value == null))
    val state: StateFlow<CreditsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failure = null, loading = it.credits == null) }
        viewModelScope.launch {
            when (val result = store.refresh(api)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, credits = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = if (it.credits == null) result.error else null) }
            }
        }
        viewModelScope.launch {
            // The packs and the history are extras: a failure just leaves them empty.
            (api.creditPackages() as? ApiResult.Success)?.let { r -> _state.update { it.copy(packages = r.value) } }
        }
        loadLedger()
    }

    private fun loadLedger() {
        viewModelScope.launch {
            (api.creditLedger() as? ApiResult.Success)?.let { r -> _state.update { it.copy(ledger = r.value) } }
        }
    }

    fun onCodeChange(value: String) = _state.update { it.copy(code = value, redeemError = null, redeemed = null) }

    fun redeem() {
        val code = _state.value.code.trim()
        if (code.isEmpty() || _state.value.redeeming) return
        _state.update { it.copy(redeeming = true, redeemError = null, redeemed = null) }
        viewModelScope.launch {
            when (val result = api.redeemVoucher(code)) {
                is ApiResult.Success -> {
                    store.update(result.value.credits)
                    _state.update { it.copy(redeeming = false, code = "", credits = result.value.credits, redeemed = result.value.granted) }
                    loadLedger()
                }
                is ApiResult.Failure -> _state.update { it.copy(redeeming = false, redeemError = result.error.code) }
            }
        }
    }
}
