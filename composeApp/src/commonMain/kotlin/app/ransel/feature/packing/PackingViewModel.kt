package app.ransel.feature.packing

import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.PackingRepository
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PatchPackingItemRequest
import app.ransel.shared.model.PackingCategory
import app.ransel.ui.category.packingCategoryOrder
import app.ransel.util.newId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PackingGroup(val category: PackingCategory, val items: List<PackingItemDto>) {
    val packed: Int get() = items.count { it.isPacked }
    val isComplete: Boolean get() = items.isNotEmpty() && packed == items.size
}

data class PackingUiState(
    val loading: Boolean = true,
    val groups: List<PackingGroup> = emptyList(),
) {
    val total: Int get() = groups.sumOf { it.items.size }
    val packed: Int get() = groups.sumOf { it.packed }
    /** 0..100, rounded. */
    val percent: Int get() = if (total == 0) 0 else (packed * 100 + total / 2) / total
}

class PackingViewModel(
    private val tripId: String,
    private val packing: PackingRepository,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    val state: StateFlow<PackingUiState> = packing.observe(tripId)
        .map { items ->
            PackingUiState(
                loading = false,
                groups = packingCategoryOrder()
                    .map { category -> PackingGroup(category, items.filter { it.category == category }) }
                    .filter { it.items.isNotEmpty() },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PackingUiState())

    fun toggle(item: PackingItemDto) {
        viewModelScope.launch {
            packing.update(tripId, item.id, PatchPackingItemRequest(isPacked = !item.isPacked))
        }
        if (!item.isPacked) {
            val items = state.value.groups.flatMap { it.items }
            val packed = items.count { it.isPacked || it.id == item.id }
            analytics.event(AnalyticsEvent.PackingItemChecked(if (items.isEmpty()) 100 else packed * 100 / items.size))
        }
    }

    fun add(name: String, category: PackingCategory, quantity: Int) {
        val order = (state.value.groups.flatMap { it.items }.maxOfOrNull { it.sortOrder } ?: 0.0) + 1.0
        viewModelScope.launch {
            packing.add(tripId, CreatePackingItemRequest(newId(), category, name.trim(), quantity, order))
        }
    }

    fun edit(item: PackingItemDto, name: String, category: PackingCategory, quantity: Int) {
        viewModelScope.launch {
            packing.update(
                tripId, item.id,
                PatchPackingItemRequest(category = category, name = name.trim(), quantity = quantity),
            )
        }
    }

    fun delete(item: PackingItemDto) {
        viewModelScope.launch { packing.delete(tripId, item.id) }
    }

    /** Bersama "Saya bawa". */
    fun bring(item: PackingItemDto, myMemberId: String) {
        viewModelScope.launch { packing.bring(tripId, item.id, myMemberId) }
    }

    fun giveBack(item: PackingItemDto) {
        viewModelScope.launch { packing.giveBack(tripId, item.id) }
    }

    fun uncheckAll() {
        viewModelScope.launch { packing.uncheckAll(tripId) }
    }
}
