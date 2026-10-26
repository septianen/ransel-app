package app.ransel.server.service

import app.ransel.server.db.FxRates
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.domain.isValidCurrency
import app.ransel.shared.dto.FxRateDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Exchange rates for expenses (PRD "Overseas trips"): the ECB euro reference rates, stored per day once a day, crossed
 * through EUR. [fetchHistory] returns the ECB 90-day history XML; null = no network source (tests insert rows).
 */
class FxRateService(private val fetchHistory: (suspend () -> String)? = null) {
    private val log = LoggerFactory.getLogger(FxRateService::class.java)

    /** Stores every day of the 90-day file; days already stored are overwritten with the same numbers. */
    suspend fun refresh() {
        val xml = try {
            fetchHistory?.invoke() ?: return
        } catch (e: Exception) {
            log.warn("fx.history failed: {}", e.javaClass.simpleName) // the stored days stay usable
            return
        }
        val days = parseHistory(xml)
        transaction {
            days.forEach { (day, rates) ->
                FxRates.batchUpsert(rates.entries) { (currency, perEuro) ->
                    this[FxRates.rateDate] = day.toJavaLocalDate()
                    this[FxRates.currency] = currency
                    this[FxRates.perEuro] = perEuro
                }
            }
        }
        log.info("fx.history days={}", days.size)
    }

    /** 1 [from] in [to] on [date], or the latest earlier day both are quoted (`stale`). 404 when there is none. */
    fun rate(from: String, to: String, date: LocalDate): FxRateDto {
        val a = from.uppercase()
        val b = to.uppercase()
        requireValid(
            buildMap {
                if (!isValidCurrency(a)) put("from", "INVALID_CURRENCY")
                if (!isValidCurrency(b)) put("to", "INVALID_CURRENCY")
            },
        )
        if (a == b) return FxRateDto(a, b, "1", date)
        val wanted = listOf(a, b).filter { it != EUR }
        return transaction {
            // the latest day on or before [date] that quotes every currency needed
            val rows = FxRates.selectAll()
                .where { (FxRates.currency inList wanted) and (FxRates.rateDate lessEq date.toJavaLocalDate()) }
                .orderBy(FxRates.rateDate to SortOrder.DESC)
                .limit(wanted.size * 40)
                .groupBy { it[FxRates.rateDate] }
            val (day, quotes) = rows.entries.firstOrNull { (_, r) -> r.map { it[FxRates.currency] }.toSet().containsAll(wanted) }
                ?: throw notFound("Exchange rate")
            fun perEuro(code: String) = if (code == EUR) BigDecimal.ONE else quotes.first { it[FxRates.currency] == code }[FxRates.perEuro]
            val rate = perEuro(b).divide(perEuro(a), MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
            val kotlinDay = day.toKotlinLocalDate()
            FxRateDto(a, b, rate.toPlainString(), kotlinDay, stale = kotlinDay != date)
        }
    }

    companion object {
        const val EUR = "EUR"
        const val HISTORY_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml"
        private val DAY = Regex("""<Cube\s+time=['"](\d{4}-\d{2}-\d{2})['"]\s*>(.*?)</Cube>""", RegexOption.DOT_MATCHES_ALL)
        private val QUOTE = Regex("""currency=['"]([A-Z]{3})['"]\s+rate=['"]([0-9.]+)['"]""")

        /** Day → currency → units per 1 EUR, from the ECB history XML. */
        internal fun parseHistory(xml: String): Map<LocalDate, Map<String, BigDecimal>> =
            DAY.findAll(xml).associate { day ->
                LocalDate.parse(day.groupValues[1]) to QUOTE.findAll(day.groupValues[2])
                    .mapNotNull { q -> q.groupValues[2].toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { q.groupValues[1] to it } }
                    .toMap()
            }.filterValues { it.isNotEmpty() }
    }
}
