package app.ransel.feature.inspiration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.InspirationAnswerRequest
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.InspirationEditChipRequest
import app.ransel.shared.dto.InspirationQuestionDto
import app.ransel.shared.dto.InspirationResultDto
import app.ransel.shared.dto.InspirationSessionDto
import app.ransel.shared.dto.SaveIdeaRequest
import app.ransel.shared.dto.BuddyInterestRequest
import app.ransel.shared.domain.inspirationInterestLabel
import app.ransel.shared.model.InspirationChipKey
import app.ransel.shared.model.InspirationStatus
import app.ransel.shared.model.Interest
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
    val chipEditsFreeLeft: Int = 0,
    val chipEditOpen: Boolean = false,
    val chipEditKey: InspirationChipKey? = null,
    val chipEditValue: String = "",
    val chipEditQuestion: InspirationQuestionDto? = null,
    val chipEditLoading: Boolean = false,
    val recalculating: Boolean = false,
) {
    val askAgainPaidCost: Int? get() = if (askAgainFreeLeft > 0) null else extraCost
    val chipEditPaidCost: Int? get() = if (chipEditsFreeLeft > 0) null else extraCost
}

sealed interface InspirationResultsEvent {
    data object OutOfCredits : InspirationResultsEvent
    data class Saved(val removedOldest: Boolean, val newIdeaId: String) : InspirationResultsEvent
    data object SaveFailed : InspirationResultsEvent
    /** "Kabari saya" was recorded (or failed: [recorded] false). */
    data class BuddyNoted(val recorded: Boolean) : InspirationResultsEvent
}

class InspirationResultsViewModel(
    private val sessionId: String,
    private val repository: InspirationRepository,
    private val analytics: app.ransel.data.analytics.Analytics = app.ransel.data.analytics.NoAnalytics,
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
        analytics.event(app.ransel.data.analytics.AnalyticsEvent.InspirationAskAgain(paid = old.askAgainFreeLeft <= 0))
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

    fun openChipEdit(chip: app.ransel.shared.dto.InspirationChipDto) {
        mutable.value = mutable.value.copy(
            chipEditOpen = true,
            chipEditKey = chip.key,
            chipEditValue = chip.value,
            chipEditQuestion = null,
            otherText = "",
            otherOpen = false,
        )
    }

    fun setChipEditValue(value: String) {
        mutable.value = mutable.value.copy(chipEditValue = value.take(80))
    }

    fun editChip() {
        val old = mutable.value
        val key = old.chipEditKey ?: return
        val value = old.chipEditValue.trim()
        if (value.isEmpty() || old.chipEditLoading) return
        analytics.event(app.ransel.data.analytics.AnalyticsEvent.InspirationChipEdit(key, paid = old.chipEditsFreeLeft <= 0))
        mutable.value = old.copy(chipEditLoading = true, recalculating = true, inlineError = false)
        viewModelScope.launch {
            when (val response = repository.editChip(sessionId, InspirationEditChipRequest(key, value))) {
                is ApiResult.Success -> {
                    val session = response.value
                    mutable.value = mutable.value.copy(
                        session = session,
                        result = session.result ?: mutable.value.result,
                        chipEditsFreeLeft = session.chipEditsFreeLeft,
                        extraCost = session.extraCost,
                        chipEditLoading = false,
                        recalculating = false,
                    )
                    if (session.status == InspirationStatus.ACTIVE && session.question != null) {
                        mutable.value = mutable.value.copy(chipEditQuestion = session.question, chipEditOpen = true)
                    } else if (session.result != null) {
                        acceptSession(session, previousCount = 0, replaceCards = true)
                    }
                }
                is ApiResult.Failure -> {
                    if (response.error.code == ErrorCode.INSUFFICIENT_CREDITS) {
                        mutable.value = old.copy(chipEditOpen = false, chipEditLoading = false, recalculating = false)
                        events.send(InspirationResultsEvent.OutOfCredits)
                    } else {
                        mutable.value = old.copy(chipEditLoading = false, recalculating = false)
                        handleFailure(response.error, old.copy(chipEditOpen = false), finishing = false)
                    }
                }
            }
        }
    }

    fun answerChipEdit(optionId: String) = answerChipEdit(InspirationAnswerRequest(optionId = optionId))

    fun answerChipEditOther() {
        val text = mutable.value.otherText.trim()
        if (text.isNotEmpty()) answerChipEdit(InspirationAnswerRequest(otherText = text))
    }

    private fun answerChipEdit(request: InspirationAnswerRequest) {
        val old = mutable.value
        mutable.value = old.copy(chipEditLoading = true, recalculating = true)
        viewModelScope.launch {
            when (val response = repository.answer(sessionId, request)) {
                is ApiResult.Success -> {
                    val session = response.value
                    if (session.status == InspirationStatus.CHARGED && session.result != null) {
                        acceptSession(session, previousCount = 0, replaceCards = true)
                    } else {
                        mutable.value = mutable.value.copy(
                            session = session,
                            chipEditQuestion = session.question,
                            chipEditLoading = false,
                            recalculating = false,
                            chipEditsFreeLeft = session.chipEditsFreeLeft,
                            extraCost = session.extraCost,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    if (response.error.code == ErrorCode.INSUFFICIENT_CREDITS) {
                        mutable.value = old.copy(chipEditOpen = false, chipEditLoading = false, recalculating = false)
                        events.send(InspirationResultsEvent.OutOfCredits)
                    } else handleFailure(response.error, old, finishing = false)
                }
            }
        }
    }

    fun closeChipEdit() {
        mutable.value = mutable.value.copy(chipEditOpen = false, chipEditQuestion = null, otherText = "", otherOpen = false)
    }

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
                is ApiResult.Success -> {
                    analytics.event(app.ransel.data.analytics.AnalyticsEvent.InspirationSaved)
                    events.send(InspirationResultsEvent.Saved(response.value.removedOldestId != null, response.value.idea.id))
                }
                is ApiResult.Failure -> events.send(InspirationResultsEvent.SaveFailed)
            }
        }
    }

    /** A card's detail sheet opened. */
    fun cardOpened(card: InspirationCardDto) = analytics.event(app.ransel.data.analytics.AnalyticsEvent.InspirationCardTapped(card.surprise))

    /** "Bikin trip dari sini" tapped. */
    fun makeTripTapped() = analytics.event(app.ransel.data.analytics.AnalyticsEvent.InspirationMakeTrip)

    /**
     * The interests of this session, read from the profile header's INTEREST chip (the session sends labels, not
     * constants): they preselect Seleramu in "Buat trip" so the user is not asked again (PRD "Handoff").
     */
    fun interestsForTrip(): Set<Interest> {
        val chip = mutableValueProfileInterest() ?: return emptySet()
        val text = chip.lowercase()
        return Interest.entries.filter { i -> listOf("id", "en").any { inspirationInterestLabel(i, it).lowercase() in text } }.toSet()
    }

    private fun mutableValueProfileInterest(): String? =
        mutable.value.result?.profile?.firstOrNull { it.key == InspirationChipKey.INTEREST }?.value

    /** "Kabari saya" on the buddy sheet: records the demand with its consent time (the server keeps it 90 days). */
    fun notifyBuddy() {
        analytics.event(app.ransel.data.analytics.AnalyticsEvent.BuddyInterest)
        viewModelScope.launch {
            val interest = interestsForTrip().firstOrNull()
            val result = repository.buddy(BuddyInterestRequest(sessionId = sessionId, interest = interest))
            events.send(InspirationResultsEvent.BuddyNoted(result is ApiResult.Success))
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

    private suspend fun acceptSession(session: InspirationSessionDto, previousCount: Int, replaceCards: Boolean = false) {
        val result = session.result ?: mutable.value.result
        val incoming = result?.let(::feedCards).orEmpty()
        val prior = mutable.value.cards
        val merged = if (replaceCards) incoming else if (prior.isNotEmpty() && incoming.isNotEmpty() && incoming.first().card.id == prior.first().card.id) {
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
            chipEditOpen = false,
            chipEditQuestion = null,
            chipEditLoading = false,
            recalculating = false,
            otherText = "",
            otherOpen = false,
            askAgainFreeLeft = session.askAgainFreeLeft,
            chipEditsFreeLeft = session.chipEditsFreeLeft,
            extraCost = session.extraCost,
            appendDividerAt = if (replaceCards) null else previousCount.takeIf { it > 0 && merged.size > it },
        )
    }

    private suspend fun handleFailure(error: ApiFailure, previous: InspirationResultsUiState, finishing: Boolean) {
        when {
            error.code == ErrorCode.INSUFFICIENT_CREDITS -> {
                // close the "Tanya lagi" sheet: it would otherwise keep spinning behind the credits screen
                mutable.value = mutable.value.copy(askAgainOpen = false, askAgainLoading = false, askAgainQuestion = null)
                events.send(InspirationResultsEvent.OutOfCredits)
            }
            error.code == ErrorCode.NOT_FOUND -> mutable.value = previous.copy(loading = false, lostSession = true)
            error.code == ErrorCode.AI_UNAVAILABLE -> mutable.value = previous.copy(loading = false, askAgainLoading = false, inlineError = true, askAgainOpen = true)
            error.isConnection -> mutable.value = previous.copy(loading = false, askAgainLoading = false, offline = true)
            else -> mutable.value = previous.copy(loading = false, askAgainLoading = false, inlineError = true)
        }
    }

    private fun feedCards(result: InspirationResultDto): List<InspirationFeedCard> =
        result.cards.map { InspirationFeedCard(it) } + listOfNotNull(result.surprise?.let { InspirationFeedCard(it, surprise = true) })
}
