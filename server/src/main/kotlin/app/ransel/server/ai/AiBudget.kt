package app.ransel.server.ai

import app.ransel.server.db.ProviderUsage
import app.ransel.server.plugins.ApiException
import app.ransel.server.service.SettingsService
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.model.AiBudgetMode
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.YearMonth
import kotlin.time.Clock

/**
 * The AI budget from Karung (stage C). With [AiBudgetMode.CACHE_ONLY], once this WIB month's provider spend
 * (`provider_usage` plus what [meter] still holds) reaches the budget, [check] answers 503 AI_PAUSED before any LLM
 * call: cached searches still work, new searches and receipt photos stop, and since credits are charged after a
 * success nothing is charged. The month's spend is read at most once a minute.
 */
class AiBudget(private val settings: SettingsService, private val meter: UsageMeter) {
    private val wib = TimeZone.of("Asia/Jakarta")
    @Volatile private var spent: Pair<Long, Long>? = null // read at (epoch ms) to month's spend from the table

    fun check() {
        val config = settings.current
        val budget = config.aiMonthlyBudgetMicroUsd
        if (config.aiBudgetMode != AiBudgetMode.CACHE_ONLY || budget <= 0) return
        if (spentThisMonth() >= budget) {
            throw ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.AI_PAUSED, "The AI budget for this month is used up")
        }
    }

    private fun spentThisMonth(): Long {
        val today = Clock.System.now().toLocalDateTime(wib).date
        val now = System.currentTimeMillis()
        val stored = spent?.takeIf { now - it.first < 60_000 }?.second ?: run {
            val month = YearMonth.of(today.year, today.month.ordinal + 1)
            val sum = ProviderUsage.costMicroUsd.sum()
            transaction {
                ProviderUsage.select(sum).where { ProviderUsage.day.between(month.atDay(1), month.atEndOfMonth()) }.single()[sum] ?: 0L
            }.also { spent = now to it }
        }
        return stored + meter.pendingMicroUsd(today.month, today.year)
    }
}
