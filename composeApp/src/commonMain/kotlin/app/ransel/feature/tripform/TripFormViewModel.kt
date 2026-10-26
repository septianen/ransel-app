package app.ransel.feature.tripform

import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import app.ransel.shared.domain.currencyDecimals
import app.ransel.shared.domain.validateTrip
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.dto.UpdateTripRequest
import app.ransel.util.newId
import app.ransel.util.plusDays
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
    val startDate: LocalDate,
    val endDate: LocalDate,
    val currency: String = "IDR",
    /** Whole units of the currency, digits only ("5000000"). Shown with thousand separators. */
    val budgetDigits: String = "",
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    /** Editing a trip that already has expenses: the base currency is fixed. */
    val currencyLocked: Boolean = false,
    /** Counts for the delete dialog; non-null = dialog visible. */
    val deleteSummary: TripSummaryDto? = null,
) {
    val titleError: Boolean get() = showErrors && title.isBlank()
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
) : ViewModel() {
    private val _state = MutableStateFlow(
        TripFormState(isEditing = tripId != null, startDate = today(), endDate = today().plusDays(3)),
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
                            startDate = trip.startDate,
                            endDate = trip.endDate,
                            currency = trip.baseCurrency,
                            budgetDigits = trip.budgetMinor?.let { b -> (b / divisor).toString() }.orEmpty(),
                            currencyLocked = summary.expenseCount > 0,
                        )
                    }
                }
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

    fun save() {
        val s = _state.value
        if (s.saving) return
        val budgetMinor = s.budgetDigits.takeIf { it.isNotEmpty() }?.toLongOrNull()?.times(pow10(currencyDecimals(s.currency)))
        val problems = validateTrip(s.title, s.startDate, s.endDate, s.currency, budgetMinor)
        if (problems.isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = tripId ?: newId()
            if (tripId == null) {
                trips.create(
                    CreateTripRequest(
                        id = id, title = s.title, destination = s.destination, startDate = s.startDate,
                        endDate = s.endDate, baseCurrency = s.currency, budgetMinor = budgetMinor,
                        destinationPlace = s.destinationPlace,
                        countryCode = s.countryCode, localCurrency = s.localCurrency, timeZone = s.timeZone,
                    ),
                )
                analytics.event(AnalyticsEvent.TripCreated)
            } else {
                trips.update(
                    tripId,
                    UpdateTripRequest(
                        title = s.title, destination = s.destination, startDate = s.startDate,
                        endDate = s.endDate, baseCurrency = s.currency, budgetMinor = budgetMinor,
                        destinationPlace = s.destinationPlace,
                        countryCode = s.countryCode, localCurrency = s.localCurrency, timeZone = s.timeZone,
                    ),
                )
            }
            events.send(TripFormEvent.Saved(id))
        }
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
