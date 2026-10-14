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
