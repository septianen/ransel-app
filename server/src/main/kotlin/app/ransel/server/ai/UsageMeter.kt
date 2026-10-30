package app.ransel.server.ai

import app.ransel.server.AppConfig
import app.ransel.server.db.ProviderUsage
import app.ransel.shared.dto.NearbyAreaDto
import app.ransel.shared.dto.PlaceDto
import app.ransel.shared.dto.PlaceSuggestionDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

/** The service a call is billed under on Karung's cost card. */
enum class ProviderService { LLM, PLACES }

/** One kind of paid call to an outside service. */
enum class ProviderCall(val service: ProviderService) {
    LLM(ProviderService.LLM),
    PLACES_FIND(ProviderService.PLACES),
    PLACES_DETAILS(ProviderService.PLACES),
    PLACES_PHOTO(ProviderService.PLACES),
    PLACES_AUTOCOMPLETE(ProviderService.PLACES),
    PLACES_GEOCODE(ProviderService.PLACES),
    ;

    fun price(prices: AppConfig.ProviderPrices): Long = when (this) {
        LLM -> prices.llmCall
        PLACES_FIND -> prices.placesFind
        PLACES_DETAILS -> prices.placesDetails
        PLACES_PHOTO -> prices.placesPhoto
        PLACES_AUTOCOMPLETE -> prices.placesAutocomplete
        PLACES_GEOCODE -> prices.placesGeocode
    }
}

/**
 * The calls made while one search runs (`withContext(SearchUsage())`): the metered clients add to it as well as to
 * the daily totals, so the search's row in `ai_search_log` carries its own estimated cost.
 */
class SearchUsage : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SearchUsage>

    private val counts = ConcurrentHashMap<ProviderCall, AtomicLong>()

    fun add(call: ProviderCall) {
        counts.computeIfAbsent(call) { AtomicLong() }.incrementAndGet()
    }

    fun costMicroUsd(service: ProviderService, prices: AppConfig.ProviderPrices): Long =
        counts.entries.filter { it.key.service == service }.sumOf { it.value.get() * it.key.price(prices) }
}

/**
 * Counts every paid call per WIB day in memory and adds the counts to `provider_usage` on [flush] (every minute, and
 * on shutdown). A crash loses at most a minute of counts: these are estimates for Karung, not billing.
 */
class UsageMeter(private val prices: AppConfig.ProviderPrices) {
    private val pending = ConcurrentHashMap<Pair<LocalDate, ProviderCall>, AtomicLong>()
    private val wib = TimeZone.of("Asia/Jakarta")

    suspend fun record(call: ProviderCall) {
        val day = Clock.System.now().toLocalDateTime(wib).date
        pending.computeIfAbsent(day to call) { AtomicLong() }.incrementAndGet()
        coroutineContext[SearchUsage]?.add(call)
    }

    /** What [pending] holds for [month] (not in `provider_usage` yet), in micro-USD. */
    fun pendingMicroUsd(month: kotlinx.datetime.Month, year: Int): Long =
        pending.entries.filter { it.key.first.month == month && it.key.first.year == year }.sumOf { it.value.get() * it.key.second.price(prices) }

    fun flush() {
        val batch = pending.keys.toList().mapNotNull { key -> pending.remove(key)?.get()?.takeIf { it > 0 }?.let { key to it } }
        if (batch.isEmpty()) return
        try {
            transaction {
                batch.forEach { (key, calls) ->
                    val (day, call) = key
                    val cost = calls * call.price(prices)
                    ProviderUsage.upsert(
                        onUpdate = {
                            it[ProviderUsage.calls] = ProviderUsage.calls + calls
                            it[ProviderUsage.costMicroUsd] = ProviderUsage.costMicroUsd + cost
                        },
                    ) {
                        it[ProviderUsage.day] = day.toJavaLocalDate()
                        it[ProviderUsage.call] = call.name
                        it[service] = call.service.name
                        it[ProviderUsage.calls] = calls
                        it[costMicroUsd] = cost
                    }
                }
            }
        } catch (e: Exception) {
            // put them back, so a database hiccup only delays them
            batch.forEach { (key, calls) -> pending.computeIfAbsent(key) { AtomicLong() }.addAndGet(calls) }
            throw e
        }
    }

    companion object {
        private const val RETENTION_DAYS = 400L

        /** Called from the hourly job: a year of days, so last year's same month can still be compared. */
        fun purge(): Int = transaction {
            ProviderUsage.deleteWhere { day less java.time.LocalDate.now().minusDays(RETENTION_DAYS) }
        }
    }
}

/**
 * The same services, with every outside call counted by [meter]. [budget] runs before each LLM call: with Karung's
 * CACHE_ONLY it stops new LLM calls once the month's budget is spent (503 AI_PAUSED, nothing charged).
 */
fun AiServices.metered(meter: UsageMeter, budget: () -> Unit = {}): AiServices =
    AiServices(MeteredLlm(llm, meter, budget), MeteredPlaces(places, meter), placeSearch?.let { MeteredPlaceSearch(it, meter) })

private class MeteredLlm(private val inner: LlmClient, private val meter: UsageMeter, private val budget: () -> Unit) : LlmClient {
    override val modelName: String get() = inner.modelName

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> {
        budget()
        meter.record(ProviderCall.LLM)
        return inner.suggest(query)
    }

    override suspend fun suggestInterests(destination: String, month: String, language: String): InterestSuggestions {
        budget()
        meter.record(ProviderCall.LLM)
        return inner.suggestInterests(destination, month, language)
    }

    override suspend fun describe(name: String, address: String?): LlmPlaceText {
        budget()
        meter.record(ProviderCall.LLM)
        return inner.describe(name, address)
    }

    override suspend fun readReceipt(image: ByteArray, mimeType: String): LlmReceipt {
        budget()
        meter.record(ProviderCall.LLM)
        return inner.readReceipt(image, mimeType)
    }
}

private class MeteredPlaces(private val inner: PlacesClient, private val meter: UsageMeter) : PlacesClient {
    override suspend fun findPlaceId(name: String, destination: String): String? {
        meter.record(ProviderCall.PLACES_FIND)
        return inner.findPlaceId(name, destination)
    }

    override suspend fun findPlaceIdNear(name: String, destination: String, center: Pair<Double, Double>?): String? {
        meter.record(ProviderCall.PLACES_FIND)
        return inner.findPlaceIdNear(name, destination, center)
    }

    override suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails? {
        meter.record(ProviderCall.PLACES_DETAILS)
        return inner.details(placeId, withPhotoCredit)
    }

    override suspend fun photo(placeId: String): PlacePhoto? {
        meter.record(ProviderCall.PLACES_PHOTO)
        return inner.photo(placeId)
    }
}

private class MeteredPlaceSearch(private val inner: PlaceSearch, private val meter: UsageMeter) : PlaceSearch {
    override suspend fun autocomplete(input: String, sessionToken: String?, near: Pair<Double, Double>?): List<PlaceSuggestionDto> {
        meter.record(ProviderCall.PLACES_AUTOCOMPLETE)
        return inner.autocomplete(input, sessionToken, near)
    }

    override suspend fun details(placeId: String, sessionToken: String?): PlaceDto? {
        meter.record(ProviderCall.PLACES_DETAILS)
        return inner.details(placeId, sessionToken)
    }

    override suspend fun reverse(lat: Double, lng: Double): List<NearbyAreaDto> {
        meter.record(ProviderCall.PLACES_GEOCODE)
        return inner.reverse(lat, lng)
    }
}
