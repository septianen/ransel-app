package app.ransel.server.ai

import app.ransel.server.service.PollService
import app.ransel.shared.dto.PollSearchRequest
import app.ransel.shared.dto.PollSearchResponse
import app.ransel.shared.dto.PollSearchResult
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.TravelStyle
import app.ransel.shared.model.PollPriceUnit
import app.ransel.server.AppConfig
import app.ransel.server.db.RecommendationCache
import app.ransel.server.db.RecommendationFeedback
import app.ransel.server.db.Trips
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.requireValid
import app.ransel.server.service.CreditService
import app.ransel.server.service.RoundService
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.requireRole
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.FeedbackRequest
import app.ransel.shared.dto.RecommendationGroup
import app.ransel.shared.dto.RecommendationItem
import app.ransel.shared.dto.RecommendationRequest
import app.ransel.shared.dto.RecommendationResponse
import app.ransel.shared.model.Category
import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.FeedbackAction
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
    /** Source of the local price guide; null = no price filter and no numbers in the prompt. */
    private val suggestions: ExploreSuggestionService? = null,
    /** Converts the LLM's USD cost estimate to the trip currency for the cards. */
    private val fx: FxRates = NoFxRates,
    /** Keeps each place's detail text for the itinerary; null = not kept (tests that do not care). */
    private val details: PlaceDetailService? = null,
    private val credits: CreditService,
    /** Voting rounds for trips with 2+ members; null = every trip behaves like a solo trip (tests that do not care). */
    private val rounds: RoundService? = null,
) {
    private val log = LoggerFactory.getLogger(RecommendationService::class.java)

    suspend fun recommend(userId: UUID, body: RecommendationRequest): RecommendationResponse {
        val tripId = runCatching { UUID.fromString(body.tripId) }.getOrNull()
            ?: throw validationFailed(mapOf("tripId" to "INVALID_UUID"))
        validate(body)
        // Liked places become itinerary items, so only people who can edit the trip may search.
        requireRole(tripId, userId, Role.EDITOR)
        // A destination in the request replaces the trip's for this search only (a Yogyakarta trip exploring Solo).
        // another destination for this search loses the trip's map point; the same name keeps it
        val trip = loadTrip(tripId).let { t ->
            body.destination?.trim()?.takeIf { it.isNotEmpty() && !it.equals(t.destination.trim(), ignoreCase = true) }
                ?.let { t.copy(destination = it, center = null) } ?: t
        }
        if (todayWib() > trip.endDate) throw validationFailed(mapOf("tripId" to "TRIP_ENDED"))

        // A trip with 2+ members turns the search into a voting round (docs/specs/voting-spec.md).
        val requestedRound = body.roundId?.let { runCatching { UUID.fromString(it) }.getOrNull() ?: throw validationFailed(mapOf("roundId" to "INVALID_UUID")) }
        val voting = rounds != null && rounds.memberCount(tripId) >= 2
        if (voting) {
            if (body.newSearch) rounds!!.requireRoomForNewRound(tripId)
            else if (requestedRound != null) rounds!!.requireAppendable(tripId, requestedRound, userId)
        }

        // Pre-check, so a user without credits costs nothing. The charge itself happens only after a successful search.
        val action = if (body.newSearch) CreditAction.SEARCH else CreditAction.NEXT_BATCH
        try {
            credits.requireAffordable(userId, action)
        } catch (e: ApiException) {
            if (e.code == ErrorCode.INSUFFICIENT_CREDITS) metrics.quotaRejections.incrementAndGet()
            throw e
        }

        val feedback = transaction {
            RecommendationFeedback.selectAll()
                .where { (RecommendationFeedback.tripId eq tripId) and (RecommendationFeedback.userId eq userId) }
                .orderBy(RecommendationFeedback.createdAt to SortOrder.DESC)
                .toList()
        }
        val seen = feedback.map { it[RecommendationFeedback.placeId] }.toSet() + body.excludePlaceIds +
            (rounds?.placeIdsInRounds(tripId) ?: emptySet())

        metrics.searches.incrementAndGet()
        val key = cacheKey(body, trip)
        val guide = suggestions?.priceGuide(trip.destination, monthOf(trip.startDate))
        val cached = readCache(key)
        val candidates: List<CachedPlace> = if (cached != null) {
            metrics.cacheHits.incrementAndGet()
            cached
        } else {
            val query = LlmQuery(
                center = trip.center,
                destination = trip.destination.trim(),
                priceGuide = guide,
                days = trip.startDate.daysUntil(trip.endDate) + 1,
                startDate = trip.startDate.toString(),
                priceLevel = body.priceLevel,
                interests = body.interests.distinct(),
                extraInterests = body.extraInterests.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                style = body.style,
                notes = body.notes?.trim()?.takeIf { it.isNotEmpty() },
                liked = feedback.filter { it[RecommendationFeedback.action] == FeedbackAction.LIKE }.take(20).map { it[RecommendationFeedback.placeName] },
                skipped = feedback.filter { it[RecommendationFeedback.action] == FeedbackAction.SKIP }.take(20).map { it[RecommendationFeedback.placeName] },
                count = CANDIDATES_ASKED,
            )
            generate(query).also {
                if (it.isNotEmpty()) {
                    writeCache(key, query.destination, it)
                    runCatching { details?.remember(it) }.onFailure { e -> log.warn("Keeping place detail failed: {}", e.javaClass.simpleName) }
                }
            }
        }

        // The price filter runs here, not before the cache write, so a cached entry stays valid for every price level.
        val picked = candidates.filter { it.placeId !in seen }
            .filter { place ->
                priceFits(place.costUsd, guide, body.priceLevel).also { if (!it) metrics.priceFiltered.incrementAndGet() }
            }
            .take(MAX_CARDS)
        val items = withPhotoCredits(picked, trip.baseCurrency)

        val state = try {
            credits.charge(userId, action, tripId.toString())
        } catch (e: ApiException) {
            if (e.code == ErrorCode.INSUFFICIENT_CREDITS) metrics.quotaRejections.incrementAndGet()
            throw e
        }
        val roundId = when {
            !voting || rounds == null -> null
            body.newSearch -> if (items.isEmpty()) null else rounds.create(tripId, userId, items)
            requestedRound != null -> requestedRound.also { rounds.append(it, items) }
            else -> null // an older app's next batch without a round: it saves directly, like a solo trip
        }
        log.info(
            "ai.search cache={} candidates={} returned={} action={} balance={} freeLeft={} round={}",
            if (cached != null) "hit" else "miss", candidates.size, items.size, action, state.balance, state.dailyFreeLeft, roundId != null,
        )
        return RecommendationResponse(groups = group(items), credits = state, roundId = roundId?.toString())
    }

    /**
     * Jelajah for a poll (PRD "Adding options"): the same pipeline as Explore, narrowed to lodging or food, with an
     * optional area, budget and wishes. Nothing is cached or put in a round; the caller picks what becomes an option.
     */
    suspend fun searchForPoll(userId: UUID, pollId: UUID, body: PollSearchRequest, polls: PollService.SearchContext = PollService.searchContext(userId, pollId)): PollSearchResponse {
        val area = body.area?.trim()?.takeIf { it.isNotEmpty() }
        val needs = body.needs?.trim()?.takeIf { it.isNotEmpty() }
        val lo = body.minMinor
        val hi = body.maxMinor
        requireValid(buildMap {
            if ((area?.length ?: 0) > 200) put("area", "TOO_LONG")
            if ((needs?.length ?: 0) > 200) put("needs", "TOO_LONG")
            if (lo != null && lo < 0) put("minMinor", "NEGATIVE")
            if (hi != null && hi < 0) put("maxMinor", "NEGATIVE")
            if (lo != null && hi != null && lo > hi) put("minMinor", "ABOVE_MAX")
        })
        try {
            credits.requireAffordable(userId, CreditAction.SEARCH)
        } catch (e: ApiException) {
            if (e.code == ErrorCode.INSUFFICIENT_CREDITS) metrics.quotaRejections.incrementAndGet()
            throw e
        }
        val stay = polls.kind == PollKind.STAY
        val unit = if (stay) PollPriceUnit.NIGHT else PollPriceUnit.PERSON
        val query = LlmQuery(
            destination = area ?: polls.destination.trim(),
            priceGuide = null,
            days = polls.startDate.daysUntil(polls.endDate) + 1,
            startDate = polls.startDate.toString(),
            priceLevel = PriceLevel.MEDIUM,
            interests = emptyList(),
            extraInterests = emptyList(),
            style = TravelStyle.entries.first(),
            notes = needs,
            liked = emptyList(),
            skipped = emptyList(),
            count = CANDIDATES_ASKED,
            onlyCategory = if (stay) Category.LODGING else Category.FOOD,
            budget = budgetText(body.minMinor, body.maxMinor, polls.currency, if (stay) "night" else "person"),
        )
        metrics.searches.incrementAndGet()
        val found = generate(query).filter { it.placeId !in polls.placeIds }
        val wantedMin = body.minMinor?.let { (it * 0.7).toLong() }
        val wantedMax = body.maxMinor?.let { (it * 1.3).toLong() }
        val priced = found.map { it to it.costUsd?.let { usd -> fx.fromUsd(usd, polls.currency) } }
        // The estimate is rough: keep what is within 30% of the range, and show everything when nothing fits rather than nothing.
        val fitting = priced.filter { (_, minor) -> minor == null || ((wantedMin == null || minor >= wantedMin) && (wantedMax == null || minor <= wantedMax)) }
        val results = (fitting.ifEmpty { priced }).take(MAX_CARDS).map { (place, minor) ->
            PollSearchResult(
                placeId = place.placeId, name = place.name, reason = place.reason, address = place.address,
                priceMinor = minor, priceUnit = if (minor != null) unit else null,
            )
        }
        val state = try {
            credits.charge(userId, CreditAction.SEARCH, pollId.toString())
        } catch (e: ApiException) {
            if (e.code == ErrorCode.INSUFFICIENT_CREDITS) metrics.quotaRejections.incrementAndGet()
            throw e
        }
        log.info("ai.pollsearch kind={} found={} returned={}", polls.kind, found.size, results.size)
        return PollSearchResponse(results, state)
    }

    private fun budgetText(min: Long?, max: Long?, currency: String, per: String): String? = when {
        min != null && max != null -> "about $min to $max minor units of $currency per $per (amounts in the smallest unit of the currency)"
        max != null -> "up to about $max minor units of $currency per $per (amounts in the smallest unit of the currency)"
        min != null -> "from about $min minor units of $currency per $per (amounts in the smallest unit of the currency)"
        else -> null
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
                            val id = ai.places.findPlaceIdNear(candidate.name, query.destination, query.center) ?: return@withPermit null
                            metrics.placesDetailsCalls.incrementAndGet()
                            val details = ai.places.details(id, withPhotoCredit = false) ?: return@withPermit null
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
                costUsd = c.costUsd?.takeIf { it > 0 },
                suggestedDurationMin = (c.suggestedDurationMin ?: DEFAULT_DURATION_MIN).coerceIn(15, 480),
                lat = d.lat,
                lng = d.lng,
                address = d.address,
                about = c.about?.trim()?.take(LlmOutput.MAX_ABOUT)?.ifEmpty { null },
                highlights = c.highlights.map { it.trim().take(LlmOutput.MAX_HIGHLIGHT) }.filter { it.isNotEmpty() }.take(3),
                openingHours = c.openingHours.filter { it.label.isNotBlank() && it.hours.isNotBlank() }.take(3),
                bestTime = c.bestTime?.trim()?.take(LlmOutput.MAX_HIGHLIGHT)?.ifEmpty { null },
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
                log.warn("LLM attempt {} failed: {}: {}", it + 1, e.javaClass.simpleName, e.message?.take(200))
            }
        }
        log.error("LLM failed twice", last)
        throw unavailable()
    }

    /** Photo credit for the top cards only: Google requires it next to the photo. Ratings are not fetched (Enterprise tier). */
    private suspend fun withPhotoCredits(places: List<CachedPlace>, currency: String): List<Pair<Category, RecommendationItem>> = coroutineScope {
        places.mapIndexed { index, place ->
            async {
                var address = place.address
                var attribution: String? = null
                if (index < CREDITED_CARDS) {
                    try {
                        metrics.placesDetailsCalls.incrementAndGet()
                        ai.places.details(place.placeId, withPhotoCredit = true)?.let {
                            attribution = it.photoAttribution
                            address = it.address ?: address
                        }
                    } catch (e: Exception) {
                        log.warn("Photo credit lookup failed: {}", e.javaClass.simpleName) // a card without a credit is fine
                    }
                }
                val costMinor = place.costUsd?.let { fx.fromUsd(it, currency) }
                place.category to RecommendationItem(
                    placeId = place.placeId,
                    name = place.name,
                    reason = place.reason,
                    priceLevel = place.priceLevel,
                    suggestedDurationMin = place.suggestedDurationMin,
                    lat = place.lat,
                    lng = place.lng,
                    photoUrl = photoSigner.signedPath(place.placeId),
                    photoAttribution = attribution,
                    address = address,
                    about = place.about,
                    highlights = place.highlights,
                    openingHours = place.openingHours,
                    bestTime = place.bestTime,
                    estimatedCostMinor = costMinor,
                    costCurrency = if (costMinor != null) currency else null,
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

    // ── time ────────────────────────────────────────────────────────────────

    private val wib = TimeZone.of("Asia/Jakarta")

    private fun todayWib(): LocalDate = Clock.System.now().toLocalDateTime(wib).date

    // ── cache ───────────────────────────────────────────────────────────────

    /** Normalized so "Bali", " bali " and "BALI" share an entry. Notes are part of the key: they change the answer. */
    internal fun cacheKey(body: RecommendationRequest, trip: TripFacts): String {
        val text = listOf(
            normalizeDestination(trip.destination),
            monthOf(trip.startDate),
            body.interests.map { it.name }.distinct().sorted().joinToString(","),
            body.extraInterests.map(::normalizeDestination).filter { it.isNotEmpty() }.distinct().sorted().joinToString(","),
            body.priceLevel.name,
            body.style.name,
            normalizeDestination(body.notes.orEmpty()),
            "detail-v1", // entries cached before the detail text existed do not carry it: a new key refills them
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    internal data class TripFacts(
        val destination: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val baseCurrency: String = "IDR",
        val center: Pair<Double, Double>? = null,
    )

    private fun loadTrip(tripId: UUID): TripFacts = transaction {
        val row = Trips.selectAll().where { (Trips.id eq tripId) and Trips.deletedAt.isNull() }.single()
        val lat = row[Trips.destLat]
        val lng = row[Trips.destLng]
        TripFacts(
            row[Trips.destination], row[Trips.startDate].toKotlinLocalDate(), row[Trips.endDate].toKotlinLocalDate(), row[Trips.baseCurrency].trim(),
            center = if (lat != null && lng != null) lat to lng else null,
        )
    }

    /**
     * The LLM's [costUsd] per person against the destination's [guide]: below the lower edge is LOW, above the upper
     * edge HIGH, else MEDIUM. A place is dropped when it is more than one step from [wanted], so MEDIUM keeps
     * everything. Free places (no cost) and an unknown guide keep the place.
     */
    internal fun priceFits(costUsd: Double?, guide: PriceGuide?, wanted: PriceLevel): Boolean {
        if (costUsd == null || costUsd <= 0 || guide == null) return true
        val actual = when {
            costUsd < guide.lowMaxUsd -> PriceLevel.LOW
            costUsd > guide.midMaxUsd -> PriceLevel.HIGH
            else -> PriceLevel.MEDIUM
        }
        return kotlin.math.abs(actual.ordinal - wanted.ordinal) <= 1
    }

    private fun monthOf(date: LocalDate) = "%04d-%02d".format(date.year, date.monthNumber)

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
            if (body.interests.isEmpty() && body.extraInterests.none { it.isNotBlank() }) put("interests", "EMPTY")
            if (body.extraInterests.size > MAX_EXTRA_INTERESTS) put("extraInterests", "TOO_MANY")
            else if (body.extraInterests.any { it.length > MAX_EXTRA_LENGTH }) put("extraInterests", "TOO_LONG")
            if (body.destination != null && body.destination!!.length > MAX_DESTINATION) put("destination", "TOO_LONG")
            if (body.notes != null && body.notes!!.length > 500) put("notes", "TOO_LONG")
            if (body.excludePlaceIds.size > 500) put("excludePlaceIds", "TOO_MANY")
        },
    )

    private fun unavailable(): ApiException {
        metrics.unavailable.incrementAndGet()
        return ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.AI_UNAVAILABLE, "Recommendations are unavailable right now")
    }

    private companion object {
        const val CANDIDATES_ASKED = 16
        const val MAX_CANDIDATES = 20
        const val MAX_CARDS = 15
        const val CREDITED_CARDS = 3
        const val GROUNDING_PARALLELISM = 5
        const val DEFAULT_DURATION_MIN = 60
        const val MAX_SPREAD_KM = 150.0
        const val MAX_EXTRA_INTERESTS = 5
        const val MAX_EXTRA_LENGTH = 40
        const val MAX_DESTINATION = 200
    }
}
