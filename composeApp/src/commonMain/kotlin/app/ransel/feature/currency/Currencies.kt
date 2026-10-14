package app.ransel.feature.currency

import org.jetbrains.compose.resources.StringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.currency_name_aud
import ransel.composeapp.generated.resources.currency_name_eur
import ransel.composeapp.generated.resources.currency_name_gbp
import ransel.composeapp.generated.resources.currency_name_idr
import ransel.composeapp.generated.resources.currency_name_jpy
import ransel.composeapp.generated.resources.currency_name_krw
import ransel.composeapp.generated.resources.currency_name_myr
import ransel.composeapp.generated.resources.currency_name_sgd
import ransel.composeapp.generated.resources.currency_name_thb
import ransel.composeapp.generated.resources.currency_name_usd

/** Currencies offered in pickers. [symbol] is the short mark shown on the tile and as the amount prefix. */
data class CurrencyOption(val code: String, val symbol: String, val name: StringResource)

val SupportedCurrencies: List<CurrencyOption> = listOf(
    CurrencyOption("IDR", "Rp", Res.string.currency_name_idr),
    CurrencyOption("USD", "$", Res.string.currency_name_usd),
    CurrencyOption("SGD", "S$", Res.string.currency_name_sgd),
    CurrencyOption("MYR", "RM", Res.string.currency_name_myr),
    CurrencyOption("JPY", "¥", Res.string.currency_name_jpy),
    CurrencyOption("THB", "฿", Res.string.currency_name_thb),
    CurrencyOption("EUR", "€", Res.string.currency_name_eur),
    CurrencyOption("AUD", "A$", Res.string.currency_name_aud),
    CurrencyOption("GBP", "£", Res.string.currency_name_gbp),
    CurrencyOption("KRW", "₩", Res.string.currency_name_krw),
)

fun currencyOption(code: String): CurrencyOption =
    SupportedCurrencies.firstOrNull { it.code == code.uppercase() }
        ?: CurrencyOption(code.uppercase(), code.uppercase(), Res.string.currency_name_idr)

fun currencySymbol(code: String): String = currencyOption(code).symbol
