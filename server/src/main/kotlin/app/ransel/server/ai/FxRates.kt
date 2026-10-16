package app.ransel.server.ai

import app.ransel.shared.domain.currencyDecimals
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

/** Converts a trip budget to US dollars so it can be compared with the destination's price guide. */
interface FxRates {
    /** USD value of [amountMinor] (minor units of [currency]); null if the rate is unknown. */
    fun toUsd(amountMinor: Long, currency: String): Double?
}

/**
 * ECB euro reference rates (one free request a day), kept in memory. Rates are per 1 EUR, so USD per unit of X is
 * usd / x. Before the first successful refresh, or for a currency ECB does not publish, [toUsd] answers null and the
 * app simply gets no suggested price level.
 */
class EcbFxRates(
    private val http: HttpClient,
    private val url: String = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml",
) : FxRates {
    private val log = LoggerFactory.getLogger(EcbFxRates::class.java)
    private val perEur = AtomicReference<Map<String, Double>>(emptyMap())

    suspend fun refresh() {
        try {
            val response = http.get(url)
            if (!response.status.isSuccess()) error("ECB HTTP ${response.status.value}")
            val parsed = parse(response.body<String>())
            if (parsed.isNotEmpty()) perEur.set(parsed + ("EUR" to 1.0))
            log.info("fx.refresh currencies={}", parsed.size)
        } catch (e: Exception) {
            log.warn("FX refresh failed: {}", e.javaClass.simpleName) // keep the previous rates
        }
    }

    override fun toUsd(amountMinor: Long, currency: String): Double? = convert(perEur.get(), amountMinor, currency)

    companion object {
        private val CUBE = Regex("""currency=['"]([A-Z]{3})['"]\s+rate=['"]([0-9.]+)['"]""")

        fun parse(xml: String): Map<String, Double> =
            CUBE.findAll(xml).mapNotNull { m -> m.groupValues[2].toDoubleOrNull()?.takeIf { it > 0 }?.let { m.groupValues[1] to it } }.toMap()

        internal fun convert(perEur: Map<String, Double>, amountMinor: Long, currency: String): Double? {
            val usd = perEur["USD"] ?: return null
            val own = perEur[currency.uppercase()] ?: return null
            val major = amountMinor / 10.0.pow(currencyDecimals(currency))
            return major * usd / own
        }
    }
}

/** No rates at all: used when Explore is not configured. */
object NoFxRates : FxRates {
    override fun toUsd(amountMinor: Long, currency: String): Double? = null
}
