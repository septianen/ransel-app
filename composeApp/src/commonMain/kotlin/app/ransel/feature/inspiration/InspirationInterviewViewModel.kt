package app.ransel.feature.inspiration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.remote.ApiResult
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.InspirationAnswerRequest
import app.ransel.shared.dto.InspirationQuestionDto
import app.ransel.shared.dto.InspirationSessionDto
import app.ransel.shared.model.InspirationStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

 data class InspirationInterviewUiState(
    val session: InspirationSessionDto? = null,
    val question: InspirationQuestionDto? = null,
    val loading: Boolean = true,
    val inlineError: Boolean = false,
    val lostSession: Boolean = false,
    val otherText: String = "",
    val otherOpen: Boolean = false,
    val hasAnswers: Boolean = false,
)
sealed interface InspirationInterviewEvent {
    data object Results : InspirationInterviewEvent
    data object OutOfCredits : InspirationInterviewEvent
}

class InspirationInterviewViewModel(private val sessionId: String, private val repository: InspirationRepository) : ViewModel() {
    private val mutable = MutableStateFlow(InspirationInterviewUiState())
    val state: StateFlow<InspirationInterviewUiState> = mutable.asStateFlow()
    private val events = Channel<InspirationInterviewEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init { load() }

    fun load() = runRequest { repository.get(sessionId) }
    fun setOtherText(value: String) { mutable.value = mutable.value.copy(otherText = value.take(80)) }
    fun openOther() { mutable.value = mutable.value.copy(otherOpen = true) }
    fun answer(optionId: String) = runRequest { repository.answer(sessionId, InspirationAnswerRequest(optionId = optionId)) }
    fun answerOther() {
        val text = mutable.value.otherText.trim()
        if (text.isNotEmpty()) runRequest { repository.answer(sessionId, InspirationAnswerRequest(otherText = text)) }
    }
    fun finish() = runRequest(repositoryCall = { repository.finish(sessionId) }, finishing = true)
    fun retry() = load()

    private fun runRequest(finishing: Boolean = false, repositoryCall: suspend () -> ApiResult<InspirationSessionDto>) {
        val previous = mutable.value
        mutable.value = previous.copy(loading = true, inlineError = false)
        viewModelScope.launch {
            when (val result = repositoryCall()) {
                is ApiResult.Success -> {
                    val session = result.value
                    mutable.value = InspirationInterviewUiState(
                        session = session,
                        question = session.question,
                        loading = false,
                        hasAnswers = (session.question?.stepsDone ?: 0) > 0,
                    )
                    when (session.status) {
                        InspirationStatus.READY -> if (!finishing) finish()
                        InspirationStatus.CHARGED -> events.send(InspirationInterviewEvent.Results)
                        else -> Unit
                    }
                }
                is ApiResult.Failure -> {
                    if (result.error.code == ErrorCode.INSUFFICIENT_CREDITS && finishing) events.send(InspirationInterviewEvent.OutOfCredits)
                    mutable.value = previous.copy(loading = false, inlineError = result.error.code != ErrorCode.NOT_FOUND, lostSession = result.error.code == ErrorCode.NOT_FOUND)
                }
            }
        }
    }
}
