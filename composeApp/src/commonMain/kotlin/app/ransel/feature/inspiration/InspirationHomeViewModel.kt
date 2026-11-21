package app.ransel.feature.inspiration

import app.ransel.data.credit.CreditsStore
import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.inspiration.InspirationSessionStore
import app.ransel.data.remote.ApiResult
import app.ransel.shared.dto.StartInspirationRequest
import app.ransel.shared.model.Interest
import app.ransel.util.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface InspirationStartState {
    data object Idle : InspirationStartState
    data object Loading : InspirationStartState
    data class Started(val sessionId: String) : InspirationStartState
    data object OutOfCredits : InspirationStartState
    data class RateLimited(val minutes: Int?) : InspirationStartState
    data object NeedsVerification : InspirationStartState
    data object Offline : InspirationStartState
    data object Failed : InspirationStartState
}
data class InspirationHomeUiState(val text: String = "", val interests: List<Interest> = emptyList(), val limitReached: Boolean = false, val cost: Int = 0, val startState: InspirationStartState = InspirationStartState.Idle)

class InspirationHomeViewModel(private val repository: InspirationRepository, private val sessions: InspirationSessionStore, private val credits: CreditsStore) {
    private val mutable = MutableStateFlow(InspirationHomeUiState())
    val state: StateFlow<InspirationHomeUiState> = mutable.asStateFlow()
    init { credits.state.value?.let { mutable.value = mutable.value.copy(cost = it.costs.inspiration) } }
    fun setText(text: String) { mutable.value = mutable.value.copy(text = text.take(120)) }
    fun toggleInterest(interest: Interest) { val old=mutable.value; if (interest in old.interests) mutable.value=old.copy(interests=old.interests-interest,limitReached=false) else if(old.interests.size<5) mutable.value=old.copy(interests=old.interests+interest,limitReached=false) else mutable.value=old.copy(limitReached=true) }
    suspend fun start() {
        val old=mutable.value; mutable.value=old.copy(startState=InspirationStartState.Loading)
        when(val result=repository.start(StartInspirationRequest(newId(),old.interests,old.text.ifBlank { null }))) {
            is ApiResult.Success -> { sessions.set(result.value.id); mutable.value=mutable.value.copy(startState=InspirationStartState.Started(result.value.id)) }
            is ApiResult.Failure -> { val e=result.error; mutable.value=mutable.value.copy(startState=when { e.status==402 -> InspirationStartState.OutOfCredits; e.status==429 -> InspirationStartState.RateLimited(e.details?.get("retryAfterSeconds")?.toIntOrNull()?.let { (it+59)/60 }); e.code=="EMAIL_NOT_VERIFIED" -> InspirationStartState.NeedsVerification; e.isConnection -> InspirationStartState.Offline; else -> InspirationStartState.Failed }) }
        }
    }
}
