package app.ransel.feature.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import app.ransel.data.sync.NoSyncStatus
import app.ransel.data.sync.SyncStatus
import app.ransel.feature.destination.DestinationChoice
import app.ransel.feature.explore.monthKey
import app.ransel.shared.domain.InterviewAnswers
import app.ransel.shared.domain.InterviewCityFacts
import app.ransel.shared.domain.InterviewDestination
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.domain.InterviewStep
import app.ransel.shared.domain.canContinue
import app.ransel.shared.domain.canSkip
import app.ransel.shared.domain.currencyDecimals
import app.ransel.shared.domain.hasAnything
import app.ransel.shared.domain.interviewCityFacts
import app.ransel.shared.domain.interviewMainTravelMode
import app.ransel.shared.domain.localeForPlace
import app.ransel.shared.domain.nextInterviewStep
import app.ransel.shared.domain.previousInterviewStep
import app.ransel.shared.domain.skipInterviewTarget
import app.ransel.shared.domain.skipped
import app.ransel.shared.domain.steps
import app.ransel.shared.domain.toggleExtraInterest
import app.ransel.shared.domain.toggleInterest
import app.ransel.shared.domain.withDates
import app.ransel.shared.domain.withDatesUndecided
import app.ransel.shared.domain.withDeparture
import app.ransel.shared.domain.withDestination
import app.ransel.shared.domain.withLocalTransport
import app.ransel.shared.domain.withOrigin
import app.ransel.shared.domain.withStyle
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.Interest
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.TravelStyle
import app.ransel.util.newId
import app.ransel.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate

/** The "Khas <destination>" chips of W4: hidden (offline, a guest, a failure, none), loading (skeleton), or loaded. */
sealed interface KhasState {
    data object Hidden : KhasState
    data object Loading : KhasState
    data class Loaded(val interests: List<String>, val destination: String) : KhasState
}

/** What the interview shows: one of the questions, or the summary. */
sealed interface InterviewScreen {
    data class Question(val step: InterviewStep) : InterviewScreen
    data object Summary : InterviewScreen
}

data class InterviewUiState(
    val door: InterviewDoor,
    val answers: InterviewAnswers = InterviewAnswers(),
    /** The destination as the picker gave it: its place, country and zone become the trip's. */
    val destinationChoice: DestinationChoice? = null,
    val screen: InterviewScreen = InterviewScreen.Question(InterviewStep.DESTINATION),
    /** "Mode ubah": the question opened from a summary row (no indicator, no Lewati, "Simpan"); null = the normal flow. */
    val editing: InterviewStep? = null,
    val facts: InterviewCityFacts = InterviewCityFacts(),
    val signedIn: Boolean = false,
    val offline: Boolean = false,
    /** "Kota terakhir": the origin of the newest trip, null for a first trip. */
    val lastOrigin: TripOriginDto? = null,
    /** The "Khas <destination>" chips of W4. */
    val khas: KhasState = KhasState.Hidden,
    /** What the user typed as the trip's name; null = "Trip ke <destination>". */
    val customName: String? = null,
    /** The Indonesian or English "Trip ke %s", set by the screen (the view model holds no strings). */
    val nameTemplate: String = "%s",
    val budgetDigits: String = "",
    val currency: String = "IDR",
    val leaveDialog: Boolean = false,
    val creating: Boolean = false,
    val createFailed: Boolean = false,
) {
    val destinationName: String get() = answers.destination?.name.orEmpty()
    val tripName: String get() = customName ?: nameTemplate.replace("%s", destinationName)

    /** The step on screen, or null on the summary. */
    val step: InterviewStep? get() = (screen as? InterviewScreen.Question)?.step
}

sealed interface InterviewEvent {
    /** The trip is saved: Ajak teman (signed in) and then its Ringkasan, or Ringkasan at once (a guest). */
    data class Created(val tripId: String, val signedIn: Boolean) : InterviewEvent

    /** Leave the flow without creating anything. */
    data object Leave : InterviewEvent
}

/**
 * The state of "Buat trip" (spec "Wawancara Buat trip"). Every rule about the answers is in `shared/domain/InterviewFlow.kt`;
 * this class holds the answers while the screens are on, talks to the places and suggestions, and saves the trip.
 * The interview itself costs no credits and calls no AI; the only request is the "Khas" chips of W4.
 */
class InterviewViewModel(
    private val door: InterviewDoor,
    private val trips: TripRepository,
    private val preferences: PreferencesRepository,
    private val api: RanselApi?,
    private val session: SessionManager?,
    private val sync: SyncStatus = NoSyncStatus,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    private val _state = MutableStateFlow(InterviewUiState(door = door, signedIn = session?.user != null))
    val state: StateFlow<InterviewUiState> = _state.asStateFlow()

    private val events = Channel<InterviewEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var khasJob: Job? = null

    /** The answers before "Mode ubah" opened a row, so Back there puts them back. */
    private var beforeEdit: InterviewAnswers? = null

    init {
        viewModelScope.launch {
            val currency = preferences.observe().first().defaultCurrency
            val newest = trips.observeTrips().first().firstOrNull { it.origin != null }
            _state.update { it.copy(currency = currency, lastOrigin = newest?.origin) }
        }
        viewModelScope.launch { sync.offline.collect { off -> _state.update { it.copy(offline = off) } } }
    }

    fun setNameTemplate(template: String) = _state.update { if (it.nameTemplate == template) it else it.copy(nameTemplate = template) }

    // ── answers ─────────────────────────────────────────────────────────────

    /** Applies [change] to the answers and brings the city lists' facts up to date with the new origin and destination. */
    private fun change(change: (InterviewAnswers, InterviewCityFacts) -> InterviewAnswers) {
        _state.update { s ->
            val answers = change(s.answers, s.facts)
            s.copy(answers = answers, facts = interviewCityFacts(answers.origin, answers.destination?.name))
        }
    }

    /**
     * A new destination changes the facts first (the rental and station lists look at its name), then the answers that depended
     * on the old one are reconciled with them.
     */
    fun onDestinationPicked(choice: DestinationChoice) {
        val destination = InterviewDestination(choice.name, choice.place?.placeId, choice.place?.lat, choice.place?.lng, choice.countryCode)
        _state.update { s ->
            val facts = interviewCityFacts(s.answers.origin, destination.name)
            s.copy(
                destinationChoice = choice,
                answers = s.answers.withDestination(destination, facts),
                facts = facts,
                khas = KhasState.Hidden,
            )
        }
    }

    fun onDestinationCleared() = _state.update {
        it.copy(destinationChoice = null, answers = it.answers.withDestination(null, InterviewCityFacts()), facts = InterviewCityFacts(), khas = KhasState.Hidden)
    }

    fun onDates(start: LocalDate, end: LocalDate) = change { a, _ -> a.withDates(start, if (end < start) start else end) }
    fun onDatesUndecided(value: Boolean) = change { a, _ -> a.withDatesUndecided(value) }

    fun onOriginPicked(choice: DestinationChoice) = pickOrigin(
        TripOriginDto(choice.name, choice.place?.lat, choice.place?.lng, choice.place?.placeId, choice.countryCode),
    )

    fun pickOrigin(origin: TripOriginDto) {
        _state.update { s ->
            val facts = interviewCityFacts(origin, s.answers.destination?.name)
            s.copy(answers = s.answers.withOrigin(origin, facts), facts = facts)
        }
    }

    fun chooseDeparture(mode: DepartureMode) = change { a, f -> a.withDeparture(mode, f) }
    fun chooseLocalTransport(choice: LocalTransport) = change { a, f -> a.withLocalTransport(choice, f) }
    fun toggleInterest(interest: Interest) = change { a, _ -> a.toggleInterest(interest) }
    fun toggleExtraInterest(label: String) = change { a, _ -> a.toggleExtraInterest(label) }
    fun chooseStyle(style: TravelStyle) = change { a, _ -> a.withStyle(style) }

    fun onNameChange(value: String) = _state.update { it.copy(customName = value) }
    fun onBudgetChange(value: String) = _state.update { it.copy(budgetDigits = value.filter(Char::isDigit).take(12)) }

    // ── moving around ───────────────────────────────────────────────────────

    /** Lanjut: the next question, or the summary after the last one. In "Mode ubah" it saves and goes back to the summary. */
    fun next() {
        val s = _state.value
        val step = s.step ?: return
        if (!canContinue(door, step, s.answers)) return
        if (s.editing != null) return saveEdit()
        goTo(nextInterviewStep(s.answers.steps(), step))
    }

    /** Lewati: stores nothing, clears this screen, goes on. */
    fun skip() {
        val s = _state.value
        val step = s.step ?: return
        if (!canSkip(door, step) || s.editing != null) return
        change { a, f -> a.skipped(step, f) }
        goTo(nextInterviewStep(_state.value.answers.steps(), step))
    }

    /** "Lewati wawancara" on W1: the summary, or W4 from Jelajah. */
    fun skipInterview() {
        if (_state.value.step != InterviewStep.ORIGIN) return
        goTo(skipInterviewTarget(door))
    }

    private fun goTo(step: InterviewStep?) {
        _state.update { it.copy(screen = if (step == null) InterviewScreen.Summary else InterviewScreen.Question(step)) }
        if (step == InterviewStep.TASTE) loadKhas()
    }

    /** Back: the question before, the question the summary row opened it from, or the leave dialog on Layar 1. */
    fun back() {
        val s = _state.value
        when {
            s.leaveDialog -> dismissLeave()
            s.editing != null -> cancelEdit()
            s.screen is InterviewScreen.Summary -> goTo(s.answers.steps().last())
            else -> when (val previous = previousInterviewStep(s.answers.steps(), s.step ?: return)) {
                null -> requestLeave()
                else -> goTo(previous)
            }
        }
    }

    /** The X of Layar 1: the dialog only when something was filled in. */
    fun requestLeave() {
        if (_state.value.answers.hasAnything()) _state.update { it.copy(leaveDialog = true) } else leave()
    }

    fun dismissLeave() = _state.update { it.copy(leaveDialog = false) }

    fun leave() {
        _state.update { it.copy(leaveDialog = false) }
        viewModelScope.launch { events.send(InterviewEvent.Leave) }
    }

    // ── "Mode ubah" ─────────────────────────────────────────────────────────

    /** A summary row: its question as a screen of its own, without the indicator and Lewati, with "Simpan". */
    fun edit(step: InterviewStep) {
        beforeEdit = _state.value.answers
        _state.update { it.copy(editing = step, screen = InterviewScreen.Question(step)) }
        if (step == InterviewStep.TASTE) loadKhas()
    }

    fun saveEdit() {
        beforeEdit = null
        _state.update { it.copy(editing = null, screen = InterviewScreen.Summary) }
    }

    private fun cancelEdit() {
        val before = beforeEdit
        _state.update { s ->
            val answers = before ?: s.answers
            s.copy(editing = null, screen = InterviewScreen.Summary, answers = answers, facts = interviewCityFacts(answers.origin, answers.destination?.name))
        }
        beforeEdit = null
    }

    // ── the Khas chips ──────────────────────────────────────────────────────

    /** Loads the destination's own interests for W4: online and signed in only; any failure just hides the box (offline: core interests). */
    private fun loadKhas() {
        val s = _state.value
        val api = api ?: return
        val destination = s.destinationName.trim()
        if (!s.signedIn || s.offline || destination.length < 2) return
        if (s.khas is KhasState.Loaded && (s.khas as KhasState.Loaded).destination == destination) return
        khasJob?.cancel()
        _state.update { it.copy(khas = KhasState.Loading) }
        khasJob = viewModelScope.launch {
            val month = if (s.answers.datesUndecided) null else (s.answers.startDate ?: today()).monthKey()
            val language = preferences.observe().first().language.tag
            val result = withContext(Dispatchers.Default) {
                withTimeoutOrNull(KHAS_TIMEOUT_MILLIS) { api.exploreSuggestions(destination = destination, month = month, language = language) }
            }
            val interests = (result as? ApiResult.Success)?.value?.interests.orEmpty()
            _state.update { st ->
                if (interests.isEmpty()) {
                    st.copy(khas = KhasState.Hidden, answers = st.answers.copy(taste = st.answers.taste.copy(extraInterests = emptySet())))
                } else {
                    st.copy(
                        khas = KhasState.Loaded(interests, destination),
                        // ticked chips that still exist stay ticked
                        answers = st.answers.copy(taste = st.answers.taste.copy(extraInterests = st.answers.taste.extraInterests.filter { it in interests }.toSet())),
                    )
                }
            }
        }
    }

    // ── saving ──────────────────────────────────────────────────────────────

    /** "Buat trip": saved locally first, so it works offline; then the screen goes on to Ajak teman (signed in) and Ringkasan. */
    fun create() {
        val s = _state.value
        if (s.creating) return
        val destination = s.answers.destination ?: return
        val undecided = s.answers.datesUndecided
        if (!undecided && !s.answers.hasDates) return
        val locale = localeForPlace(s.destinationChoice?.countryCode, s.destinationChoice?.timeZone)
        val budgetMinor = s.budgetDigits.takeIf { it.isNotEmpty() }?.toLongOrNull()?.times(pow10(currencyDecimals(s.currency)))
        _state.update { it.copy(creating = true, createFailed = false) }
        viewModelScope.launch {
            runCatching {
                val id = newId()
                trips.create(
                    CreateTripRequest(
                        id = id,
                        title = s.tripName.trim().ifEmpty { destination.name },
                        destination = destination.name,
                        startDate = if (undecided) null else s.answers.startDate,
                        endDate = if (undecided) null else s.answers.endDate,
                        baseCurrency = s.currency,
                        budgetMinor = budgetMinor,
                        destinationPlace = s.destinationChoice?.place,
                        countryCode = locale.countryCode, localCurrency = locale.localCurrency, timeZone = locale.timeZone,
                        origin = s.answers.origin,
                        departureMode = s.answers.departure,
                        localTransport = s.answers.localTransport,
                        mainTravelMode = s.answers.interviewMainTravelMode(),
                    ),
                )
                analytics.event(AnalyticsEvent.TripCreated)
                events.send(InterviewEvent.Created(id, s.signedIn))
            }.onFailure {
                _state.update { st -> st.copy(creating = false, createFailed = true) }
            }
        }
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }

    private companion object {
        /** The spec's 8 s for the chips, wall-clock time. */
        const val KHAS_TIMEOUT_MILLIS = 8_000L
    }
}
