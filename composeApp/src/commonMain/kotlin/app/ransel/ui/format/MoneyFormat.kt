package app.ransel.ui.format

import app.ransel.shared.domain.currencyDecimals
import kotlin.math.abs

private val SYMBOLS = mapOf(
    "IDR" to "Rp",
    "USD" to "US$",
    "EUR" to "€",
    "GBP" to "£",
    "JPY" to "¥",
    "SGD" to "S$",
    "MYR" to "RM",
    "AUD" to "A$",
)

/**
 * Display text for a stored amount: [amountMinor] is in the minor unit of [currency]
 * (`shared/domain/Money.kt`). Indonesian number style: "." groups thousands, "," starts decimals.
 * `formatMoney(50000, "IDR") == "Rp50.000"`, `formatMoney(1250, "USD") == "US$12,50"`.
 */
fun formatMoney(amountMinor: Long, currency: String): String {
    val decimals = currencyDecimals(currency)
    var divisor = 1L
    repeat(decimals) { divisor *= 10 }
    val absolute = abs(amountMinor)
    val whole = (absolute / divisor).toString().reversed().chunked(3).joinToString(".").reversed()
    val fraction = if (decimals == 0) "" else "," + (absolute % divisor).toString().padStart(decimals, '0')
    val code = currency.uppercase()
    val symbol = SYMBOLS[code] ?: "$code "
    val sign = if (amountMinor < 0) "-" else ""
    return "$sign$symbol$whole$fraction"
}

/**
 * Short amounts for tight rows ("Rp4,25 jt", "Rp1,1 jt", "Rp850 rb"). Whole numbers under 1.000
 * major units are shown in full. [thousand]/[million]/[billion] are the localized suffixes.
 */
fun formatMoneyCompact(
    amountMinor: Long,
    currency: String,
    thousand: String,
    million: String,
    billion: String,
): String {
    var divisor = 1L
    repeat(currencyDecimals(currency)) { divisor *= 10 }
    val major = abs(amountMinor) / divisor
    val (value, unit) = when {
        major >= 1_000_000_000 -> major / 1_000_000_000.0 to billion
        major >= 1_000_000 -> major / 1_000_000.0 to million
        major >= 1_000 -> major / 1_000.0 to thousand
        else -> return formatMoney(amountMinor, currency)
    }
    // up to two decimals, trailing zeros dropped, "," as decimal separator
    val hundredths = (value * 100).toLong()
    val whole = hundredths / 100
    val frac = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    val number = if (frac.isEmpty()) "$whole" else "$whole,$frac"
    val code = currency.uppercase()
    val symbol = SYMBOLS[code] ?: "$code "
    val sign = if (amountMinor < 0) "-" else ""
    return "$sign$symbol$number $unit"
}
