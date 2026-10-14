package app.ransel.shared.domain

import kotlin.math.roundToLong

/*
 * Money is a Long in the minor unit of its currency. RANSEL's minor unit follows how
 * people write the currency, not strict ISO 4217: Rp50.000 = 50000 (IDR has 0 decimals
 * in the app), USD 12,50 = 1250.
 */

private val ZERO_DECIMAL = setOf("IDR", "JPY", "KRW", "VND", "CLP", "ISK", "HUF", "TWD", "UGX")
private val THREE_DECIMAL = setOf("BHD", "KWD", "OMR", "JOD", "TND")

fun currencyDecimals(code: String): Int = when (code.uppercase()) {
    in ZERO_DECIMAL -> 0
    in THREE_DECIMAL -> 3
    else -> 2
}

private fun pow10(n: Int): Long { var r = 1L; repeat(n) { r *= 10 }; return r }

/**
 * Converts [amountMinor] of [from] into minor units of [to] with [fxRateToBase]
 * (1 major [from] = rate major [to], decimal string). Rounds half up.
 * Only for summaries and balances; stored amounts are never converted.
 */
fun convertMinor(amountMinor: Long, from: String, to: String, fxRateToBase: String?): Long {
    if (from.equals(to, ignoreCase = true) || fxRateToBase == null) return amountMinor
    val rate = fxRateToBase.toDouble()
    val major = amountMinor.toDouble() / pow10(currencyDecimals(from))
    return (major * rate * pow10(currencyDecimals(to))).roundToLong()
}
