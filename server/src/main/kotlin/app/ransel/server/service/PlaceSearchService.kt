package app.ransel.server.service

import app.ransel.server.ai.PlaceSearch
import app.ransel.server.db.PlaceAutocompleteCache
import app.ransel.server.db.PlaceDetailsCache
import app.ransel.server.db.Trips
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.PLACE_QUERY_MIN
import app.ransel.shared.domain.normalizeSearch
import app.ransel.shared.domain.regionSuggestions
import app.ransel.shared.dto.PlaceDto
import app.ransel.shared.dto.PlaceSuggestionsDto
import app.ransel.shared.dto.PopularDestinationDto
import app.ransel.shared.dto.ReverseGeocodeDto
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Destination search (TRD "Places"): Google behind a Postgres cache, the built-in region list when Google is down or
 * slow, and the places groups pick most often. [search] null = no Places key configured (suggestions use the list,
 * details and reverse answer 503).
 */
class PlaceSearchService(
    private val search: PlaceSearch?,
    private val fallbackAfterMillis: Long = 1_500,
    private val popularMinOwners: Int = 3,
) {
    private val log = LoggerFactory.getLogger(PlaceSearchService::class.java)

    @Volatile private var popular: Pair<Instant, List<PopularDestinationDto>>? = null

    suspend fun autocomplete(query: String, session: String?, near: Pair<Double, Double>?): PlaceSuggestionsDto {
        val q = query.trim()
        requireValid(if (q.length > MAX_QUERY) mapOf("q" to "TOO_LONG") else emptyMap())
        if (normalizeSearch(q).length < PLACE_QUERY_MIN) return PlaceSuggestionsDto(emptyList())
        // a bias point rounded to about 10 km keeps the cache useful
        val key = normalizeSearch(q) + (near?.let { "@" + round1(it.first) + "," + round1(it.second) } ?: "")
        cachedSuggestions(key)?.let { return PlaceSuggestionsDto(it) }
        val found = try {
            val s = search ?: throw IllegalStateException("places not configured")
            withTimeout(fallbackAfterMillis) { s.autocomplete(q, session.validToken(), near) }
        } catch (e: TimeoutCancellationException) {
            log.warn("places.autocomplete timeout, using the built-in list")
            null
        } catch (e: Exception) {
            log.warn("places.autocomplete failed: {}, using the built-in list", e.toString())
            null
        } ?: return PlaceSuggestionsDto(regionSuggestions(q), fallback = true)
        transaction {
            PlaceAutocompleteCache.upsert {
                it[queryKey] = key
                it[suggestions] = found
                it[createdAt] = Instant.now()
            }
        }
        return PlaceSuggestionsDto(found)
    }

    suspend fun details(placeId: String, session: String?): PlaceDto {
        requireValid(if (placeId.isBlank() || placeId.length > MAX_PLACE_ID) mapOf("placeId" to "INVALID") else emptyMap())
        transaction {
            PlaceDetailsCache.selectAll().where { PlaceDetailsCache.placeId eq placeId }.singleOrNull()
                ?.takeIf { Duration.between(it[PlaceDetailsCache.fetchedAt], Instant.now()) < DETAILS_TTL }
                ?.get(PlaceDetailsCache.place)
        }?.let { return it }
        val place = try {
            (search ?: throw IllegalStateException("places not configured")).details(placeId, session.validToken())
        } catch (e: Exception) {
            log.warn("places.details failed: {}", e.toString())
            throw unavailable()
        } ?: throw notFound("Place")
        transaction {
            PlaceDetailsCache.upsert {
                it[PlaceDetailsCache.placeId] = placeId
                it[PlaceDetailsCache.place] = place
                it[fetchedAt] = Instant.now()
            }
        }
        return place
    }

    /** The coordinates are only passed on to Google: never logged, never stored. */
    suspend fun reverse(lat: Double, lng: Double): ReverseGeocodeDto {
        requireValid(
            buildMap {
                if (lat !in -90.0..90.0) put("lat", "OUT_OF_RANGE")
                if (lng !in -180.0..180.0) put("lng", "OUT_OF_RANGE")
            },
        )
        return try {
            ReverseGeocodeDto((search ?: throw IllegalStateException("places not configured")).reverse(lat, lng))
        } catch (e: Exception) {
            log.warn("places.reverse failed: {}", e.javaClass.simpleName)
            throw unavailable()
        }
    }

    /**
     * Places picked by at least [popularMinOwners] different trip owners in the last 90 days, so no single group can be
     * recognised; recomputed at most once a day.
     */
    fun popular(): List<PopularDestinationDto> {
        popular?.let { (at, list) -> if (Duration.between(at, Instant.now()) < POPULAR_TTL) return list }
        val owners = Trips.ownerId.countDistinct()
        val name = Trips.destination.max()
        val since = Instant.now().minus(Duration.ofDays(90))
        val list = transaction {
            Trips.select(Trips.destPlaceId, owners, name)
                .where { Trips.destPlaceId.isNotNull() and Trips.deletedAt.isNull() and (Trips.createdAt greaterEq since) }
                .groupBy(Trips.destPlaceId)
                .having { owners greaterEq popularMinOwners.toLong() }
                .orderBy(owners to SortOrder.DESC)
                .limit(POPULAR_COUNT)
                .mapNotNull { row ->
                    val placeId = row[Trips.destPlaceId] ?: return@mapNotNull null
                    PopularDestinationDto(placeId, row[name] ?: return@mapNotNull null)
                }
        }
        popular = Instant.now() to list
        return list
    }

    private fun cachedSuggestions(key: String) = transaction {
        PlaceAutocompleteCache.selectAll().where { PlaceAutocompleteCache.queryKey eq key }.singleOrNull()
            ?.takeIf { Duration.between(it[PlaceAutocompleteCache.createdAt], Instant.now()) < AUTOCOMPLETE_TTL }
            ?.get(PlaceAutocompleteCache.suggestions)
    }

    private fun unavailable() = ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.PLACES_UNAVAILABLE, "Place search is unavailable")

    /** A session token is the app's UUID; anything else is not passed on. */
    private fun String?.validToken(): String? = this?.takeIf { it.length in 8..64 && it.all { c -> c.isLetterOrDigit() || c == '-' } }

    private fun round1(value: Double) = (value * 10).roundToInt() / 10.0

    companion object {
        private const val MAX_QUERY = 100
        private const val MAX_PLACE_ID = 300
        private const val POPULAR_COUNT = 6
        private val AUTOCOMPLETE_TTL: Duration = Duration.ofHours(24)
        private val DETAILS_TTL: Duration = Duration.ofDays(30)
        private val POPULAR_TTL: Duration = Duration.ofHours(24)
    }
}
