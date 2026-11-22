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
    /** Back on the first question: the interview closes (the session stays resumable for 24 h). */
    data object Leave : InspirationInterviewEvent
}

class InspirationInterviewViewModel(
    private val sessionId: String,
    private val repository: InspirationRepository,
    private val analytics: app.ransel.data.analytics.Analytics = app.ransel.data.analytics.NoAnalytics,
) : ViewModel() {
    private val mutable = MutableStateFlow(InspirationInterviewUiState())
    val state: StateFlow<InspirationInterviewUiState> = mutable.asStateFlow()
    private val events = Channel<InspirationInterviewEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init { load() }

    fun load() = runRequest { repository.get(sessionId) }
    fun setOtherText(value: String) { mutable.value = mutable.value.copy(otherText = value.take(80)) }
    fun openOther() { mutable.value = mutable.value.copy(otherOpen = true) }
    fun answer(optionId: String) {
        trackStep(other = false)
        runRequest { repository.answer(sessionId, InspirationAnswerRequest(optionId = optionId)) }
    }
    fun answerOther() {
        val text = mutable.value.otherText.trim()
        if (text.isEmpty()) return
        trackStep(other = true)
        runRequest { repository.answer(sessionId, InspirationAnswerRequest(otherText = text)) }
    }
    fun finish() = runRequest(repositoryCall = { repository.finish(sessionId) }, finishing = true)

    /** "Sudah cukup, lihat inspirasi". */
    fun enough() {
        analytics.event(app.ransel.data.analytics.AnalyticsEvent.InspirationEnough)
        finish()
    }

    private fun trackStep(other: Boolean) {
        val slot = mutable.value.question?.slot ?: return
        analytics.event(app.ransel.data.analytics.AnalyticsEvent.InspirationStep(slot, other))
    }
    fun retry() = load()

    /**
     * The back arrow and the system back: one question back (owner, 22 Nov 2026), the answers before it stay. On the first
     * question, or when the server has nothing earlier (409), the interview closes.
     */
    fun back() {
        val current = mutable.value
        if (current.loading) return
        if (current.otherOpen) {
            mutable.value = current.copy(otherOpen = false, otherText = "")
            return
        }
        val stepsDone = current.question?.stepsDone ?: 0
        if (current.lostSession || stepsDone == 0) {
            viewModelScope.launch { events.send(InspirationInterviewEvent.Leave) }
            return
        }
        mutable.value = current.copy(loading = true, inlineError = false)
        viewModelScope.launch {
            when (val result = repository.back(sessionId)) {
                is ApiResult.Success -> mutable.value = InspirationInterviewUiState(
                    session = result.value,
                    question = result.value.question,
                    loading = false,
                    hasAnswers = (result.value.question?.stepsDone ?: 0) > 0,
                )
                is ApiResult.Failure ->
                    if (result.error.code == ErrorCode.CONFLICT) {
                        mutable.value = current
                        events.send(InspirationInterviewEvent.Leave)
                    } else {
                        // the question stays on screen; a failed step back is like a failed step
                        mutable.value = current.copy(loading = false, inlineError = true)
                    }
            }
        }
    }

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
                        InspirationStatus.CHARGED -> {
                            val cards = session.result?.let { it.cards.size + (if (it.surprise != null) 1 else 0) } ?: 0
                            analytics.event(app.ransel.data.analytics.AnalyticsEvent.InspirationFinished(cards))
                            events.send(InspirationInterviewEvent.Results)
                        }
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
