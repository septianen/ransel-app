package app.ransel.feature.tripform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.shared.domain.startFromTemplate
import app.ransel.shared.dto.TripTemplateDto
import app.ransel.util.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TripTemplatesUiState(
    val templates: List<TripTemplateDto> = emptyList(),
    val chosen: TripTemplateDto? = null,
)

/**
 * "Pakai template trip" on Buat trip: the user's trip templates (online, signed in). After the trip is saved, the chosen
 * template's activities and packing list go into it through the normal (offline-first) repositories.
 */
class TripTemplatesViewModel(
    private val api: RanselApi,
    session: SessionManager,
    private val itinerary: ItineraryRepository,
    private val packing: PackingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TripTemplatesUiState())
    val state: StateFlow<TripTemplatesUiState> = _state.asStateFlow()

    init {
        if (session.user != null) {
            viewModelScope.launch {
                (api.tripTemplates() as? ApiResult.Success)?.let { r -> _state.update { it.copy(templates = r.value) } }
            }
        }
    }

    fun choose(template: TripTemplateDto?) = _state.update { it.copy(chosen = template) }

    fun delete(template: TripTemplateDto) {
        viewModelScope.launch {
            if (api.deleteTripTemplate(template.id) is ApiResult.Success) {
                _state.update { s -> s.copy(templates = s.templates - template, chosen = s.chosen.takeIf { it?.id != template.id }) }
            }
        }
    }

    /** Fills the new trip; [done] runs when everything is written. Without dates every activity is unscheduled. */
    fun applyTo(tripId: String, start: LocalDate?, end: LocalDate?, done: () -> Unit) {
        val template = _state.value.chosen ?: return done()
        viewModelScope.launch {
            val fill = startFromTemplate(template, start, end, ::newId)
            fill.items.forEach { itinerary.save(tripId, it) }
            packing.addAll(tripId, fill.packing)
            done()
        }
    }
}
