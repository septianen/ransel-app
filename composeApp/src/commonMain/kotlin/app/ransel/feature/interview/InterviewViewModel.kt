package app.ransel.feature.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import app.ransel.data.auth.SessionManager
import app.ransel.data.credit.CreditsStore
import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.shared.dto.CreditsDto
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
import app.ransel.shared.domain.InterviewTaste
import app.ransel.shared.domain.canSaveFromSheet
import app.ransel.shared.domain.canContinue
import app.ransel.shared.domain.canLoadDestinationInterests
import app.ransel.shared.domain.cityDisplayName
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
import app.ransel.platform.LocationFix
import kotlinx.datetime.daysUntil
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.TravelStyle
import app.ransel.util.newId
import app.ransel.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

/** W1's "Gunakan lokasi saat ini": nothing yet, looking for the city, refused by the user, or it did not work. */
sealed interface OriginLocation {
    data object Idle : OriginLocation
    data object Locating : OriginLocation
    data object Denied : OriginLocation
    data object Failed : OriginLocation
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
    val location: OriginLocation = OriginLocation.Idle,
    /** Jelajah summary: the wallet (null = not known yet, or offline). */
    val credits: CreditsDto? = null,
    /** "Ubah seleramu": the sheet is open and this is its draft (applied by Simpan, dropped by closing it). */
    val tasteDraft: InterviewTaste? = null,
    /** What the user typed as the trip's name; null = "Trip ke <destination>". */
    val customName: String? = null,
    /** The Indonesian or English "Trip ke %s", set by the screen (the view model holds no strings). */
    val nameTemplate: String = "%s",
    val budgetDigits: String = "",
    /** The price range the budget typed on the summary points to (the server's maths); null = no budget, offline or not known. */
    val budgetPriceLevel: PriceLevel? = null,
    val currency: String = "IDR",
    val leaveDialog: Boolean = false,
    val creating: Boolean = false,
    val createFailed: Boolean = false,
) {
    val destinationName: String get() = answers.destination?.name.orEmpty()

    /** What the search uses: the range the user picked, else the one of the budget, else Sedang. */
    val priceLevel: PriceLevel get() = answers.taste.priceLevel ?: budgetPriceLevel ?: PriceLevel.MEDIUM

    /** The range shown is the budget's ("Dari budget trip"), because the user did not pick one. */
    val priceFromBudget: Boolean get() = answers.taste.priceLevel == null && budgetPriceLevel != null
    val tripName: String get() = customName ?: nameTemplate.replace("%s", destinationName)

    /** The step on screen, or null on the summary. */
    val step: InterviewStep? get() = (screen as? InterviewScreen.Question)?.step
}

sealed interface InterviewEvent {
    /** The trip is saved: Ajak teman (signed in) and then its Ringkasan, or Ringkasan at once (a guest). */
    data class Created(val tripId: String, val signedIn: Boolean) : InterviewEvent

    /**
     * From Jelajah ("Buat trip dan cari"): the trip is saved and on the server, its Seleramu is the session of the search; the
     * swipe screen makes the request (and shows "Kreditmu tidak cukup" or the verify-email gate when it cannot).
     */
    data class CreatedAndSearch(val tripId: String) : InterviewEvent

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
    /** The trip's Seleramu goes here (both doors); from Jelajah it is also the session of the search. Null in tests that do not need it. */
    private val explore: ExploreSessionStore? = null,
    /** The balance shown on the Jelajah summary ("Saldo 27 kredit · sisa 24 setelah mencari"). */
    private val creditsStore: CreditsStore? = null,
    /** Where the idea's activity goes after "Buat trip" from an Explore Inspirasi card; null in tests that do not need it. */
    private val itinerary: app.ransel.data.repository.ItineraryRepository? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(InterviewUiState(door = door, signedIn = session?.user != null))
    val state: StateFlow<InterviewUiState> = _state.asStateFlow()

    private val events = Channel<InterviewEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var khasJob: Job? = null
    private var budgetJob: Job? = null

    /** The answers before "Mode ubah" opened a row, so Back there puts them back. */
    private var beforeEdit: InterviewAnswers? = null

    init {
        viewModelScope.launch {
            val currency = preferences.observe().first().defaultCurrency
            val newest = trips.observeTrips().first().firstOrNull { it.origin != null }
            _state.update { it.copy(currency = currency, lastOrigin = newest?.origin?.let { o -> o.copy(name = cityDisplayName(o.name)) }) }
        }
        viewModelScope.launch { sync.offline.collect { off -> _state.update { it.copy(offline = off) } } }
        if (door == InterviewDoor.EXPLORE && creditsStore != null) {
            viewModelScope.launch { creditsStore.state.collect { c -> _state.update { it.copy(credits = c) } } }
            // the balance for "sisa 24 setelah mencari": quietly, nothing is shown when it cannot be had
            if (api != null && session?.user != null) viewModelScope.launch { creditsStore.refresh(api) }
        }
    }

    private var prefilled = false
    /** The idea's activity and its region, added to the new trip as one unscheduled item. */
    private var ideaItem: Pair<String, String>? = null

    /**
     * "Bikin trip dari sini" (Explore Inspirasi): the card's region becomes the destination (as text: an activity + region
     * has no place id) and the interview's interests are preselected in Seleramu, so they are not asked again. Once per
     * ViewModel; the dates and the duration are left to the user (the card's duration is a suggestion only, PRD v0.8).
     */
    fun applyPrefill(prefill: InterviewPrefill) {
        if (prefilled) return
        prefilled = true
        val name = prefill.destination.trim().take(120)
        if (name.isNotEmpty()) onDestinationPicked(DestinationChoice(name, countryCode = prefill.countryCode?.uppercase()?.takeIf { it.length == 2 }))
        if (prefill.interests.isNotEmpty()) change { a, _ -> a.copy(taste = a.taste.copy(interests = prefill.interests)) }
        val activity = prefill.activity
        if (activity != null && name.isNotEmpty()) ideaItem = activity to name
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
        val destination = InterviewDestination(cityDisplayName(choice.name), choice.place?.placeId, choice.place?.lat, choice.place?.lng, choice.countryCode)
        _state.update { s ->
            val facts = interviewCityFacts(s.answers.origin, destination.name)
            s.copy(
                destinationChoice = choice,
                answers = s.answers.withDestination(destination, facts),
                facts = facts,
                khas = KhasState.Hidden,
            )
        }
        loadKhas()
    }

    fun onDestinationCleared() = _state.update {
        it.copy(destinationChoice = null, answers = it.answers.withDestination(null, InterviewCityFacts()), facts = InterviewCityFacts(), khas = KhasState.Hidden)
    }

    fun onDates(start: LocalDate, end: LocalDate) = change { a, _ -> a.withDates(start, if (end < start) start else end) }
    fun onDatesUndecided(value: Boolean) = change { a, _ -> a.withDatesUndecided(value) }

    fun onOriginPicked(choice: DestinationChoice) = pickOrigin(
        TripOriginDto(cityDisplayName(choice.name), choice.place?.lat, choice.place?.lng, choice.place?.placeId, choice.countryCode),
    )

    /**
     * W1's "Gunakan lokasi saat ini": one coarse fix, turned into a city by /places/reverse. Only the city's name and centre
     * are kept, never the fix itself (spec). Needs internet and an account, like the destination picker's button.
     */
    fun useCurrentLocation(request: suspend () -> LocationFix) {
        val api = api ?: return
        val s = _state.value
        if (!s.signedIn || s.offline || s.location == OriginLocation.Locating) return
        _state.update { it.copy(location = OriginLocation.Locating) }
        viewModelScope.launch {
            val next = when (val found = findOriginCity(api, request)) {
                is OriginFound.City -> {
                    pickOrigin(found.origin)
                    OriginLocation.Idle
                }
                OriginFound.Denied -> OriginLocation.Denied
                OriginFound.Failed -> OriginLocation.Failed
            }
            _state.update { it.copy(location = next) }
        }
    }

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

    // ── "Ubah seleramu" (Jelajah summary) ───────────────────────────────────

    fun openTasteSheet() = _state.update { it.copy(tasteDraft = it.answers.taste) }
    fun closeTasteSheet() = _state.update { it.copy(tasteDraft = null) }

    private fun draft(change: (InterviewTaste) -> InterviewTaste) = _state.update { s -> s.tasteDraft?.let { s.copy(tasteDraft = change(it)) } ?: s }
    fun draftToggleInterest(interest: Interest) = draft { t -> t.copy(interests = if (interest in t.interests) t.interests - interest else t.interests + interest) }
    fun draftToggleExtra(label: String) = draft { t -> t.copy(extraInterests = if (label in t.extraInterests) t.extraInterests - label else t.extraInterests + label) }
    fun draftStyle(style: TravelStyle) = draft { it.copy(style = style) }
    fun draftPriceLevel(level: PriceLevel) = draft { it.copy(priceLevel = level) }
    fun draftNotes(value: String) = draft { it.copy(notes = value.take(app.ransel.shared.domain.TASTE_NOTES_MAX)) }

    /** Simpan: the draft becomes this trip's Seleramu (only this trip's, never the account's). It needs an interest. */
    fun saveTasteSheet() {
        val taste = _state.value.tasteDraft ?: return
        if (!taste.canSaveFromSheet()) return
        _state.update { it.copy(answers = it.answers.copy(taste = taste), tasteDraft = null) }
    }

    fun onNameChange(value: String) = _state.update { it.copy(customName = value) }
    fun onBudgetChange(value: String) {
        _state.update { it.copy(budgetDigits = value.filter(Char::isDigit).take(12)) }
        loadBudgetPrice()
    }

    /** The price range of the budget: asked from the server (it knows the destination's prices and the rates), online and signed in only. */
    private fun loadBudgetPrice() {
        budgetJob?.cancel()
        val s = _state.value
        val api = api
        val minor = s.budgetMinor()
        val destination = s.destinationName.trim()
        if (door != InterviewDoor.EXPLORE || api == null || !s.signedIn || s.offline || minor == null || minor <= 0 || destination.length < 2) {
            _state.update { it.copy(budgetPriceLevel = null) }
            return
        }
        budgetJob = viewModelScope.launch {
            delay(BUDGET_TYPING_MILLIS)
            val undecided = s.answers.datesUndecided
            val start = s.answers.startDate
            val end = s.answers.endDate
            val days = if (!undecided && start != null && end != null) start.daysUntil(end) + 1 else null
            val month = if (undecided) null else (start ?: today()).monthKey()
            val language = preferences.observe().first().language.tag
            val result = withContext(Dispatchers.Default) {
                withTimeoutOrNull(KHAS_TIMEOUT_MILLIS) {
                    api.exploreSuggestions(
                        destination = destination, month = month, language = language,
                        budgetMinor = minor, budgetCurrency = s.currency, budgetDays = days,
                    )
                }
            }
            val level = (result as? ApiResult.Success)?.value?.suggestedPriceLevel
            _state.update { it.copy(budgetPriceLevel = level) }
        }
    }

    private fun InterviewUiState.budgetMinor(): Long? =
        budgetDigits.takeIf { it.isNotEmpty() }?.toLongOrNull()?.times(pow10(currencyDecimals(currency)))

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
        // the summary shows the range of the budget: ask again, the destination or the dates may have changed
        if (step == null) loadBudgetPrice()
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

    /**
     * Loads the destination's own interests for W4 from `GET /explore/suggestions`. Guests, offline, and a missing API
     * never call it (the "Umum" chips stay); a timeout, 401/403 or empty list hides the Khas box — generic is fallback
     * only, not a second generator.
     */
    private fun loadKhas() {
        val s = _state.value
        val api = api ?: return
        val destination = s.destinationName.trim()
        if (!canLoadDestinationInterests(s.signedIn, s.offline, destination)) return
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
        val budgetMinor = s.budgetMinor()
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
                addIdeaItem(id)
                val title = s.tripName.trim().ifEmpty { destination.name }
                val form = s.toExploreForm(id, title)
                // the answers of W4 are the trip's Seleramu on both doors (per trip, per device)
                if (!s.answers.taste.isEmpty) explore?.saveTaste(form)
                val canSearch = door == InterviewDoor.EXPLORE && s.signedIn && !s.offline && api != null && explore != null
                if (canSearch) {
                    // "Buat trip dan cari": the trip must be on the server before the search; then the swipe screen asks for the cards
                    awaitOnServer(id)
                    explore!!.save(ExploreSession(form))
                    events.send(InterviewEvent.CreatedAndSearch(id))
                } else {
                    // from Jelajah offline or as a guest: only the trip (the search is run again from Jelajah); no Ajak teman there
                    events.send(InterviewEvent.Created(id, signedIn = s.signedIn && door == InterviewDoor.MY_TRIPS))
                }
            }.onFailure {
                _state.update { st -> st.copy(creating = false, createFailed = true) }
            }
        }
    }

    /**
     * From an idea: one item in "Belum dijadwalkan" (no day), title = the activity, place = the region as an area (no point),
     * no note and no label: AI-made items look the same whatever their source (owner, seq 1077). Free: no request is made.
     * A failure here does not undo the trip.
     */
    private suspend fun addIdeaItem(tripId: String) {
        val (activity, region) = ideaItem ?: return
        val repo = itinerary ?: return
        runCatching {
            repo.save(
                tripId,
                app.ransel.shared.dto.ItineraryItemInput(
                    id = newId(), dayDate = null, title = activity, category = app.ransel.shared.model.Category.ATTRACTION,
                    placeName = region, placeKind = app.ransel.shared.dto.PlaceKind.AREA,
                    placeCity = region.substringBefore(',').trim().ifEmpty { null },
                    source = app.ransel.shared.model.ItemSource.AI,
                ),
            )
        }
    }

    /** What the search starts with: the answers of W4 and the sheet, the trip's own destination and dates. */
    private fun InterviewUiState.toExploreForm(tripId: String, title: String): ExploreForm {
        val taste = answers.taste
        return ExploreForm(
            tripId = tripId, tripTitle = title, destination = destinationName,
            startDate = answers.startDate.takeIf { !answers.datesUndecided },
            endDate = answers.endDate.takeIf { !answers.datesUndecided },
            priceLevel = priceLevel,
            interests = taste.interests.toList(),
            // the server takes at most 5 of the destination's own interests
            extraInterests = taste.extraInterests.toList().take(5),
            style = taste.style ?: TravelStyle.SANTAI,
            notes = taste.notes,
        )
    }

    /** Mobile saves locally first: push now and wait until the server knows the trip (the search needs it). */
    private suspend fun awaitOnServer(id: String) {
        val api = api ?: return
        sync.syncNow()
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(TRIP_SYNC_WAIT_MILLIS) {
                while (true) {
                    if ((api.trips() as? ApiResult.Success)?.value?.any { it.id == id } == true) return@withTimeoutOrNull
                    delay(TRIP_SYNC_POLL_MILLIS)
                }
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
        const val BUDGET_TYPING_MILLIS = 600L
        const val TRIP_SYNC_WAIT_MILLIS = 10_000L
        const val TRIP_SYNC_POLL_MILLIS = 500L
    }
}
