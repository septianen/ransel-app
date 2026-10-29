package app.ransel.server.service

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Requests, 5xx responses and latency of this process per hour, the last 24 hours, for Karung's system page. In
 * memory only: a restart starts over, which [startedAt] makes visible. Latency goes into fixed buckets, so p95 is the
 * upper edge of its bucket.
 */
class RequestStats(val startedAt: Instant = Instant.now()) {
    private class Hour {
        val requests = AtomicLong()
        val serverErrors = AtomicLong()
        val latency = AtomicLongArray(BOUNDS_MS.size + 1)
    }

    data class Summary(val requests: Long, val serverErrors: Long, val p95Ms: Int?)

    private val hours = ConcurrentHashMap<Long, Hour>()

    fun record(status: Int, millis: Long, at: Instant = Instant.now()) {
        val hour = at.epochSecond / 3600
        val h = hours.computeIfAbsent(hour) { Hour() }
        h.requests.incrementAndGet()
        if (status >= 500) h.serverErrors.incrementAndGet()
        h.latency.incrementAndGet(BOUNDS_MS.indexOfFirst { millis <= it }.let { if (it < 0) BOUNDS_MS.size else it })
        if (hours.size > 26) hours.keys.removeIf { it <= hour - 24 }
    }

    fun last24Hours(now: Instant = Instant.now()): Summary {
        val from = now.epochSecond / 3600 - 23
        val recent = hours.filterKeys { it >= from }.values
        val buckets = LongArray(BOUNDS_MS.size + 1)
        recent.forEach { h -> for (i in buckets.indices) buckets[i] += h.latency[i] }
        val total = buckets.sum()
        val p95 = if (total == 0L) null else {
            val target = (total * 95 + 99) / 100
            var seen = 0L
            val index = buckets.indexOfFirst { seen += it; seen >= target }
            BOUNDS_MS.getOrElse(index) { BOUNDS_MS.last() }.toInt()
        }
        return Summary(recent.sumOf { it.requests.get() }, recent.sumOf { it.serverErrors.get() }, p95)
    }

    private companion object {
        /** Upper edges in ms; anything slower lands in the last bucket and reads as the last edge. */
        val BOUNDS_MS = longArrayOf(5, 10, 20, 30, 50, 75, 100, 150, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_000, 5_000, 10_000, 30_000, 60_000, 120_000)
    }
}
