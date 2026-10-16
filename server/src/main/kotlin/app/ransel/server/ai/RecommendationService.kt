package app.ransel.server.ai

import app.ransel.server.AppConfig
import app.ransel.server.db.AiUsage
import app.ransel.server.db.RecommendationCache
import app.ransel.server.db.RecommendationFeedback
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.requireRole
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.AI_DAILY_QUOTA
import app.ransel.shared.dto.FeedbackRequest
import app.ransel.shared.dto.QuotaDto
import app.ransel.shared.dto.RecommendationGroup
import app.ransel.shared.dto.RecommendationItem
import app.ransel.shared.dto.RecommendationRequest
import app.ransel.shared.dto.RecommendationResponse
import app.ransel.shared.model.Category
import app.ransel.shared.model.FeedbackAction
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinInstant

/**
 * Explore AI (TRD "AI feature"): quota -> cache -> LLM -> Places grounding -> exclude seen -> top cards get ratings.
 * The LLM only proposes names; whatever Places cannot find never reaches the user.
 */
class RecommendationService(
    private val config: AppConfig,
    private val ai: AiServices,
    val metrics: AiMetrics = AiMetrics(),
    private val photoSigner: PhotoSigner = PhotoSigner(config.photoSigningSecret),
) {
    private val log = LoggerFactory.getLogger(RecommendationService::class.java)

    suspend fun recommend(userId: UUID, body: RecommendationRequest): RecommendationResponse {
        val tripId = runCatching { UUID.fromString(body.tripId) }.getOrNull()
            ?: throw validationFailed(mapOf("tripId" to "INVALID_UUID"))
        validate(body)
        // Liked places become itinerary items, so only people who can edit the trip may search.
        requireRole(tripId, userId, Role.EDITOR)

        // Pre-check, so an exhausted user costs nothing. The counter itself moves only after a successful search.
        if (body.newSearch) failIfExhausted(currentUsage(userId))

        val feedback = transaction {
            RecommendationFeedback.selectAll()
                .where { (RecommendationFeedback.tripId eq tripId) and (RecommendationFeedback.userId eq userId) }
                .orderBy(RecommendationFeedback.createdAt to SortOrder.DESC)
                .toList()
        }
        val seen = feedback.map { it[RecommendationFeedback.placeId] }.toSet() + body.excludePlaceIds

        metrics.searches.incrementAndGet()
        val key = cacheKey(body)
        val cached = readCache(key)
        val candidates: List<CachedPlace> = if (cached != null) {
            metrics.cacheHits.incrementAndGet()
            cached
        } else {
            val query = LlmQuery(
                destination = body.destination.trim(),
                days = body.startDate.daysUntil(body.endDate) + 1,
                budget = body.budget,
                interests = body.interests.distinct(),
                style = body.style,
                notes = body.notes?.trim()?.takeIf { it.isNotEmpty() },
                liked = feedback.filter { it[RecommendationFeedback.action] == FeedbackAction.LIKE }.take(20).map { it[RecommendationFeedback.placeName] },
                skipped = feedback.filter { it[RecommendationFeedback.action] == FeedbackAction.SKIP }.take(20).map { it[RecommendationFeedback.placeName] },
                count = CANDIDATES_ASKED,
            )
            generate(query).also { if (it.isNotEmpty()) writeCache(key, query.destination, it) }
        }

        val picked = candidates.filter { it.placeId !in seen }.take(MAX_CARDS)
        val items = withRatings(picked)

        val quota = if (body.newSearch) consume(userId) else currentUsage(userId).let(::toQuota)
        log.info(
            "ai.search cache={} candidates={} returned={} quotaUsed={}/{}",
            if (cached != null) "hit" else "miss", candidates.size, items.size, quota.used, quota.limit,
        )
        return RecommendationResponse(groups = group(items), quota = quota)
    }

    fun saveFeedback(userId: UUID, body: FeedbackRequest) {
        val tripId = runCatching { UUID.fromString(body.tripId) }.getOrNull()
            ?: throw validationFailed(mapOf("tripId" to "INVALID_UUID"))
        requireValid(
            buildMap {
                if (body.placeId.isBlank() || body.placeId.length > 256) put("placeId", "INVALID")
                if (body.placeName.isBlank()) put("placeName", "REQUIRED")
            },
        )
        requireRole(tripId, userId, Role.EDITOR)
        transaction {
            RecommendationFeedback.upsert {
                it[RecommendationFeedback.tripId] = tripId
                it[RecommendationFeedback.userId] = userId
                it[placeId] = body.placeId
                it[placeName] = body.placeName.trim().take(200)
                it[category] = body.category
                it[action] = body.action
            }
        }
    }

    /** Undo of the last swipe. Idempotent: deleting something that is not there is fine. */
    fun undoFeedback(userId: UUID, tripId: UUID, placeId: String) {
        requireRole(tripId, userId, Role.EDITOR)
        transaction {
            RecommendationFeedback.deleteWhere {
                (RecommendationFeedback.tripId eq tripId) and (RecommendationFeedback.userId eq userId) and
                    (RecommendationFeedback.placeId eq placeId)
            }
        }
    }

    /** Called from a scheduled job. */
    fun purgeExpiredCache(): Int = transaction {
        RecommendationCache.deleteWhere { RecommendationCache.expiresAt less Instant.now() }
    }

    // ── pipeline ────────────────────────────────────────────────────────────

    /** LLM (one retry) then Places validation. Returns only places that exist. */
    private suspend fun generate(query: LlmQuery): List<CachedPlace> {
        val suggestions = askLlm(query)
        if (suggestions.isEmpty()) return emptyList()

        val gate = Semaphore(GROUNDING_PARALLELISM)
        var failures = 0
        val grounded = coroutineScope {
            suggestions.take(MAX_CANDIDATES).map { candidate ->
                async {
                    gate.withPermit {
                        try {
                            metrics.placesSearchCalls.incrementAndGet()
                            val id = ai.places.findPlaceId(candidate.name, query.destination) ?: return@withPermit null
                            metrics.placesDetailsCalls.incrementAndGet()
                            val details = ai.places.details(id, withRating = false) ?: return@withPermit null
                            candidate to details
                        } catch (e: Exception) {
                            synchronized(this@RecommendationService) { failures++ }
                            log.warn("Places lookup failed: {}", e.javaClass.simpleName)
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        // Every lookup erroring means Places is down: say so instead of claiming "nothing matches".
        if (grounded.isEmpty() && failures > 0 && failures >= suggestions.take(MAX_CANDIDATES).size) throw unavailable()

        val unique = grounded.distinctBy { it.second.placeId }
        return dropFarOutliers(unique).map { (c, d) ->
            CachedPlace(
                placeId = d.placeId,
                name = c.name.trim(),
                category = c.category,
                reason = c.reason.trim(),
                priceLevel = c.priceLevel?.takeIf { it in 0..4 },
                suggestedDurationMin = (c.suggestedDurationMin ?: DEFAULT_DURATION_MIN).coerceIn(15, 480),
                lat = d.lat,
                lng = d.lng,
                address = d.address,
            )
        }
    }

    private suspend fun askLlm(query: LlmQuery): List<LlmCandidate> {
        var last: Exception? = null
        repeat(2) {
            try {
                metrics.llmCalls.incrementAndGet()
                return ai.llm.suggest(query).filter { it.name.isNotBlank() }
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                metrics.llmFailures.incrementAndGet()
                last = e
                log.warn("LLM attempt {} failed: {}", it + 1, e.javaClass.simpleName)
            }
        }
        log.error("LLM failed twice", last)
        throw unavailable()
    }

    /** Fetches ratings for the top cards only (the billing tier rises with the rating field). */
    private suspend fun withRatings(places: List<CachedPlace>): List<Pair<Category, RecommendationItem>> = coroutineScope {
        places.mapIndexed { index, place ->
            async {
                var rating: Double? = null
                var address = place.address
                var attribution: String? = null
                if (index < RATED_CARDS) {
                    try {
                        metrics.placesDetailsCalls.incrementAndGet()
                        ai.places.details(place.placeId, withRating = true)?.let {
                            rating = it.rating
                            attribution = it.photoAttribution
                            address = it.address ?: address
                        }
                    } catch (e: Exception) {
                        log.warn("Rating lookup failed: {}", e.javaClass.simpleName) // a card without a rating is fine
                    }
                }
                place.category to RecommendationItem(
                    placeId = place.placeId,
                    name = place.name,
                    reason = place.reason,
                    priceLevel = place.priceLevel,
                    suggestedDurationMin = place.suggestedDurationMin,
                    lat = place.lat,
                    lng = place.lng,
                    rating = rating,
                    photoUrl = photoSigner.signedPath(place.placeId),
                    photoAttribution = attribution,
                    address = address,
                )
            }
        }.awaitAll()
    }

    /** A place > 150 km from where most of the results are is almost certainly a name collision elsewhere. */
    private fun dropFarOutliers(rows: List<Pair<LlmCandidate, PlaceDetails>>): List<Pair<LlmCandidate, PlaceDetails>> {
        if (rows.size < 4) return rows
        val medianLat = rows.map { it.second.lat }.sorted()[rows.size / 2]
        val medianLng = rows.map { it.second.lng }.sorted()[rows.size / 2]
        return rows.filter { distanceKm(medianLat, medianLng, it.second.lat, it.second.lng) <= MAX_SPREAD_KM }
    }

    private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** One group per category, in the order categories first appear (the order the LLM ranked them). */
    private fun group(items: List<Pair<Category, RecommendationItem>>): List<RecommendationGroup> =
        items.groupBy({ it.first }, { it.second }).map { (category, list) -> RecommendationGroup(category, list) }

    // ── quota ───────────────────────────────────────────────────────────────

    private val wib = TimeZone.of("Asia/Jakarta")

    private fun todayWib(): LocalDate = Clock.System.now().toLocalDateTime(wib).date

    private fun resetsAt() = todayWib().plus(1, DateTimeUnit.DAY).atStartOfDayIn(wib)

    private fun currentUsage(userId: UUID): Int = transaction {
        AiUsage.selectAll()
            .where { (AiUsage.userId eq userId) and (AiUsage.usageDate eq todayWib().toJavaLocalDate()) }
            .singleOrNull()?.get(AiUsage.count) ?: 0
    }

    private fun toQuota(used: Int) = QuotaDto(used = used, limit = AI_DAILY_QUOTA, resetsAt = resetsAt())

    private fun failIfExhausted(used: Int) {
        if (used >= AI_DAILY_QUOTA) {
            metrics.quotaRejections.incrementAndGet()
            throw ApiException(
                HttpStatusCode.TooManyRequests, ErrorCode.AI_QUOTA_EXCEEDED,
                "Daily Explore searches used up",
                mapOf("used" to used.toString(), "limit" to AI_DAILY_QUOTA.toString(), "resetsAt" to resetsAt().toString()),
            )
        }
    }

    /**
     * Atomic: the increment only happens while count < limit, so two parallel searches cannot overshoot.
     * First search of the day inserts the row; a lost insert race falls back to the conditional update.
     */
    private fun consume(userId: UUID): QuotaDto {
        val day = todayWib().toJavaLocalDate()
        val used = transaction {
            fun bump() = AiUsage.update({
                (AiUsage.userId eq userId) and (AiUsage.usageDate eq day) and (AiUsage.count less AI_DAILY_QUOTA)
            }) { it[count] = count + 1 }

            val ok = bump() > 0 ||
                AiUsage.insertIgnore { it[AiUsage.userId] = userId; it[usageDate] = day; it[count] = 1 }.insertedCount > 0 ||
                bump() > 0
            if (!ok) return@transaction null
            AiUsage.selectAll().where { (AiUsage.userId eq userId) and (AiUsage.usageDate eq day) }.single()[AiUsage.count]
        }
        if (used == null) failIfExhausted(AI_DAILY_QUOTA)
        return toQuota(used ?: AI_DAILY_QUOTA)
    }

    // ── cache ───────────────────────────────────────────────────────────────

    /** Normalized so "Bali", " bali " and "BALI" share an entry. Notes are part of the key: they change the answer. */
    internal fun cacheKey(body: RecommendationRequest): String {
        val text = listOf(
            body.destination.trim().lowercase().replace(Regex("\\s+"), " "),
            body.interests.map { it.name }.distinct().sorted().joinToString(","),
            body.budget.name,
            body.style.name,
            body.notes?.trim()?.lowercase()?.replace(Regex("\\s+"), " ").orEmpty(),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun readCache(key: String): List<CachedPlace>? = transaction {
        val row = RecommendationCache.selectAll()
            .where { (RecommendationCache.queryHash eq key) and (RecommendationCache.expiresAt greater Instant.now()) }
            .singleOrNull() ?: return@transaction null
        RecommendationCache.update({ RecommendationCache.queryHash eq key }) { it[hitCount] = row[RecommendationCache.hitCount] + 1 }
        row[RecommendationCache.response]
    }

    private fun writeCache(key: String, destination: String, places: List<CachedPlace>) {
        transaction {
            RecommendationCache.upsert {
                it[queryHash] = key
                it[RecommendationCache.destination] = destination.take(200)
                it[response] = places
                it[model] = ai.llm.modelName
                it[hitCount] = 0
                it[expiresAt] = Instant.now().plus(config.aiCacheTtl.toJavaDuration())
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun validate(body: RecommendationRequest) = requireValid(
        buildMap {
            if (body.destination.isBlank() || body.destination.length > 200) put("destination", "REQUIRED")
            if (body.endDate < body.startDate) put("endDate", "MUST_BE_AFTER_START")
            if (body.notes != null && body.notes!!.length > 500) put("notes", "TOO_LONG")
            if (body.excludePlaceIds.size > 500) put("excludePlaceIds", "TOO_MANY")
        },
    )

    private fun unavailable(): ApiException {
        metrics.unavailable.incrementAndGet()
        return ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.AI_UNAVAILABLE, "Recommendations are unavailable right now")
    }

    private companion object {
        const val CANDIDATES_ASKED = 18
        const val MAX_CANDIDATES = 20
        const val MAX_CARDS = 15
        const val RATED_CARDS = 3
        const val GROUNDING_PARALLELISM = 5
        const val DEFAULT_DURATION_MIN = 60
        const val MAX_SPREAD_KM = 150.0
    }
}
