package app.ransel.shared.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Instant

/* Overseas trips (PRD "Overseas trips"): local currency and time zone follow the destination, totals stay in the base currency. */

/** The zone the app compares a trip's time with ("2 jam lebih cepat dari WIB"). */
const val HOME_TIME_ZONE = "Asia/Jakarta"

/** Country, local currency and zone of a trip, derived from its destination and editable afterwards. */
data class TripLocale(val countryCode: String?, val localCurrency: String?, val timeZone: String?)

/** What a picked place sets on the trip: its country, that country's currency and the place's zone. */
fun localeForPlace(countryCode: String?, timeZone: String?): TripLocale =
    TripLocale(countryCode?.uppercase(), currencyForCountry(countryCode), timeZone)

/** Outside Indonesia. An unknown country is home. */
fun isOverseas(countryCode: String?): Boolean = countryCode != null && !countryCode.equals("ID", ignoreCase = true)

/** Minutes [timeZone] is ahead of WIB at [at] (negative = behind); null for a zone this device does not know. */
fun minutesFromWib(timeZone: String, at: Instant): Int? = runCatching {
    (TimeZone.of(timeZone).offsetAt(at).totalSeconds - TimeZone.of(HOME_TIME_ZONE).offsetAt(at).totalSeconds) / 60
}.getOrNull()

/** Field name → reason, for the trip's country, currency and zone. */
fun validateTripLocale(countryCode: String?, localCurrency: String?, timeZone: String?): Map<String, String> = buildMap {
    if (countryCode != null && !Regex("^[A-Za-z]{2}$").matches(countryCode)) put("countryCode", "INVALID_COUNTRY")
    if (localCurrency != null && !isValidCurrency(localCurrency)) put("localCurrency", "INVALID_CURRENCY")
    if (timeZone != null && (timeZone.isBlank() || timeZone.length > 64 || runCatching { TimeZone.of(timeZone) }.isFailure)) {
        put("timeZone", "INVALID_TIME_ZONE")
    }
}

/** 1 [from] in [to] when both are quoted per euro (ECB reference rates). */
fun crossRate(fromPerEuro: Double, toPerEuro: Double): Double = toPerEuro / fromPerEuro
