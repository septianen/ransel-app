package app.ransel.data.explore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Asks the trip screen to open its "Cari tempat" sheet (plan explore-trip-one-tap): from the Explore trip picker, "Ubah
 * preferensi" on the swipe screen and old `trips/<id>/explore/new` links, which used to open the removed form.
 */
class TripExploreRequests {
    private val _tripId = MutableStateFlow<String?>(null)
    val tripId: StateFlow<String?> = _tripId.asStateFlow()

    fun open(tripId: String) { _tripId.value = tripId }

    /** The trip screen of [tripId] took the request; true when it was for that trip. */
    fun consume(tripId: String): Boolean = (_tripId.value == tripId).also { if (it) _tripId.value = null }
}
