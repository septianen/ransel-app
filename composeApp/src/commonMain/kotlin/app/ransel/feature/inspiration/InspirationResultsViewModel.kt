package app.ransel.feature.inspiration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.InspirationAnswerRequest
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.InspirationQuestionDto
import app.ransel.shared.dto.InspirationResultDto
import app.ransel.shared.dto.InspirationSessionDto
import app.ransel.shared.dto.SaveIdeaRequest
import app.ransel.shared.model.InspirationStatus
import app.ransel.util.newId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

 data class InspirationFeedCard(val card: InspirationCardDto, val surprise: Boolean = false)

data class InspirationResultsUiState(
    val session: InspirationSessionDto? = null,
    val result: InspirationResultDto? = null,
    val cards: List<InspirationFeedCard> = emptyList(),
    val loading: Boolean = true,
    val inlineError: Boolean = false,
    val offline: Boolean = false,
    val lostSession: Boolean = false,
    val outOfCredits: Boolean = false,
    val askAgainOpen: Boolean = false,
    val askAgainLoading: Boolean = false,
    val askAgainQuestion: InspirationQuestionDto? = null,
    val otherText: String = "",
    val otherOpen: Boolean = false,
    val askAgainFreeLeft: Int = 0,
    val extraCost: Int = 0,
    val appendDividerAt: Int? = null,
) {
    val askAgainPaidCost: Int? get() = if (askAgainFreeLeft > 0) null else extraCost
}

sealed interface InspirationResultsEvent {
    data object OutOfCredits : InspirationResultsEvent
    data class Saved(val removedOldest: Boolean, val newIdeaId: String) : InspirationResultsEvent
}

class InspirationResultsViewModel(
    private val sessionId: String,
    private val repository: InspirationRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(InspirationResultsUiState())
    val state: StateFlow<InspirationResultsUiState> = mutable.asStateFlow()
    private val events = Channel<InspirationResultsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init { load() }

    fun load() {
        val previous = mutable.value
        mutable.value = previous.copy(loading = true, inlineError = false, offline = false)
        viewModelScope.launch { applySession(repository.get(sessionId), previous, finishing = false) }
    }

    fun askAgain() {
        if (mutable.value.askAgainLoading) return
        val old = mutable.value
        mutable.value = old.copy(
            askAgainOpen = true,
            askAgainLoading = true,
            inlineError = false,
            appendDividerAt = old.cards.size,
        )
        viewModelScope.launch {
            val result = repository.askAgain(sessionId)
            when (result) {
                is ApiResult.Success -> {
                    val session = result.value
                    mutable.value = mutable.value.copy(
                        session = session,
                        result = session.result ?: mutable.value.result,
                        askAgainQuestion = session.question,
                        askAgainLoading = false,
                        askAgainFreeLeft = session.askAgainFreeLeft,
                        extraCost = session.extraCost,
                    )
                    if (session.status == InspirationStatus.CHARGED) acceptSession(session, old.cards.size)
                }
                is ApiResult.Failure -> handleFailure(result.error, old, finishing = false)
            }
        }
    }

    fun answer(optionId: String) = answer(InspirationAnswerRequest(optionId = optionId))
    fun answerOther() {
        val text = mutable.value.otherText.trim()
        if (text.isNotEmpty()) answer(InspirationAnswerRequest(otherText = text))
    }
    fun setOtherText(value: String) { mutable.value = mutable.value.copy(otherText = value.take(80)) }
    fun openOther() { mutable.value = mutable.value.copy(otherOpen = true) }
    fun closeAskAgain() { mutable.value = mutable.value.copy(askAgainOpen = false, askAgainQuestion = null, otherText = "", otherOpen = false) }

    private fun answer(request: InspirationAnswerRequest) {
        val old = mutable.value
        mutable.value = old.copy(askAgainLoading = true, inlineError = false)
        viewModelScope.launch {
            when (val response = repository.answer(sessionId, request)) {
                is ApiResult.Success -> {
                    val session = response.value
                    mutable.value = mutable.value.copy(session = session, askAgainQuestion = session.question, askAgainLoading = false)
                    when (session.status) {
                        InspirationStatus.READY -> finish(old.cards.size)
                        InspirationStatus.CHARGED -> acceptSession(session, old.cards.size)
                        else -> {
                            val result = session.result
                            if (result != null && result.cards.size > old.cards.size) acceptSession(session, old.cards.size)
                        }
                    }
                }
                is ApiResult.Failure -> handleFailure(response.error, old, finishing = false)
            }
        }
    }

    private fun finish(previousCount: Int) {
        val old = mutable.value
        mutable.value = old.copy(askAgainLoading = true)
        viewModelScope.launch { applySession(repository.finish(sessionId), old, finishing = true, previousCount = previousCount) }
    }

    fun save(card: InspirationCardDto) {
        viewModelScope.launch {
            val request = SaveIdeaRequest(id = newId(), sessionId = sessionId, cardId = card.id)
            when (val response = repository.save(request)) {
                is ApiResult.Success -> events.send(InspirationResultsEvent.Saved(response.value.removedOldestId != null, response.value.idea.id))
                is ApiResult.Failure -> handleFailure(response.error, mutable.value, finishing = false)
            }
        }
    }

    fun undoSave(newIdeaId: String) {
        viewModelScope.launch { repository.undo(newIdeaId) }
    }

    private suspend fun applySession(
        response: ApiResult<InspirationSessionDto>,
        previous: InspirationResultsUiState,
        finishing: Boolean,
        previousCount: Int = previous.cards.size,
    ) {
        when (response) {
            is ApiResult.Success -> acceptSession(response.value, previousCount)
            is ApiResult.Failure -> handleFailure(response.error, previous, finishing)
        }
    }

    private suspend fun acceptSession(session: InspirationSessionDto, previousCount: Int) {
        val result = session.result ?: mutable.value.result
        val incoming = result?.let(::feedCards).orEmpty()
        val prior = mutable.value.cards
        val merged = if (prior.isNotEmpty() && incoming.isNotEmpty() && incoming.first().card.id == prior.first().card.id) {
            val ids = prior.mapTo(mutableSetOf()) { it.card.id }
            prior + incoming.filterNot { it.card.id in ids }
        } else if (prior.isNotEmpty()) {
            val ids = prior.mapTo(mutableSetOf()) { it.card.id }
            prior + incoming.filterNot { it.card.id in ids }
        } else incoming
        mutable.value = mutable.value.copy(
            session = session,
            result = result,
            cards = merged,
            loading = false,
            inlineError = false,
            offline = false,
            lostSession = false,
            askAgainOpen = false,
            askAgainLoading = false,
            askAgainQuestion = null,
            otherText = "",
            otherOpen = false,
            askAgainFreeLeft = session.askAgainFreeLeft,
            extraCost = session.extraCost,
            appendDividerAt = previousCount.takeIf { it > 0 && merged.size > it },
        )
    }

    private suspend fun handleFailure(error: ApiFailure, previous: InspirationResultsUiState, finishing: Boolean) {
        when {
            error.code == ErrorCode.INSUFFICIENT_CREDITS -> events.send(InspirationResultsEvent.OutOfCredits)
            error.code == ErrorCode.NOT_FOUND -> mutable.value = previous.copy(loading = false, lostSession = true)
            error.code == ErrorCode.AI_UNAVAILABLE -> mutable.value = previous.copy(loading = false, askAgainLoading = false, inlineError = true, askAgainOpen = true)
            error.isConnection -> mutable.value = previous.copy(loading = false, askAgainLoading = false, offline = true)
            else -> mutable.value = previous.copy(loading = false, askAgainLoading = false, inlineError = true)
        }
    }

    private fun feedCards(result: InspirationResultDto): List<InspirationFeedCard> =
        result.cards.map { InspirationFeedCard(it) } + listOfNotNull(result.surprise?.let { InspirationFeedCard(it, surprise = true) })
}
