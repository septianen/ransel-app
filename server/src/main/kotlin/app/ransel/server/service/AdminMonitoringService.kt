package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.dto.AdminActiveDayDto
import app.ransel.shared.dto.AdminAiCostDto
import app.ransel.shared.dto.AdminCostByServiceDto
import app.ransel.shared.dto.AdminCostDayDto
import app.ransel.shared.dto.AdminCountDto
import app.ransel.shared.dto.AdminErrorDto
import app.ransel.shared.dto.AdminExploreDto
import app.ransel.shared.dto.AdminInterestDto
import app.ransel.shared.dto.AdminOverviewDto
import app.ransel.shared.dto.AdminSearchDto
import app.ransel.shared.dto.AdminSystemDto
import app.ransel.shared.model.AdminRange
import app.ransel.shared.model.AiSearchKind
import app.ransel.shared.model.AiSearchSource
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Karung's monitoring pages (docs/specs/admin-spec.md, stage B) from the V27 tables, `trips`, `users` and
 * `credit_ledger`. Counts, dates and destinations only, never trip content. Days are WIB days.
 */
class AdminMonitoringService(private val config: AppConfig, private val requests: RequestStats) {
    private val wib = ZoneId.of("Asia/Jakarta")

    // ── Ringkasan ───────────────────────────────────────────────────────────

    fun overview(range: AdminRange, now: Instant = Instant.now()): AdminOverviewDto {
        if (range !in OVERVIEW_RANGES) throw validationFailed(mapOf("range" to "UNKNOWN"))
        Monitoring.flush()
        return transaction {
            val today = LocalDate.ofInstant(now, wib)
            val todayStart = start(today)
            val rangeDays = when (range) {
                AdminRange.LAST_7_DAYS -> 7L
                AdminRange.LAST_30_DAYS -> 30L
                else -> 1L
            }
            val rangeStart = start(today.minusDays(rangeDays - 1))
            val active = query(
                """
                SELECT count(*) FILTER (WHERE day = ?), count(*) FILTER (WHERE day = ?), count(DISTINCT user_id) FILTER (WHERE day > ?)
                FROM user_active_days WHERE day > ?
                """,
                today, today.minusDays(7), today.minusDays(7), today.minusDays(8),
            ) { Triple(it.getInt(1), it.getInt(2), it.getInt(3)) }.single()
            val registered = query("SELECT count(*) FROM users WHERE email IS NOT NULL AND deleted_at IS NULL") { it.getInt(1) }.single()
            val trips = query(
                """
                SELECT count(*) FILTER (WHERE created_at >= ? AND created_at < ?), count(*) FILTER (WHERE created_at >= ? AND created_at < ?)
                FROM trips WHERE created_at >= ?
                """,
                rangeStart, utc(now), rangeStart.minusDays(rangeDays), utc(now).minusDays(rangeDays), rangeStart.minusDays(rangeDays),
            ) { it.getInt(1) to it.getInt(2) }.single()
            val searches = query(
                """
                SELECT count(*) FILTER (WHERE source <> 'NO_CREDITS'), count(*) FILTER (WHERE source = 'CACHE'),
                       count(DISTINCT user_id) FILTER (WHERE source = 'NO_CREDITS')
                FROM ai_search_log WHERE created_at >= ?
                """,
                todayStart,
            ) { Triple(it.getInt(1), it.getInt(2), it.getInt(3)) }.single()
            val credits = query(
                """
                SELECT coalesce(-sum(delta) FILTER (WHERE kind = 'SPEND'), 0), coalesce(-sum(delta) FILTER (WHERE kind = 'SPEND' AND source = 'FREE_DAILY'), 0),
                       count(*) FILTER (WHERE kind = 'VOUCHER')
                FROM credit_ledger WHERE created_at >= ?
                """,
                todayStart,
            ) { Triple(it.getInt(1), it.getInt(2), it.getInt(3)) }.single()
            val month = YearMonth.from(today)
            AdminOverviewDto(
                range = range,
                generatedAt = now.toTimestamp(),
                activeToday = active.first,
                activeWeekAgo = active.second,
                activeLast7Days = active.third,
                registeredUsers = registered,
                newTrips = trips.first,
                newTripsPrevious = trips.second,
                searchesToday = searches.first,
                searchesTodayFromCache = searches.second,
                usersOutOfCreditsToday = searches.third,
                creditsUsedToday = credits.first,
                creditsUsedTodayFree = credits.second,
                vouchersRedeemedToday = credits.third,
                aiCost = aiCost(month, today),
                activeByDay = activeByDay(today.minusDays(29), today),
                topDestinations = query(
                    """
                    SELECT min(trim(destination)), count(*) FROM trips
                    WHERE created_at >= ? AND trim(destination) <> ''
                    GROUP BY lower(trim(destination)) ORDER BY count(*) DESC, min(trim(destination)) LIMIT 6
                    """,
                    start(today.minusDays(29)),
                ) { AdminCountDto(it.getString(1), it.getInt(2)) },
                recentErrors = errors(utc(now).minusHours(24), today, limit = 5, withTrend = false),
                costByService = costByService(month),
            )
        }
    }

    private fun JdbcTransaction.activeByDay(from: LocalDate, to: LocalDate): List<AdminActiveDayDto> {
        val active = query("SELECT day, count(*) FROM user_active_days WHERE day >= ? AND day <= ? GROUP BY day", from, to) {
            it.getObject(1, LocalDate::class.java) to it.getInt(2)
        }.toMap()
        val signups = query(
            """
            SELECT (created_at AT TIME ZONE 'Asia/Jakarta')::date, count(*) FROM users
            WHERE email IS NOT NULL AND created_at >= ? GROUP BY 1
            """,
            start(from),
        ) { it.getObject(1, LocalDate::class.java) to it.getInt(2) }.toMap()
        return days(from, to).map { AdminActiveDayDto(it.toKotlinLocalDate(), active[it] ?: 0, signups[it] ?: 0) }
    }

    private fun JdbcTransaction.costByService(month: YearMonth): AdminCostByServiceDto {
        val cost = query(
            "SELECT service, sum(cost_micro_usd) FROM provider_usage WHERE day >= ? AND day <= ? GROUP BY service",
            month.atDay(1), month.atEndOfMonth(),
        ) { it.getString(1) to it.getLong(2) }.toMap()
        return AdminCostByServiceDto(cost["LLM"] ?: 0, cost["PLACES"] ?: 0, config.serverMonthlyCostMicroUsd)
    }

    private fun JdbcTransaction.aiCost(month: YearMonth, today: LocalDate): AdminAiCostDto {
        val spent = query(
            "SELECT coalesce(sum(cost_micro_usd), 0) FROM provider_usage WHERE day >= ? AND day <= ?",
            month.atDay(1), month.atEndOfMonth(),
        ) { it.getLong(1) }.single()
        val elapsed = daysSoFar(month, today)
        val projected = when {
            elapsed == 0 -> 0
            elapsed >= month.lengthOfMonth() -> spent
            else -> spent * month.lengthOfMonth() / elapsed
        }
        return AdminAiCostDto(
            month = month.toString(),
            spentMicroUsd = spent,
            projectedMicroUsd = projected,
            budgetMicroUsd = config.aiMonthlyBudgetMicroUsd.takeIf { it > 0 },
            alertPercent = config.aiBudgetAlertPercent,
        )
    }

    // ── Jelajah AI ──────────────────────────────────────────────────────────

    fun explore(monthParam: String?, now: Instant = Instant.now()): AdminExploreDto {
        val today = LocalDate.ofInstant(now, wib)
        val month = monthParam?.let { runCatching { YearMonth.parse(it) }.getOrNull() ?: throw validationFailed(mapOf("month" to "INVALID")) }
            ?: YearMonth.from(today)
        Monitoring.flush()
        return transaction {
            val from = start(month.atDay(1))
            val until = start(month.plusMonths(1).atDay(1))
            data class Totals(
                val searches: Int, val cache: Int, val outOfCredits: Int, val cards: Int,
                val median: Int?, val p95: Int?, val avgNewCost: Long, val explore: Int,
            )
            val t = query(
                """
                SELECT count(*) FILTER (WHERE source <> 'NO_CREDITS'),
                       count(*) FILTER (WHERE source = 'CACHE'),
                       count(DISTINCT user_id) FILTER (WHERE source = 'NO_CREDITS'),
                       coalesce(sum(cards) FILTER (WHERE kind = 'EXPLORE' AND source IN ('NEW', 'CACHE')), 0),
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY latency_ms) FILTER (WHERE source IN ('NEW', 'CACHE')),
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) FILTER (WHERE source IN ('NEW', 'CACHE')),
                       coalesce(avg(llm_cost_micro_usd + places_cost_micro_usd) FILTER (WHERE source = 'NEW'), 0),
                       count(*) FILTER (WHERE kind = 'EXPLORE' AND source <> 'NO_CREDITS')
                FROM ai_search_log WHERE created_at >= ? AND created_at < ?
                """,
                from, until,
            ) {
                Totals(
                    it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4),
                    it.getObject(5)?.let { v -> (v as Number).toInt() }, it.getObject(6)?.let { v -> (v as Number).toInt() },
                    it.getDouble(7).toLong(), it.getInt(8),
                )
            }.single()
            val saved = query(
                """
                SELECT count(*) FROM ai_search_log l
                JOIN recommendation_feedback f ON f.trip_id = l.trip_id AND f.user_id = l.user_id AND f.place_id = ANY (l.place_ids)
                WHERE l.created_at >= ? AND l.created_at < ? AND l.kind = 'EXPLORE' AND l.source IN ('NEW', 'CACHE') AND f.action = 'LIKE'
                """,
                from, until,
            ) { it.getInt(1) }.single()
            val credits = query(
                """
                SELECT coalesce(-sum(delta) FILTER (WHERE source = 'FREE_DAILY'), 0), coalesce(-sum(delta) FILTER (WHERE source = 'WALLET'), 0)
                FROM credit_ledger WHERE kind = 'SPEND' AND action IN ('SEARCH', 'NEXT_BATCH') AND created_at >= ? AND created_at < ?
                """,
                from, until,
            ) { it.getInt(1) to it.getInt(2) }.single()
            val elapsed = daysSoFar(month, today)
            val costs = query(
                """
                SELECT day, coalesce(sum(cost_micro_usd) FILTER (WHERE service = 'LLM'), 0), coalesce(sum(cost_micro_usd) FILTER (WHERE service = 'PLACES'), 0)
                FROM provider_usage WHERE day >= ? AND day <= ? GROUP BY day
                """,
                month.atDay(1), month.atEndOfMonth(),
            ) { it.getObject(1, LocalDate::class.java) to (it.getLong(2) to it.getLong(3)) }.toMap()
            val levels = query(
                """
                SELECT price_level, count(*) FROM ai_search_log
                WHERE created_at >= ? AND created_at < ? AND kind = 'EXPLORE' AND source <> 'NO_CREDITS' AND price_level IS NOT NULL
                GROUP BY price_level
                """,
                from, until,
            ) { it.getString(1) to it.getInt(2) }.toMap()
            val builtIn = Interest.entries.map { it.name }.toSet()
            val interests = query(
                """
                SELECT i, count(*), mode() WITHIN GROUP (ORDER BY destination)
                FROM ai_search_log, unnest(interests) AS i
                WHERE created_at >= ? AND created_at < ? AND kind = 'EXPLORE' AND source <> 'NO_CREDITS'
                GROUP BY i ORDER BY count(*) DESC, i LIMIT 8
                """,
                from, until,
            ) { AdminInterestDto(it.getString(1), it.getInt(2), it.getString(3).takeIf { _ -> it.getString(1) !in builtIn }) }
            AdminExploreDto(
                month = month.toString(),
                days = elapsed,
                searches = t.searches,
                searchesFromCache = t.cache,
                cacheSavingsMicroUsd = t.cache * t.avgNewCost,
                cardsShown = t.cards,
                cardsSaved = saved,
                latencyMedianMs = t.median,
                latencyP95Ms = t.p95,
                usersOutOfCredits = t.outOfCredits,
                creditsFromFree = credits.first,
                creditsFromWallet = credits.second,
                aiCost = aiCost(month, today),
                costByDay = if (elapsed == 0) emptyList() else days(month.atDay(1), month.atDay(elapsed)).map { day ->
                    val (llm, places) = costs[day] ?: (0L to 0L)
                    AdminCostDayDto(day.toKotlinLocalDate(), llm, places)
                },
                priceLevels = PriceLevel.entries.map { AdminCountDto(it.name, levels[it.name] ?: 0) },
                exploreSearches = t.explore,
                topInterests = interests,
            )
        }
    }

    /** "Pencarian terbaru", newest first; [userId] narrows it to one user (the user page). */
    fun searches(userId: UUID?, page: Int, pageSize: Int): Pair<List<AdminSearchDto>, Int> = transaction {
        val filter = if (userId != null) "WHERE l.user_id = ?" else ""
        val args: List<Any> = listOfNotNull(userId)
        val total = query("SELECT count(*) FROM ai_search_log l $filter", *args.toTypedArray()) { it.getInt(1) }.single()
        val rows = query(
            """
            SELECT l.id, l.created_at, l.user_id, coalesce(u.display_name, ''), l.kind, l.destination, l.price_level, l.interests,
                   l.new_search, l.source, l.cards, l.latency_ms, l.credits_charged, l.llm_cost_micro_usd + l.places_cost_micro_usd,
                   (SELECT count(*) FROM recommendation_feedback f
                     WHERE f.trip_id = l.trip_id AND f.user_id = l.user_id AND f.place_id = ANY (l.place_ids) AND f.action = 'LIKE')
            FROM ai_search_log l LEFT JOIN users u ON u.id = l.user_id
            $filter
            ORDER BY l.created_at DESC, l.id LIMIT ? OFFSET ?
            """,
            *(args + listOf<Any>(pageSize, (page - 1) * pageSize)).toTypedArray(),
        ) {
            @Suppress("UNCHECKED_CAST")
            AdminSearchDto(
                id = it.getString(1),
                createdAt = it.getObject(2, OffsetDateTime::class.java).toInstant().toTimestamp(),
                userId = it.getString(3),
                userName = it.getString(4),
                kind = AiSearchKind.valueOf(it.getString(5)),
                destination = it.getString(6),
                priceLevel = it.getString(7)?.let { v -> PriceLevel.entries.firstOrNull { p -> p.name == v } },
                interests = (it.getArray(8).array as Array<String>).toList(),
                newSearch = it.getBoolean(9),
                source = AiSearchSource.valueOf(it.getString(10)),
                cards = it.getInt(11),
                saved = it.getInt(15),
                latencyMs = it.getInt(12),
                creditsCharged = it.getInt(13),
                costMicroUsd = it.getLong(14),
            )
        }
        rows to total
    }

    // ── Sistem ──────────────────────────────────────────────────────────────

    fun system(range: AdminRange, now: Instant = Instant.now()): AdminSystemDto {
        if (range !in SYSTEM_RANGES) throw validationFailed(mapOf("range" to "UNKNOWN"))
        Monitoring.flush()
        val today = LocalDate.ofInstant(now, wib)
        val traffic = requests.last24Hours(now)
        return transaction {
            val from = if (range == AdminRange.LAST_7_DAYS) start(today.minusDays(6)) else utc(now).minusHours(24)
            AdminSystemDto(
                range = range,
                generatedAt = now.toTimestamp(),
                version = config.appVersion.ifBlank { null },
                startedAt = requests.startedAt.toTimestamp(),
                requests24h = traffic.requests,
                serverErrors24h = traffic.serverErrors,
                latencyP95Ms = traffic.p95Ms,
                databaseBytes = query("SELECT pg_database_size(current_database())") { it.getLong(1) }.single(),
                lastBackupAt = lastBackup()?.toTimestamp(),
                flywayVersion = query(
                    "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1",
                ) { it.getString(1) }.singleOrNull().orEmpty(),
                llmConfigured = config.llmApiKey.isNotBlank(),
                placesConfigured = config.placesApiKey.isNotBlank(),
                emailConfigured = config.smtp != null,
                pushConfigured = config.fcmServiceAccountJson.isNotBlank(),
                errors = errors(from, today, limit = 50, withTrend = true),
            )
        }
    }

    /** The backup script writes an ISO instant; an unreadable file falls back to its modification time. */
    private fun lastBackup(): Instant? {
        val file = File(config.backupMarkerFile)
        if (!file.isFile) return null
        return runCatching { Instant.parse(file.readText().trim()) }.getOrNull()
            ?: file.lastModified().takeIf { it > 0 }?.let(Instant::ofEpochMilli)
    }

    /** Errors per code since [from]: total, the route with most, status, distinct users, last time; [withTrend] adds 7 days. */
    private fun JdbcTransaction.errors(from: OffsetDateTime, today: LocalDate, limit: Int, withTrend: Boolean): List<AdminErrorDto> {
        val since = from.truncatedTo(ChronoUnit.HOURS)
        val users = query("SELECT code, count(DISTINCT user_id) FROM error_count_users WHERE hour >= ? GROUP BY code", since) {
            it.getString(1) to it.getInt(2)
        }.toMap()
        val trendFrom = today.minusDays(6)
        val trend = if (!withTrend) emptyMap() else query(
            """
            SELECT code, (hour AT TIME ZONE 'Asia/Jakarta')::date, sum(count) FROM error_counts
            WHERE hour >= ? GROUP BY 1, 2
            """,
            start(trendFrom),
        ) { Triple(it.getString(1), it.getObject(2, LocalDate::class.java), it.getLong(3)) }
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, perDay) -> perDay.toMap().let { m -> days(trendFrom, today).map { d -> m[d] ?: 0L } } }
        return query(
            """
            WITH per_route AS (
                SELECT code, route, sum(count) AS n, max(last_at) AS last_at, max(status) AS status
                FROM error_counts WHERE hour >= ? GROUP BY code, route
            )
            SELECT code, sum(n), max(last_at), (array_agg(route ORDER BY n DESC, route))[1], (array_agg(status ORDER BY n DESC, route))[1]
            FROM per_route GROUP BY code ORDER BY sum(n) DESC, code LIMIT ?
            """,
            since, limit,
        ) {
            val code = it.getString(1)
            AdminErrorDto(
                code = code,
                httpStatus = it.getInt(5),
                topRoute = it.getString(4),
                count = it.getLong(2),
                users = users[code] ?: 0,
                lastAt = it.getObject(3, OffsetDateTime::class.java).toInstant().toTimestamp(),
                trend = if (withTrend) trend[code] ?: days(trendFrom, today).map { 0L } else emptyList(),
            )
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun start(day: LocalDate): OffsetDateTime = day.atStartOfDay(wib).toOffsetDateTime()

    private fun utc(at: Instant): OffsetDateTime = at.atOffset(ZoneOffset.UTC)

    /** Days of [month] that have begun: all of a past month, none of a future one. */
    private fun daysSoFar(month: YearMonth, today: LocalDate): Int = when {
        month < YearMonth.from(today) -> month.lengthOfMonth()
        month > YearMonth.from(today) -> 0
        else -> today.dayOfMonth
    }

    private fun days(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()

    /** Plain SQL for the aggregates (FILTER, percentiles, unnest), bound by position. */
    private fun <T> JdbcTransaction.query(sql: String, vararg args: Any?, row: (ResultSet) -> T): List<T> {
        val jdbc = connection.connection as Connection
        return jdbc.prepareStatement(sql.trimIndent()).use { statement ->
            args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
            statement.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
        }
    }

    private companion object {
        val OVERVIEW_RANGES = setOf(AdminRange.TODAY, AdminRange.LAST_7_DAYS, AdminRange.LAST_30_DAYS)
        val SYSTEM_RANGES = setOf(AdminRange.LAST_24_HOURS, AdminRange.LAST_7_DAYS)
    }
}
