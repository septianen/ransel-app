package app.ransel.server.ai

import app.ransel.server.db.ExploreSuggestionsCache
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.requireRole
import app.ransel.shared.dto.ExploreSuggestionsResponse
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.Role
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

/** Normalized so "Bali", " bali " and "BALI" share an entry. */
internal fun normalizeDestination(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Destination interests for the Explore form (docs/specs/AI-Recommendation-Spec.md, "Destination interests").
 * One short LLM call per destination + month + language, shared by every user for 30 days. Never touches the daily
 * search quota, and a failure is an empty list, not an error: the app just hides the "Khas ..." box.
 */
class ExploreSuggestionService(
    private val llm: LlmClient,
    private val fx: FxRates,
    private val metrics: AiMetrics,
    private val cacheTtl: kotlin.time.Duration = 30.days,
) {
    private val log = LoggerFactory.getLogger(ExploreSuggestionService::class.java)

    private class Guide(val interests: List<String>, val lowMaxUsd: Double, val midMaxUsd: Double)

    private data class TripFacts(
        val destination: String,
        val month: String,
        val days: Int,
        val members: Int,
        val budgetMinor: Long?,
        val currency: String,
    )

    suspend fun suggestions(userId: UUID, tripId: String?, destination: String?, month: String?, language: String): ExploreSuggestionsResponse {
        if (tripId != null) {
            val id = runCatching { UUID.fromString(tripId) }.getOrNull() ?: throw validationFailed(mapOf("tripId" to "INVALID_UUID"))
            requireRole(id, userId, Role.VIEWER)
            // A destination next to the trip replaces the trip's for the chips; month and budget stay the trip's.
            val override = destination?.trim()?.takeIf { it.isNotEmpty() }
            if (override != null && override.length > MAX_DESTINATION) throw validationFailed(mapOf("destination" to "TOO_LONG"))
            val trip = loadTrip(id).let { if (override != null) it.copy(destination = override) else it }
            val guide = guide(trip.destination, trip.month, language) ?: return ExploreSuggestionsResponse()
            return withBudget(trip, guide)
        }
        val place = destination?.trim().orEmpty()
        if (place.isEmpty() || place.length > MAX_DESTINATION) throw validationFailed(mapOf("destination" to "REQUIRED"))
        if (month == null || !MONTH.matches(month)) throw validationFailed(mapOf("month" to "INVALID"))
        val guide = guide(place, month, language) ?: return ExploreSuggestionsResponse()
        return ExploreSuggestionsResponse(interests = guide.interests)
    }

    /** The price edges for a destination and month, from the cache or one short LLM call; null when unavailable. */
    suspend fun priceGuide(destination: String, month: String, language: String = "id"): PriceGuide? =
        guide(destination, month, language)?.let { PriceGuide(it.lowMaxUsd, it.midMaxUsd) }

    /** Fills the cache after a trip is created or moved. Failures are ignored; this is only a head start. */
    suspend fun warm(destination: String, month: String) {
        if (destination.isBlank() || destination.length > MAX_DESTINATION) return
        guide(destination, month, "id")
    }

    private fun withBudget(trip: TripFacts, guide: Guide): ExploreSuggestionsResponse {
        val budget = trip.budgetMinor
        if (budget == null || trip.days < 1 || trip.members < 1) return ExploreSuggestionsResponse(interests = guide.interests)
        val perPersonPerDayMinor = (budget.toDouble() / trip.days / trip.members).roundToLong()
        val usd = fx.toUsd(perPersonPerDayMinor, trip.currency)
        val level = usd?.let {
            when {
                it < guide.lowMaxUsd -> PriceLevel.LOW
                it > guide.midMaxUsd -> PriceLevel.HIGH
                else -> PriceLevel.MEDIUM
            }
        }
        return ExploreSuggestionsResponse(
            interests = guide.interests,
            suggestedPriceLevel = level,
            budgetPerPersonPerDayMinor = perPersonPerDayMinor,
            currency = trip.currency,
        )
    }

    private fun loadTrip(tripId: UUID): TripFacts = transaction {
        val row = Trips.selectAll().where { (Trips.id eq tripId) and Trips.deletedAt.isNull() }.single()
        val start = row[Trips.startDate].toKotlinLocalDate()
        TripFacts(
            destination = row[Trips.destination],
            month = "%04d-%02d".format(start.year, start.monthNumber),
            days = start.daysUntil(row[Trips.endDate].toKotlinLocalDate()) + 1,
            members = TripMembers.selectAll().where { TripMembers.tripId eq tripId }.count().toInt(),
            budgetMinor = row[Trips.budgetMinor],
            currency = row[Trips.baseCurrency],
        )
    }

    /** Cache first, then one LLM call (retried once). Null = nothing to show; failures are not cached. */
    private suspend fun guide(destination: String, month: String, language: String): Guide? {
        val key = normalizeDestination(destination)
        val lang = if (language == "en") "en" else "id"
        readCache(key, month, lang)?.let {
            metrics.suggestionCacheHits.incrementAndGet()
            return it
        }
        repeat(2) { attempt ->
            try {
                metrics.suggestionCalls.incrementAndGet()
                val answer = llm.suggestInterests(destination.trim(), month, lang)
                val guide = Guide(answer.interests, answer.lowMaxUsd, answer.midMaxUsd)
                writeCache(key, month, lang, guide)
                return guide
            } catch (e: Exception) {
                metrics.suggestionFailures.incrementAndGet()
                log.warn("Interest suggestions attempt {} failed: {}", attempt + 1, e.javaClass.simpleName)
            }
        }
        return null
    }

    private fun readCache(destination: String, month: String, language: String): Guide? = transaction {
        ExploreSuggestionsCache.selectAll().where {
            (ExploreSuggestionsCache.destination eq destination) and (ExploreSuggestionsCache.month eq month) and
                (ExploreSuggestionsCache.language eq language) and (ExploreSuggestionsCache.expiresAt greater Instant.now())
        }.singleOrNull()?.let {
            Guide(it[ExploreSuggestionsCache.interests], it[ExploreSuggestionsCache.priceLowMaxUsd].toDouble(), it[ExploreSuggestionsCache.priceMidMaxUsd].toDouble())
        }
    }

    private fun writeCache(destination: String, month: String, language: String, guide: Guide) {
        transaction {
            ExploreSuggestionsCache.upsert {
                it[ExploreSuggestionsCache.destination] = destination.take(MAX_DESTINATION)
                it[ExploreSuggestionsCache.month] = month
                it[ExploreSuggestionsCache.language] = language
                it[interests] = guide.interests
                it[priceLowMaxUsd] = BigDecimal.valueOf(guide.lowMaxUsd).setScale(2, java.math.RoundingMode.HALF_UP)
                it[priceMidMaxUsd] = BigDecimal.valueOf(guide.midMaxUsd).setScale(2, java.math.RoundingMode.HALF_UP)
                it[model] = llm.modelName
                it[expiresAt] = Instant.now().plus(cacheTtl.toJavaDuration())
            }
        }
    }

    /** Called by the hourly job next to the recommendation cache cleanup. */
    fun purgeExpired(): Int = transaction {
        ExploreSuggestionsCache.deleteWhere { ExploreSuggestionsCache.expiresAt less Instant.now() }
    }

    private companion object {
        const val MAX_DESTINATION = 200
        val MONTH = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")
    }
}
