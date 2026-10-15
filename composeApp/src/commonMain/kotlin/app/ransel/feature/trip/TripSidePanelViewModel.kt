package app.ransel.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.packing.PackingGroup
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.model.PackingCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TripSidePanelState(
    val summary: TripSummaryDto? = null,
    val packingGroups: List<PackingGroup> = emptyList(),
)

class TripSidePanelViewModel(
    tripId: String,
    trips: TripRepository,
    packing: PackingRepository,
) : ViewModel() {
    val state: StateFlow<TripSidePanelState> = combine(
        trips.observeSummary(tripId),
        packing.observe(tripId),
    ) { summary, items ->
        TripSidePanelState(
            summary = summary,
            packingGroups = PackingCategory.entries
                .map { category -> PackingGroup(category, items.filter { it.category == category }) }
                .filter { it.items.isNotEmpty() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripSidePanelState())
}
