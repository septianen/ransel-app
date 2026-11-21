package app.ransel.feature.inspiration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.NoAnalytics
import app.ransel.data.credit.CreditsStore
import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.inspiration.InspirationSessionStore
import app.ransel.data.remote.ApiResult
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.StartInspirationRequest
import app.ransel.shared.model.Interest
import app.ransel.util.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface InspirationStartState {
    data object Idle : InspirationStartState
    data object Loading : InspirationStartState
    data object OutOfCredits : InspirationStartState
    data class RateLimited(val minutes: Int?) : InspirationStartState
    data object NeedsVerification : InspirationStartState
    data object Offline : InspirationStartState
    data object Failed : InspirationStartState
}
data class InspirationHomeUiState(
    val text: String = "",
    val interests: List<Interest> = emptyList(),
    val limitReached: Boolean = false,
    val cost: Int = 0,
    val startState: InspirationStartState = InspirationStartState.Idle,
    val sessionId: String? = null,
    val rateLimitMinutes: Int? = null,
)

class InspirationHomeViewModel(
    private val repository: InspirationRepository,
    private val sessions: InspirationSessionStore,
    private val credits: CreditsStore,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    private val mutable = MutableStateFlow(InspirationHomeUiState())
    val state: StateFlow<InspirationHomeUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            credits.state.collect { value -> mutable.value = mutable.value.copy(cost = value?.costs?.inspiration ?: 0) }
        }
    }

    fun setText(text: String) {
        mutable.value = mutable.value.copy(text = text.take(120))
    }

    fun toggleInterest(interest: Interest) {
        val old = mutable.value
        mutable.value = when {
            interest in old.interests -> old.copy(interests = old.interests - interest, limitReached = false)
            old.interests.size < 5 -> old.copy(interests = old.interests + interest, limitReached = false)
            else -> old.copy(limitReached = true)
        }
    }

    fun start() {
        val old = mutable.value
        mutable.value = old.copy(startState = InspirationStartState.Loading, rateLimitMinutes = null)
        viewModelScope.launch {
            when (val result = repository.start(StartInspirationRequest(newId(), old.interests, old.text.ifBlank { null }))) {
                is ApiResult.Success -> {
                    sessions.set(result.value.id)
                    mutable.value = mutable.value.copy(startState = InspirationStartState.Idle, sessionId = result.value.id)
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    val rateMinutes = if (error.code == ErrorCode.RATE_LIMITED) {
                        error.details?.get("retryAfterSeconds")?.toIntOrNull()?.let { (it + 59) / 60 }
                    } else null
                    mutable.value = mutable.value.copy(
                    startState = when {
                            error.code == ErrorCode.INSUFFICIENT_CREDITS -> InspirationStartState.OutOfCredits
                            error.code == ErrorCode.RATE_LIMITED -> InspirationStartState.RateLimited(rateMinutes)
                            error.code == ErrorCode.EMAIL_NOT_VERIFIED -> InspirationStartState.NeedsVerification
                            error.isConnection -> InspirationStartState.Offline
                            else -> InspirationStartState.Failed
                        },
                        rateLimitMinutes = rateMinutes,
                    )
                }
            }
        }
    }
}
