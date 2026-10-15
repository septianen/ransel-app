package app.ransel.feature.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PackingRepository
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.CreatePackingTemplateRequest
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PackingTemplateDto
import app.ransel.shared.dto.TemplateItem
import app.ransel.util.newId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val loading: Boolean = true,
    val templates: List<PackingTemplateDto> = emptyList(),
    val failure: ApiFailure? = null,
    val selectedId: String? = null,
    val skipExisting: Boolean = true,
    val busy: Boolean = false,
) {
    val builtIn: List<PackingTemplateDto> get() = templates.filter { it.ownerId == null }
    val mine: List<PackingTemplateDto> get() = templates.filter { it.ownerId != null }
    val selected: PackingTemplateDto? get() = templates.firstOrNull { it.id == selectedId }
}

sealed interface TemplateEvent {
    /** [count] items were added to the list. */
    data class Applied(val count: Int) : TemplateEvent
    data object Saved : TemplateEvent
    data class Problem(val code: String) : TemplateEvent
}

/** Same name (case-insensitive) in the same category counts as already on the list. */
private fun key(category: app.ransel.shared.model.PackingCategory, name: String) = category to name.trim().lowercase()

/** The items of [template] that [applyTemplate] would add to a list that already holds [existing]. */
fun templateItemsToAdd(template: PackingTemplateDto, existing: List<PackingItemDto>, skipExisting: Boolean): List<TemplateItem> {
    if (!skipExisting) return template.items
    val have = existing.map { key(it.category, it.name) }.toSet()
    return template.items.filter { key(it.category, it.name) !in have }
}

class PackingTemplatesViewModel(
    private val tripId: String,
    private val api: RanselApi,
    private val packing: PackingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()

    private val events = Channel<TemplateEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun load() {
        _state.update { it.copy(loading = it.templates.isEmpty(), failure = null) }
        viewModelScope.launch {
            when (val result = api.packingTemplates()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        templates = result.value,
                        selectedId = it.selectedId?.takeIf { id -> result.value.any { t -> t.id == id } }
                            ?: result.value.firstOrNull { t -> t.ownerId == null }?.id,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = result.error) }
            }
        }
    }

    fun select(id: String) = _state.update { it.copy(selectedId = id) }
    fun setSkipExisting(skip: Boolean) = _state.update { it.copy(skipExisting = skip) }

    /** Adds the selected template's items to this trip's list, unchecked, after the existing ones. */
    fun apply() {
        val s = _state.value
        val template = s.selected ?: return
        if (s.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val existing = packing.observe(tripId).first()
            val toAdd = templateItemsToAdd(template, existing, s.skipExisting)
            var order = (existing.maxOfOrNull { it.sortOrder } ?: 0.0)
            packing.addAll(
                tripId,
                toAdd.map { item ->
                    order += 1.0
                    CreatePackingItemRequest(newId(), item.category, item.name, item.quantity ?: 1, order)
                },
            )
            _state.update { it.copy(busy = false) }
            events.send(TemplateEvent.Applied(toAdd.size))
        }
    }

    /** "Simpan sebagai template": sends the items the list shows now, never the ticked state. */
    fun save(name: String, keepQuantities: Boolean, share: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val items = packing.observe(tripId).first().map {
                TemplateItem(it.category, it.name, it.quantity.takeIf { keepQuantities })
            }
            val request = CreatePackingTemplateRequest(
                id = newId(),
                name = name.trim(),
                items = items,
                sharedTripId = tripId.takeIf { share },
            )
            when (val result = api.createPackingTemplate(request)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busy = false, templates = it.templates + result.value) }
                    events.send(TemplateEvent.Saved)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(busy = false) }
                    events.send(TemplateEvent.Problem(result.error.code))
                }
            }
        }
    }
}
