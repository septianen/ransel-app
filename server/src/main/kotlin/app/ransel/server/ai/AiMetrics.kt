package app.ransel.server.ai

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * In-process counters, logged as one line per search and as a summary line each hour (see Application).
 * Not exposed over HTTP on purpose: there is no admin role to protect such an endpoint.
 */
class AiMetrics {
    val searches = AtomicLong()
    val cacheHits = AtomicLong()
    val llmCalls = AtomicLong()
    val llmFailures = AtomicLong()
    val placesSearchCalls = AtomicLong()
    val placesDetailsCalls = AtomicLong()
    val quotaRejections = AtomicLong()
    val unavailable = AtomicLong()

    private val log = LoggerFactory.getLogger("app.ransel.server.ai.metrics")

    fun summary(): String {
        val s = searches.get()
        val rate = if (s == 0L) "n/a" else "%.0f%%".format(100.0 * cacheHits.get() / s)
        return "ai.metrics searches=$s cacheHitRate=$rate llmCalls=${llmCalls.get()} llmFailures=${llmFailures.get()} " +
            "placesSearch=${placesSearchCalls.get()} placesDetails=${placesDetailsCalls.get()} " +
            "quota429=${quotaRejections.get()} unavailable503=${unavailable.get()}"
    }

    fun logSummary() = log.info(summary())
}
