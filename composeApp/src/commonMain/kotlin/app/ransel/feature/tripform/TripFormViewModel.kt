package app.ransel.feature.tripform

import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Role
import app.ransel.ui.components.PhotoProblem
import app.ransel.ui.components.photoProblem
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.sync.NoSyncStatus
import app.ransel.data.sync.SyncStatus
import app.ransel.shared.domain.DATE_POLL_MAX_OPTIONS
import app.ransel.feature.polls.datePollOptionsValid
import app.ransel.shared.domain.defaultMultiChoice
import app.ransel.shared.domain.effectiveAnonymous
import app.ransel.shared.dto.CreatePollRequest
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.model.PollKind
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.withTimeoutOrNull
import app.ransel.data.repository.TripRepository
import app.ransel.shared.domain.currencyDecimals
import app.ransel.shared.domain.validateTrip
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.dto.UpdateTripRequest
import app.ransel.util.newId
import app.ransel.util.today
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import app.ransel.feature.destination.DestinationChoice
import app.ransel.shared.domain.localeForPlace
import app.ransel.shared.dto.TripPlaceDto
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TripFormState(
    val isEditing: Boolean,
    val loading: Boolean = true,
    val title: String = "",
    val destination: String = "",
    /** Where [destination] is, when it was picked from the suggestions. */
    val destinationPlace: TripPlaceDto? = null,
    /** From the picked destination (overseas trips); null = home. */
    val countryCode: String? = null,
    val localCurrency: String? = null,
    val timeZone: String? = null,
    /**
     * Null until picked: a new trip starts without dates, so nobody saves today's dates by accident (owner,
     * 30 Oct 2026); also an edited trip that has no dates yet.
     */
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val currency: String = "IDR",
    /** Whole units of the currency, digits only ("5000000"). Shown with thousand separators. */
    val budgetDigits: String = "",
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    /** Editing a trip that already has expenses: the base currency is fixed. */
    val currencyLocked: Boolean = false,
    /** Editing: the start date as saved, still allowed when it is already past (a trip under way keeps its start). */
    val savedStartDate: LocalDate? = null,
    /** Counts for the delete dialog; non-null = dialog visible. */
    val deleteSummary: TripSummaryDto? = null,
    /** "Tanggal belum pasti" can be switched on: a new trip of a signed-in user (polls need an account). */
    val canAskDates: Boolean = false,
    /** The switch is on: the trip is made without dates and the group picks them in a DATE poll. */
    val datesUndecided: Boolean = false,
    /** The DATE poll draft: its ranges and when voting closes (design: "tutup 3 hari lagi"). */
    val pollOptions: List<PollOptionInput> = emptyList(),
    val pollClosingDays: Int = 3,
    /** The question the poll is made with ("Kapan berangkat?"), from the screen's strings. */
    val pollQuestion: String = "",
    /** "Ubah": the date poll sheet is open. */
    val pollSheet: Boolean = false,
    /** The trip was saved but its DATE poll could not be made (offline): the dialog that says so. */
    val pollFailedTripId: String? = null,
    /** The cover photo as the server has it (B19.1). */
    val coverUrl: String? = null,
    /** Editing, signed in, EDITOR or above: "Ganti foto" shows (the upload needs the account and a connection). */
    val canChangeCover: Boolean = false,
    /** An upload or removal is under way. */
    val coverBusy: Boolean = false,
    val coverProblem: PhotoProblem? = null,
) {
    val titleError: Boolean get() = showErrors && title.isBlank()

    /** A new trip needs its dates, unless "Tanggal belum pasti" is on (an edited trip without dates may stay so). */
    val datesMissing: Boolean get() = !isEditing && !(datesUndecided && canAskDates) && (startDate == null || endDate == null)

    val datesError: Boolean get() = showErrors && datesMissing

    /** The options are not 2–5 ranges from today on (shown after a save attempt). */
    val pollError: Boolean get() = showErrors && datesUndecided && !datePollOptionsValid(pollOptions, today())
}

sealed interface TripFormEvent {
    data class Saved(val tripId: String) : TripFormEvent
    data object Deleted : TripFormEvent
}

class TripFormViewModel(
    private val tripId: String?,
    private val trips: TripRepository,
    private val preferences: PreferencesRepository,
    private val analytics: Analytics = NoAnalytics,
    /** The DATE poll of a trip without dates is made through the API (signed in only). */
    private val api: RanselApi? = null,
    private val session: SessionManager? = null,
    private val sync: SyncStatus = NoSyncStatus,
) : ViewModel() {
    private val _state = MutableStateFlow(
        TripFormState(
            isEditing = tripId != null, startDate = null, endDate = null,
            canAskDates = tripId == null && api != null && session?.user != null,
        ),
    )
    val state: StateFlow<TripFormState> = _state.asStateFlow()

    private val events = Channel<TripFormEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (tripId == null) {
                val currency = preferences.observe().first().defaultCurrency
                _state.update { it.copy(loading = false, currency = currency) }
            } else {
                val trip = trips.observeTrip(tripId).first()
                val summary = trips.observeSummary(tripId).first()
                if (trip == null) {
                    _state.update { it.copy(loading = false) }
                } else {
                    val divisor = pow10(currencyDecimals(trip.baseCurrency))
                    _state.update {
                        it.copy(
                            loading = false,
                            title = trip.title,
                            destination = trip.destination,
                            destinationPlace = trip.destinationPlace,
                            countryCode = trip.countryCode,
                            localCurrency = trip.localCurrency,
                            timeZone = trip.timeZone,
                            // a trip without dates gets them here with the range picker
                            startDate = trip.startDate,
                            savedStartDate = trip.startDate,
                            endDate = trip.endDate,
                            currency = trip.baseCurrency,
                            budgetDigits = trip.budgetMinor?.let { b -> (b / divisor).toString() }.orEmpty(),
                            currencyLocked = summary.expenseCount > 0,
                            coverUrl = trip.coverUrl,
                            canChangeCover = api != null && session?.user != null && trip.myRole != Role.VIEWER,
                        )
                    }
                }
            }
        }
    }

    /** A photo picked and shrunk on the device (`PhotoSourceSheet`) becomes the cover; online only. */
    fun changeCover(jpeg: ByteArray) = coverCall { api, id -> api.setTripCover(id, jpeg) }

    fun removeCover() = coverCall { api, id -> api.clearTripCover(id) }

    fun coverUnreadable() = _state.update { it.copy(coverProblem = PhotoProblem.UNREADABLE) }

    private fun coverCall(block: suspend (RanselApi, String) -> ApiResult<TripDto>) {
        val api = api ?: return
        val id = tripId ?: return
        if (_state.value.coverBusy) return
        _state.update { it.copy(coverBusy = true, coverProblem = null) }
        viewModelScope.launch {
            when (val result = block(api, id)) {
                is ApiResult.Success -> {
                    trips.applyCover(id, result.value.coverUrl)
                    _state.update { it.copy(coverBusy = false, coverUrl = result.value.coverUrl) }
                }
                is ApiResult.Failure -> _state.update { it.copy(coverBusy = false, coverProblem = result.error.photoProblem()) }
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }
    fun onDestinationChange(value: String) =
        _state.update { it.copy(destination = value, destinationPlace = null, countryCode = null, localCurrency = null, timeZone = null) }

    /** The place sets its country, the local currency and the zone; the main currency stays as chosen (IDR by default). */
    fun onDestinationPicked(choice: DestinationChoice) {
        val locale = localeForPlace(choice.countryCode, choice.timeZone)
        _state.update {
            it.copy(
                destination = choice.name, destinationPlace = choice.place,
                countryCode = locale.countryCode, localCurrency = locale.localCurrency, timeZone = locale.timeZone,
            )
        }
    }
    fun onBudgetChange(value: String) = _state.update { it.copy(budgetDigits = value.filter(Char::isDigit).take(12)) }
    fun onCurrencyChange(code: String) = _state.update { it.copy(currency = code) }

    fun onDatesChange(start: LocalDate, end: LocalDate) =
        _state.update { it.copy(startDate = start, endDate = if (end < start) start else end) }

    // ── "Tanggal belum pasti" (new trips, signed in) ─────────────────────────

    fun onDatesUndecided(value: Boolean) = _state.update { if (it.canAskDates) it.copy(datesUndecided = value) else it }
    fun prefillPollQuestion(text: String) = _state.update { if (it.pollQuestion.isEmpty()) it.copy(pollQuestion = text) else it }
    fun openPollSheet() = _state.update { it.copy(pollSheet = true) }
    fun closePollSheet() = _state.update { it.copy(pollSheet = false) }
    fun onPollClosingDays(days: Int) = _state.update { it.copy(pollClosingDays = days) }

    fun savePollOption(input: PollOptionInput) = _state.update { s ->
        val exists = s.pollOptions.any { it.id == input.id }
        if (!exists && s.pollOptions.size >= DATE_POLL_MAX_OPTIONS) return@update s
        s.copy(pollOptions = if (exists) s.pollOptions.map { if (it.id == input.id) input else it } else s.pollOptions + input)
    }

    fun removePollOption(id: String) = _state.update { it.copy(pollOptions = it.pollOptions.filterNot { o -> o.id == id }) }

    /** "Lanjut" on the "polling belum dibuat" dialog: the trip is there, go on as after any save. */
    fun acknowledgePollFailure() {
        val id = _state.value.pollFailedTripId ?: return
        _state.update { it.copy(pollFailedTripId = null) }
        viewModelScope.launch { events.send(TripFormEvent.Saved(id)) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val budgetMinor = s.budgetDigits.takeIf { it.isNotEmpty() }?.toLongOrNull()?.times(pow10(currencyDecimals(s.currency)))
        // the switch drops the dates; editing a trip without dates keeps none until some are picked
        val undecided = s.datesUndecided && s.canAskDates
        val start = if (undecided) null else s.startDate
        val end = if (undecided) null else s.endDate
        val problems = validateTrip(s.title, start, end, s.currency, budgetMinor)
        if (problems.isNotEmpty() || s.datesMissing || (undecided && !datePollOptionsValid(s.pollOptions, today()))) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = tripId ?: newId()
            if (tripId == null) {
                trips.create(
                    CreateTripRequest(
                        id = id, title = s.title, destination = s.destination, startDate = start,
                        endDate = end, baseCurrency = s.currency, budgetMinor = budgetMinor,
                        destinationPlace = s.destinationPlace,
                        countryCode = s.countryCode, localCurrency = s.localCurrency, timeZone = s.timeZone,
                    ),
                )
                analytics.event(AnalyticsEvent.TripCreated)
                if (undecided && !createDatePoll(id, s)) {
                    // the trip stays; the poll can be started from Ringkasan later
                    _state.update { it.copy(saving = false, pollFailedTripId = id) }
                    return@launch
                }
            } else {
                trips.update(
                    tripId,
                    UpdateTripRequest(
                        title = s.title, destination = s.destination, startDate = start,
                        endDate = end, baseCurrency = s.currency, budgetMinor = budgetMinor,
                        destinationPlace = s.destinationPlace,
                        countryCode = s.countryCode, localCurrency = s.localCurrency, timeZone = s.timeZone,
                    ),
                )
            }
            events.send(TripFormEvent.Saved(id))
        }
    }

    /**
     * Makes the DATE poll of the trip just created. Mobile saves the trip locally first and the server only takes a poll
     * for a trip it has, so the push is started and awaited a little (like "Ajak teman"). False when it did not work.
     */
    private suspend fun createDatePoll(tripId: String, s: TripFormState): Boolean {
        val api = api ?: return false
        if (tripId in sync.pendingIds.value) {
            sync.syncNow()
            withTimeoutOrNull(TRIP_SYNC_WAIT_MILLIS) { sync.pendingIds.first { tripId !in it } }
        }
        val request = CreatePollRequest(
            id = newId(),
            kind = PollKind.DATE,
            question = s.pollQuestion.trim().ifEmpty { "?" },
            closesAt = Clock.System.now() + s.pollClosingDays.days,
            multiChoice = defaultMultiChoice(PollKind.DATE),
            anonymous = effectiveAnonymous(PollKind.DATE, false),
            applyWinner = true,
            options = s.pollOptions,
        )
        return api.createPoll(tripId, request) is ApiResult.Success
    }

    fun requestDelete() {
        val id = tripId ?: return
        viewModelScope.launch {
            val summary = trips.observeSummary(id).first()
            _state.update { it.copy(deleteSummary = summary) }
        }
    }

    fun dismissDelete() = _state.update { it.copy(deleteSummary = null) }

    fun confirmDelete() {
        val id = tripId ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleteSummary = null, saving = true) }
            trips.delete(id)
            events.send(TripFormEvent.Deleted)
        }
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}

/** How long Buat trip waits for a new trip to reach the server before making its DATE poll. */
private const val TRIP_SYNC_WAIT_MILLIS = 10_000L
