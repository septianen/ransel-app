package app.ransel.data.explore

import app.ransel.data.remote.ApiJson
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PreferencesRepository
import app.ransel.feature.itinerary.toInput
import app.ransel.shared.dto.InspirationCardDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** An idea card waiting to reach the server with its item (the trip is made on the phone first and synced later). */
@Serializable
data class PendingIdea(val tripId: String, val card: InspirationCardDto)

/**
 * The idea cards behind items made by "Bikin trip dari sini" (plan idea-activity-detail, owner 23 Nov 2026). The card is
 * kept on this device until the server has it (`PUT .../itinerary/{itemId}/idea`); then every device and member reads it
 * from the server. Uploading also gives the region's point, which goes onto the item so the day map shows it. Nothing
 * here calls the AI or charges credits.
 */
class ItineraryIdeas(
    private val api: RanselApi?,
    private val itinerary: ItineraryRepository,
    private val preferences: PreferencesRepository,
) {
    private val pending = MutableStateFlow<Map<String, PendingIdea>?>(null)

    private suspend fun all(): Map<String, PendingIdea> = pending.value ?: runCatching {
        preferences.getString(KEY)?.let { ApiJson.decodeFromString(SERIALIZER, it) }
    }.getOrNull().orEmpty().also { loaded -> pending.update { it ?: loaded } }

    private suspend fun write(change: (Map<String, PendingIdea>) -> Map<String, PendingIdea>) {
        val next = change(all())
        pending.value = next
        preferences.putString(KEY, if (next.isEmpty()) null else ApiJson.encodeToString(SERIALIZER, next))
    }

    /** Right after "Buat trip": the card waits here for the upload. */
    suspend fun add(tripId: String, itemId: String, card: InspirationCardDto) = write { it + (itemId to PendingIdea(tripId, card)) }

    /** The card of [itemId] still on this device; null once uploaded (or never made here). */
    suspend fun local(itemId: String): InspirationCardDto? = all()[itemId]?.card

    suspend fun hasPending(tripId: String): Boolean = all().values.any { it.tripId == tripId }

    /**
     * Uploads every card of [tripId] still on this device. A trip the server does not know yet (not synced) stays for the
     * next try. Returns true when nothing is left.
     */
    suspend fun push(tripId: String): Boolean {
        val api = api ?: return false
        all().filterValues { it.tripId == tripId }.forEach { (itemId, idea) ->
            when (val result = api.saveItineraryIdea(tripId, itemId, idea.card)) {
                is ApiResult.Success -> {
                    val dto = result.value
                    val lat = dto.lat
                    val lng = dto.lng
                    if (lat != null && lng != null) {
                        val item = itinerary.observe(tripId).first().firstOrNull { it.id == itemId }
                        // the region's point, unless the user already placed the item somewhere
                        if (item != null && item.lat == null && item.lng == null) itinerary.save(tripId, item.toInput().copy(lat = lat, lng = lng))
                    }
                    write { it - itemId }
                }
                // the item was deleted, or the trip left: nothing to keep
                is ApiResult.Failure -> if (result.error.isNotFound && itinerary.observe(tripId).first().none { it.id == itemId }) write { it - itemId }
            }
        }
        return !hasPending(tripId)
    }

    private companion object {
        const val KEY = "itinerary_ideas_pending"
        val SERIALIZER = MapSerializer(String.serializer(), PendingIdea.serializer())
    }
}
