package app.ransel.server.ai

import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

/**
 * Fetches place photos through [PlacesClient] and keeps a small LRU of the results (a swipe stack asks for the same
 * photo again after a reload, and every miss is a billed Google call). "No photo" is cached too; failures are not.
 */
class PlacePhotoService(
    private val places: PlacesClient,
    private val metrics: AiMetrics,
    private val maxEntries: Int = 200,
    private val ttl: Duration = 1.hours,
) {
    private val log = LoggerFactory.getLogger(PlacePhotoService::class.java)

    private class Entry(val photo: PlacePhoto?, val at: TimeSource.Monotonic.ValueTimeMark)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > maxEntries
    }

    /** Null = no photo, or Google refused / failed. Never throws, so the route can always answer 404. */
    suspend fun photo(placeId: String): PlacePhoto? {
        synchronized(cache) {
            cache[placeId]?.let { if (it.at.elapsedNow() < ttl) return it.photo else cache.remove(placeId) }
        }
        metrics.placesPhotoCalls.incrementAndGet()
        val photo = try {
            places.photo(placeId)
        } catch (e: PlacesQuotaException) {
            metrics.placesPhotoQuotaHits.incrementAndGet()
            log.warn("Place photo quota/permission rejection: {}", e.message)
            return null
        } catch (e: Exception) {
            log.warn("Place photo failed: {}", e.javaClass.simpleName)
            return null
        }
        synchronized(cache) { cache[placeId] = Entry(photo, TimeSource.Monotonic.markNow()) }
        return photo
    }
}
