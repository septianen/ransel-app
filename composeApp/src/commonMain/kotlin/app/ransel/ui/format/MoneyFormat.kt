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

/**
 * Parses text typed in an amount field into minor units of [currency] ("85.000" IDR → 85000,
 * "12,50" USD → 1250). Both "," and "." are accepted as the decimal separator for currencies that have
 * decimals; for zero-decimal currencies they are thousand separators and ignored. Returns null for
 * empty or invalid input.
 */
fun parseAmountMinor(text: String, currency: String): Long? {
    val decimals = currencyDecimals(currency)
    val cleaned = text.trim().filter { it.isDigit() || it == ',' || it == '.' }
    if (cleaned.none { it.isDigit() }) return null
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    if (decimals == 0) {
        return cleaned.filter { it.isDigit() }.toLongOrNull()
    }
    val separator = cleaned.indexOfLast { it == ',' || it == '.' }
    val whole = (if (separator >= 0) cleaned.substring(0, separator) else cleaned).filter { it.isDigit() }
    val fraction = if (separator >= 0) cleaned.substring(separator + 1).filter { it.isDigit() } else ""
    val wholePart = (whole.ifEmpty { "0" }).toLongOrNull() ?: return null
    val fractionPart = fraction.padEnd(decimals, '0').take(decimals).toLongOrNull() ?: 0L
    return wholePart * scale + fractionPart
}

/** Inverse of [parseAmountMinor] for pre-filling a field: 85000 IDR → "85000", 1250 USD → "12,50". */
fun amountToInput(amountMinor: Long, currency: String): String {
    val decimals = currencyDecimals(currency)
    if (decimals == 0) return amountMinor.toString()
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val whole = amountMinor / scale
    val fraction = (amountMinor % scale).toString().padStart(decimals, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole" else "$whole,$fraction"
}

/** Decimals a typed exchange rate keeps (the server stores rates with 6). */
const val FX_RATE_INPUT_DECIMALS = 6

/**
 * A stored rate ("16250.5", decimal point) as the text of a rate field, which follows the amount fields: ","
 * starts the decimals and the field groups thousands itself. "16250.500000" → "16250,5".
 */
fun fxRateToInput(rate: String): String {
    val trimmed = rate.trim()
    val plain = if ('.' in trimmed) trimmed.trimEnd('0').trimEnd('.') else trimmed
    return plain.replace('.', ',')
}

/**
 * Inverse of [fxRateToInput]: the field's text (cleaned by [sanitizeAmountInput]) as a stored rate, or null when it
 * is empty or not above zero. "16250" → "16250", "0,62" → "0.62".
 */
fun fxRateFromInput(text: String): String? {
    val rate = text.trim().replace(',', '.').trimEnd('.')
    val value = rate.toDoubleOrNull() ?: return null
    return rate.takeIf { value > 0.0 }
}

/**
 * Cleans text typed into an amount field: digits only, plus one decimal separator when [decimals] > 0.
 * "." and "," both count as the separator; the result always uses "," and keeps at most [decimals]
 * fraction digits, so [parseAmountMinor] and the visual grouping can rely on it.
 */
fun sanitizeAmountInput(text: String, decimals: Int): String {
    if (decimals == 0) return text.filter { it.isDigit() }.take(15)
    val firstSeparator = text.indexOfFirst { it == ',' || it == '.' }
    if (firstSeparator < 0) return text.filter { it.isDigit() }.take(15)
    val whole = text.substring(0, firstSeparator).filter { it.isDigit() }.take(15)
    val fraction = text.substring(firstSeparator + 1).filter { it.isDigit() }.take(decimals)
    return "$whole,$fraction"
}
