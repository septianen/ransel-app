package app.ransel.server.ai

import app.ransel.server.db.PlaceDetailCache
import app.ransel.server.plugins.ApiException
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.PlaceDetailDto
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * The detail text of a place (about, tips, hours estimate), kept per place id so the itinerary can open it again.
 * [remember] is fed by every search (the text arrives with the recommendations); [get] serves the itinerary and
 * rebuilds an expired or missing row with one small LLM call.
 */
class PlaceDetailService(
    private val ai: AiServices,
    private val photoSigner: PhotoSigner,
    private val fx: FxRates,
    private val metrics: AiMetrics,
) {
    private val log = LoggerFactory.getLogger(PlaceDetailService::class.java)

    /** Keeps the detail text of places that came with one. Places without any text are skipped: nothing to show later. */
    fun remember(places: List<CachedPlace>) {
        val withText = places.filter { it.about != null || it.highlights.isNotEmpty() || it.openingHours.isNotEmpty() || it.bestTime != null }
        if (withText.isEmpty()) return
        val expires = Instant.now().plus(TTL)
        transaction {
            withText.forEach { place ->
                PlaceDetailCache.upsert {
                    it[placeId] = place.placeId
                    it[lang] = LANG
                    it[response] = CachedDetail(
                        name = place.name,
                        address = place.address,
                        lat = place.lat,
                        lng = place.lng,
                        about = place.about,
                        highlights = place.highlights,
                        openingHours = place.openingHours,
                        bestTime = place.bestTime,
                        costUsd = place.costUsd,
                    )
                    it[model] = ai.llm.modelName
                    it[expiresAt] = expires
                }
            }
        }
    }

    /**
     * [name] is the itinerary item's title: needed only to rebuild when the cache has no row at all.
     * [currency] is the trip's base currency; the cost estimate is converted to it when a rate is known.
     */
    suspend fun get(placeId: String, name: String?, currency: String?): PlaceDetailDto {
        if (placeId.isBlank() || placeId.length > MAX_PLACE_ID) throw notFound()
        metrics.placeDetailReads.incrementAndGet()
        val row = readRow(placeId)
        val detail = if (row != null && row.second.isAfter(Instant.now())) {
            metrics.placeDetailCacheHits.incrementAndGet()
            row.first
        } else {
            rebuild(placeId, row?.first, name?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() })
        }
        return toDto(placeId, detail, currency)
    }

    /** Called from the hourly job. */
    fun purgeExpired(): Int = transaction { PlaceDetailCache.deleteWhere { PlaceDetailCache.expiresAt less Instant.now() } }

    private fun readRow(placeId: String): Pair<CachedDetail, Instant>? = transaction {
        PlaceDetailCache.selectAll()
            .where { (PlaceDetailCache.placeId eq placeId) and (PlaceDetailCache.lang eq LANG) }
            .singleOrNull()?.let { it[PlaceDetailCache.response] to it[PlaceDetailCache.expiresAt] }
    }

    /** Needs a name (from the old row or the caller) and a place Google still knows. A failing LLM keeps whatever the old row had. */
    private suspend fun rebuild(placeId: String, old: CachedDetail?, nameFromCaller: String?): CachedDetail {
        val name = nameFromCaller ?: old?.name ?: throw notFound()
        val places = try {
            metrics.placesDetailsCalls.incrementAndGet()
            ai.places.details(placeId, withPhotoCredit = false)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            log.warn("Place lookup failed: {}", e.javaClass.simpleName)
            null
        }
        if (places == null && old == null) throw notFound()
        val address = places?.address ?: old?.address
        val text = try {
            metrics.placeDetailLlmCalls.incrementAndGet()
            ai.llm.describe(name, address)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            log.warn("Place text failed: {}", e.javaClass.simpleName)
            return old ?: CachedDetail(name = name, address = address, lat = places?.lat, lng = places?.lng)
        }
        val fresh = CachedDetail(
            name = name,
            address = address,
            lat = places?.lat ?: old?.lat,
            lng = places?.lng ?: old?.lng,
            about = text.about,
            highlights = text.highlights,
            openingHours = text.openingHours,
            bestTime = text.bestTime,
            costUsd = text.costUsd,
        )
        transaction {
            PlaceDetailCache.upsert {
                it[PlaceDetailCache.placeId] = placeId
                it[lang] = LANG
                it[response] = fresh
                it[model] = ai.llm.modelName
                it[expiresAt] = Instant.now().plus(TTL)
            }
        }
        return fresh
    }

    private fun toDto(placeId: String, d: CachedDetail, currency: String?): PlaceDetailDto {
        val code = currency?.trim()?.uppercase()?.takeIf { it.length == 3 && it.all(Char::isLetter) }
        val minor = if (d.costUsd != null && code != null) fx.fromUsd(d.costUsd, code) else null
        return PlaceDetailDto(
            placeId = placeId,
            name = d.name,
            address = d.address,
            lat = d.lat,
            lng = d.lng,
            photoUrl = photoSigner.signedPath(placeId),
            about = d.about,
            highlights = d.highlights,
            openingHours = d.openingHours,
            bestTime = d.bestTime,
            estimatedCostMinor = minor,
            costCurrency = if (minor != null) code else null,
        )
    }

    private fun notFound() = ApiException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "Place not found")

    private companion object {
        const val LANG = "id"
        const val MAX_PLACE_ID = 256
        const val MAX_NAME = 200
        val TTL: Duration = Duration.ofDays(30)
    }
}
