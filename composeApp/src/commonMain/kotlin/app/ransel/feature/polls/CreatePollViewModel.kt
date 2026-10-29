package app.ransel.feature.polls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.TripRepository
import app.ransel.shared.domain.PollAction
import app.ransel.shared.domain.actionFor
import app.ransel.shared.domain.defaultMultiChoice
import app.ransel.shared.domain.effectiveAnonymous
import app.ransel.shared.domain.isAnonymousForced
import app.ransel.shared.domain.maxPollOptions
import app.ransel.shared.domain.validatePollQuestion
import app.ransel.util.today
import app.ransel.shared.dto.CreatePollRequest
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.model.PollKind
import app.ransel.util.newId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** The choices for "Voting ditutup dalam". */
val CLOSING_CHOICES_DAYS = listOf(1, 2, 3)

data class CreatePollUiState(
    val kind: PollKind,
    val question: String = "",
    val questionWrong: Boolean = false,
    val closingDays: Int = 1,
    val multiChoice: Boolean = false,
    val anonymous: Boolean = false,
    val applyWinner: Boolean = true,
    /** FOOD: when the activity is added. */
    val foodDay: LocalDate? = null,
    val foodTime: LocalTime? = null,
    val options: List<PollOptionInput> = emptyList(),
    val currency: String = "IDR",
    val tripStart: LocalDate? = null,
    val tripEnd: LocalDate? = null,
    /** The option being added or edited (null = sheet closed); [editingId] set while editing one from the list. */
    val optionSheet: Boolean = false,
    val editingId: String? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
    /** DATE: the options are not 2–5 ranges from today on (shown after "Buat"). */
    val optionsWrong: Boolean = false,
) {
    val anonymousLocked: Boolean get() = isAnonymousForced(kind)
    val canAddOption: Boolean get() = options.size < maxPollOptions(kind)
    val hasAction: Boolean get() = actionFor(kind) != PollAction.NONE
}

sealed interface CreatePollEvent {
    data class Created(val pollId: String) : CreatePollEvent
}

/** The form of a new poll. Options can be added now or later from the poll itself. */
class CreatePollViewModel(
    private val tripId: String,
    kind: PollKind,
    private val api: RanselApi,
    trips: TripRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CreatePollUiState(
            kind = kind,
            multiChoice = defaultMultiChoice(kind),
            anonymous = effectiveAnonymous(kind, false),
        ),
    )
    val state: StateFlow<CreatePollUiState> = _state.asStateFlow()

    private val events = Channel<CreatePollEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            trips.observeTrip(tripId).collect { trip ->
                if (trip != null) _state.update { it.copy(currency = trip.baseCurrency, tripStart = trip.startDate, tripEnd = trip.endDate) }
            }
        }
    }

    /** The screen fills in the question of the template once; after that it belongs to the user. */
    fun prefillQuestion(text: String) = _state.update { if (it.question.isEmpty() && !it.questionWrong) it.copy(question = text) else it }

    fun onQuestion(value: String) = _state.update { it.copy(question = value, questionWrong = false) }
    fun onClosingDays(days: Int) = _state.update { it.copy(closingDays = days) }
    fun onMulti(value: Boolean) = _state.update { it.copy(multiChoice = value) }
    fun onAnonymous(value: Boolean) = _state.update { if (it.anonymousLocked) it else it.copy(anonymous = value) }
    fun onApplyWinner(value: Boolean) = _state.update { it.copy(applyWinner = value) }
    fun onFoodDay(day: LocalDate?) = _state.update { it.copy(foodDay = day, foodTime = if (day == null) null else it.foodTime ?: LocalTime(19, 0)) }
    fun onFoodTime(time: LocalTime) = _state.update { it.copy(foodTime = time) }

    /** The title and picture behind a pasted link, or null when it cannot be read. */
    suspend fun previewLink(url: String): app.ransel.shared.dto.LinkPreviewDto? = (api.linkPreview(url) as? ApiResult.Success)?.value

    fun openOptionSheet(editingId: String? = null) = _state.update { it.copy(optionSheet = true, editingId = editingId) }
    fun closeOptionSheet() = _state.update { it.copy(optionSheet = false, editingId = null) }

    fun saveOption(input: PollOptionInput) = _state.update { s ->
        val exists = s.options.any { it.id == input.id }
        if (!exists && !s.canAddOption) return@update s.copy(optionSheet = false, editingId = null)
        val options = if (exists) s.options.map { if (it.id == input.id) input else it } else s.options + input
        s.copy(
            options = options,
            optionSheet = false, editingId = null,
            optionsWrong = s.optionsWrong && !(s.kind == PollKind.DATE && datePollOptionsValid(options, today())),
        )
    }

    fun removeOption(id: String) = _state.update { it.copy(options = it.options.filterNot { o -> o.id == id }) }

    fun create() {
        val s = _state.value
        if (s.saving) return
        if (validatePollQuestion(s.question).isNotEmpty()) {
            _state.update { it.copy(questionWrong = true) }
            return
        }
        if (s.kind == PollKind.DATE && !datePollOptionsValid(s.options, today())) {
            _state.update { it.copy(optionsWrong = true) }
            return
        }
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            val request = CreatePollRequest(
                id = newId(),
                kind = s.kind,
                question = s.question.trim(),
                closesAt = Clock.System.now() + s.closingDays.days,
                multiChoice = s.multiChoice,
                anonymous = effectiveAnonymous(s.kind, s.anonymous),
                applyWinner = s.applyWinner,
                dayDate = s.foodDay.takeIf { s.kind == PollKind.FOOD },
                startTime = s.foodTime.takeIf { s.kind == PollKind.FOOD && s.foodDay != null },
                options = s.options,
            )
            when (val result = api.createPoll(tripId, request)) {
                is ApiResult.Success -> events.send(CreatePollEvent.Created(result.value.id))
                is ApiResult.Failure -> _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }
}
