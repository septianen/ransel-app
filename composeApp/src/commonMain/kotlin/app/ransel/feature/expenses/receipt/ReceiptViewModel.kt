package app.ransel.feature.expenses.receipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import app.ransel.data.credit.CreditsStore
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.dto.ReceiptDraftDto
import app.ransel.shared.dto.TripDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the receipt flow is (designs "FotoStruk" → "HasilStruk"). */
sealed interface ReceiptStep {
    /** Choose the camera or a photo. */
    data object Pick : ReceiptStep
    data object Reading : ReceiptStep
    /** What was read; the result form takes over from here. */
    data class Draft(val draft: ReceiptDraftDto) : ReceiptStep
    /** [code] is an `ErrorCode` (RECEIPT_UNREADABLE, INSUFFICIENT_CREDITS, FILE_TOO_LARGE, a network code, ...). */
    data class Failed(val code: String) : ReceiptStep
}

/** Sends a receipt photo to the server's reader. Online and signed in only; the server charges the credit. */
class ReceiptViewModel(
    private val trip: TripDto,
    private val api: RanselApi,
    private val credits: CreditsStore,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    private val _step = MutableStateFlow<ReceiptStep>(ReceiptStep.Pick)
    val step: StateFlow<ReceiptStep> = _step.asStateFlow()

    /** The wallet, for "costs n credits" before the photo is sent. */
    val wallet: StateFlow<CreditsDto?> = credits.state

    init {
        viewModelScope.launch { credits.refresh(api) }
    }

    fun read(image: ReceiptImage) {
        if (_step.value == ReceiptStep.Reading) return
        if (image.bytes.size > MAX_RECEIPT_BYTES) {
            _step.value = ReceiptStep.Failed(ErrorCode.FILE_TOO_LARGE)
            return
        }
        _step.value = ReceiptStep.Reading
        viewModelScope.launch {
            _step.value = when (val result = api.parseReceipt(trip.id, image.bytes, image.mimeType)) {
                is ApiResult.Success -> {
                    credits.update(result.value.credits)
                    ReceiptStep.Draft(result.value)
                }
                is ApiResult.Failure -> ReceiptStep.Failed(result.error.code)
            }
            val readable = _step.value is ReceiptStep.Draft
            if (readable || (_step.value as? ReceiptStep.Failed)?.code == ErrorCode.RECEIPT_UNREADABLE) {
                analytics.event(AnalyticsEvent.ReceiptScanned(readable))
            }
        }
    }

    /** Preparing the picked file failed (unreadable image). */
    fun failed(code: String) {
        _step.value = ReceiptStep.Failed(code)
    }

    fun retry() {
        _step.value = ReceiptStep.Pick
    }
}
