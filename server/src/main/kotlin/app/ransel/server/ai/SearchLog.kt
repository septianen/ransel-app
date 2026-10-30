package app.ransel.server.ai

import app.ransel.server.AppConfig
import app.ransel.server.db.AiSearchLog
import app.ransel.server.plugins.ApiException
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.model.AiSearchKind
import app.ransel.shared.model.AiSearchSource
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/** What the search learned on the way; read by [SearchLog.around] when the search ends, however it ends. */
class SearchTrace(val kind: AiSearchKind, val userId: UUID, val newSearch: Boolean) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SearchTrace>

    @Volatile var tripId: UUID? = null
    @Volatile var destination: String? = null
    @Volatile var priceLevel: String? = null
    @Volatile var interests: List<String> = emptyList()
    @Volatile var cached = false
    @Volatile var placeIds: List<String> = emptyList()
    @Volatile var creditsCharged = 0
}

/** The search running in this coroutine, or null outside one (tests calling a service directly). */
suspend fun searchTrace(): SearchTrace? = coroutineContext[SearchTrace]

/**
 * Writes one `ai_search_log` row per search: its parameters (never the prompt), how it ended, the cards, how long it
 * took, the credits it cost and the estimated provider cost of the calls it made. Kept 180 days.
 */
class SearchLog(private val prices: AppConfig.ProviderPrices) {
    private val log = LoggerFactory.getLogger(SearchLog::class.java)

    /**
     * Runs [block] as one logged search. A search that never got going (bad input, no access, one already running)
     * leaves no row: only a result, a refusal for credits or a failure of the AI services counts.
     */
    suspend fun <T> around(trace: SearchTrace, block: suspend () -> T): T {
        val usage = SearchUsage()
        val started = System.nanoTime()
        fun elapsed() = ((System.nanoTime() - started) / 1_000_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val result = try {
            withContext(trace + usage) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            when (e.code) {
                ErrorCode.INSUFFICIENT_CREDITS -> write(trace, usage, AiSearchSource.NO_CREDITS, elapsed())
                ErrorCode.AI_UNAVAILABLE -> write(trace, usage, AiSearchSource.FAILED, elapsed())
            }
            throw e
        } catch (e: Exception) {
            write(trace, usage, AiSearchSource.FAILED, elapsed())
            throw e
        }
        write(trace, usage, if (trace.cached) AiSearchSource.CACHE else AiSearchSource.NEW, elapsed())
        return result
    }

    private fun write(trace: SearchTrace, usage: SearchUsage, source: AiSearchSource, latencyMs: Int) {
        val succeeded = source == AiSearchSource.NEW || source == AiSearchSource.CACHE
        runCatching {
            transaction {
                AiSearchLog.insert {
                    it[id] = UUID.randomUUID()
                    it[userId] = trace.userId
                    it[tripId] = trace.tripId
                    it[kind] = trace.kind.name
                    it[destination] = trace.destination.orEmpty().take(200)
                    it[priceLevel] = trace.priceLevel
                    it[interests] = trace.interests.take(20).map { i -> i.take(40) }
                    it[newSearch] = trace.newSearch
                    it[searchSource] = source.name
                    it[cards] = if (succeeded) trace.placeIds.size else 0
                    it[placeIds] = if (succeeded) trace.placeIds else emptyList()
                    it[AiSearchLog.latencyMs] = latencyMs
                    it[creditsCharged] = if (succeeded) trace.creditsCharged else 0
                    it[llmCostMicroUsd] = usage.costMicroUsd(ProviderService.LLM, prices)
                    it[placesCostMicroUsd] = usage.costMicroUsd(ProviderService.PLACES, prices)
                }
            }
        }.onFailure { log.warn("Search log write failed: {}", it.javaClass.simpleName) } // the search itself still answers
    }

    /** Called from the hourly job. */
    fun purge(): Int = transaction {
        AiSearchLog.deleteWhere { createdAt less Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS) }
    }

    private companion object {
        const val RETENTION_DAYS = 180L
    }
}
