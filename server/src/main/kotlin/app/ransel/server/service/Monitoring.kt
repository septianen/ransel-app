package app.ransel.server.service

import app.ransel.server.ai.SearchLog
import app.ransel.server.ai.UsageMeter
import app.ransel.server.db.ErrorCountUsers
import app.ransel.server.db.ErrorCounts
import app.ransel.server.db.UserActiveDays
import app.ransel.server.db.Users
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.javatime.JavaInstantColumnType
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Karung's stored monitoring data (docs/specs/admin-spec.md "Monitoring data"). Set by the module like [Notify];
 * the minute job calls [flush], the hourly job [purge], and tests flush before they read the tables.
 */
object Monitoring {
    @Volatile var errors: ErrorCounter? = null
    @Volatile var usage: UsageMeter? = null
    @Volatile var searches: SearchLog? = null

    fun flush() {
        errors?.flush()
        usage?.flush()
    }

    fun purge() {
        errors?.purge()
        searches?.purge()
        UsageMeter.purge()
        LastSeen.purge()
    }
}

/**
 * Error responses per hour, code and route template, counted in memory and added to `error_counts` on [flush].
 * Routes are templates (`GET /trips/{id}/expenses`), so a table row never carries an id.
 */
class ErrorCounter {
    private data class Key(val hour: Instant, val code: String, val route: String)

    private class Bucket(var status: Int, var count: Long, var lastAt: Instant, val users: MutableSet<UUID>)

    private val pending = ConcurrentHashMap<Key, Bucket>()

    fun record(code: String, route: String, status: Int, userId: UUID?, at: Instant = Instant.now()) {
        val key = Key(at.truncatedTo(ChronoUnit.HOURS), code, route)
        // compute holds the key's lock, so a flush never takes a bucket half-way through an update
        pending.compute(key) { _, bucket ->
            (bucket ?: Bucket(status, 0, at, mutableSetOf())).also {
                it.status = status
                it.count++
                if (at > it.lastAt) it.lastAt = at
                userId?.let(it.users::add)
            }
        }
    }

    fun flush() {
        val batch = pending.keys.toList().mapNotNull { key -> pending.remove(key)?.let { key to it } }
        if (batch.isEmpty()) return
        try {
            transaction {
                batch.forEach { (key, bucket) ->
                    ErrorCounts.upsert(
                        onUpdate = {
                            it[ErrorCounts.count] = ErrorCounts.count + bucket.count
                            it[ErrorCounts.lastAt] = CustomFunction("GREATEST", JavaInstantColumnType(), ErrorCounts.lastAt, insertValue(ErrorCounts.lastAt))
                            it[ErrorCounts.status] = insertValue(ErrorCounts.status)
                        },
                    ) {
                        it[hour] = key.hour
                        it[code] = key.code
                        it[route] = key.route
                        it[status] = bucket.status
                        it[count] = bucket.count
                        it[lastAt] = bucket.lastAt
                    }
                    ErrorCountUsers.batchInsert(bucket.users, ignore = true, shouldReturnGeneratedValues = false) { user ->
                        this[ErrorCountUsers.hour] = key.hour
                        this[ErrorCountUsers.code] = key.code
                        this[ErrorCountUsers.route] = key.route
                        this[ErrorCountUsers.userId] = user
                    }
                }
            }
        } catch (e: Exception) {
            // put them back, so a database hiccup only delays them
            batch.forEach { (key, b) ->
                pending.merge(key, b) { now, old ->
                    now.count += old.count
                    if (old.lastAt > now.lastAt) now.lastAt = old.lastAt
                    now.users += old.users
                    now
                }
            }
            throw e
        }
    }

    fun purge(): Int = transaction {
        val before = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS)
        ErrorCountUsers.deleteWhere { hour less before }
        ErrorCounts.deleteWhere { hour less before }
    }

    private companion object {
        const val RETENTION_DAYS = 90L
    }
}

/**
 * `users.last_seen_at` and `user_active_days` for "active users": written off the request's path, at most once an
 * hour per user and again when the WIB day changes. The map spares the database a query per request; the WHERE and
 * the insert-ignore spare it duplicates after a restart emptied the map.
 */
class LastSeen(private val scope: CoroutineScope) {
    private val log = LoggerFactory.getLogger(LastSeen::class.java)
    private val written = ConcurrentHashMap<UUID, Pair<Long, LocalDate>>()
    private val wib = ZoneId.of("Asia/Jakarta")

    fun touch(userId: UUID) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now(wib)
        var due = false
        written.compute(userId) { _, last ->
            if (last == null || now - last.first >= INTERVAL_MILLIS || last.second != today) (now to today).also { due = true } else last
        }
        if (due) {
            scope.launch {
                runCatching { write(userId) }.onFailure {
                    written.remove(userId) // try again on the next request
                    log.warn("last_seen_at write failed: {}", it.javaClass.simpleName)
                }
            }
        }
    }

    fun write(userId: UUID, at: Instant = Instant.now()) = transaction {
        Users.update({
            (Users.id eq userId) and (Users.lastSeenAt.isNull() or (Users.lastSeenAt less at.minusMillis(INTERVAL_MILLIS)))
        }) { it[lastSeenAt] = at }
        UserActiveDays.insertIgnore {
            it[day] = at.atZone(wib).toLocalDate()
            it[UserActiveDays.userId] = userId
        }
    }

    companion object {
        private const val INTERVAL_MILLIS = 60 * 60 * 1000L
        private const val RETENTION_DAYS = 400L

        fun purge(): Int = transaction {
            UserActiveDays.deleteWhere { day less LocalDate.now().minusDays(RETENTION_DAYS) }
        }
    }
}
