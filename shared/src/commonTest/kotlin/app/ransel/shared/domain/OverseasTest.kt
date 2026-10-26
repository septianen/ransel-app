package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class OverseasTest {
    private val december = Instant.parse("2026-12-06T03:00:00Z")

    @Test
    fun aPlaceAbroadSetsItsCountryCurrencyAndZone() {
        assertEquals(TripLocale("JP", "JPY", "Asia/Tokyo"), localeForPlace("jp", "Asia/Tokyo"))
        assertEquals("SGD", currencyForCountry("SG"))
        assertEquals("IDR", currencyForCountry("ID"))
        assertEquals(null, currencyForCountry("XX"))
        assertTrue(isOverseas("JP"))
        assertFalse(isOverseas("ID"))
        assertFalse(isOverseas(null))
    }

    @Test
    fun theDifferenceWithWibFollowsTheZone() {
        assertEquals(120, minutesFromWib("Asia/Tokyo", december))
        assertEquals(60, minutesFromWib("Asia/Makassar", december), "Bali is WITA")
        assertEquals(0, minutesFromWib("Asia/Jakarta", december))
        assertEquals(-360, minutesFromWib("Europe/Amsterdam", december), "winter time, UTC+1 is 6 hours behind")
        assertEquals(null, minutesFromWib("Mars/Olympus", december))
    }

    @Test
    fun theLocaleIsChecked() {
        assertEquals(emptyMap(), validateTripLocale("JP", "JPY", "Asia/Tokyo"))
        assertEquals(emptyMap(), validateTripLocale(null, null, null))
        assertEquals(
            mapOf("countryCode" to "INVALID_COUNTRY", "localCurrency" to "INVALID_CURRENCY", "timeZone" to "INVALID_TIME_ZONE"),
            validateTripLocale("JPN", "yen", "Tokyo"),
        )
    }

    @Test
    fun ratesAreCrossedThroughTheEuro() {
        // 1 EUR = 162.5 JPY and 17,250 IDR, so 1 JPY = 106.15 IDR
        assertEquals(106.15, crossRate(162.5, 17_250.0), 0.01)
    }
}
