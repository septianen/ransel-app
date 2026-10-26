package app.ransel.shared.domain

import app.ransel.shared.dto.MatchRange
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.TripPlaceDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlacesTest {
    @Test
    fun theBuiltInListHasEveryProvinceCityAndRegency() {
        assertEquals(38, INDONESIAN_REGIONS.count { it.kind == RegionKind.PROVINCE })
        assertEquals(514, INDONESIAN_REGIONS.count { it.kind != RegionKind.PROVINCE })
        assertTrue(INDONESIAN_REGIONS.any { it.name == "Manggarai Barat" && it.province == "Nusa Tenggara Timur" })
    }

    @Test
    fun namesStartingWithTheQueryComeFirstAndCitiesBeforeRegencies() {
        val found = searchRegions("lab")
        assertEquals("Labuhanbatu", found.first().name)
        assertTrue(found.all { normalizeSearch(it.name).contains("lab") })
        val bandung = searchRegions("bandung")
        assertEquals(RegionKind.CITY, bandung.first().kind, "Kota Bandung before Kabupaten Bandung")
        assertEquals(listOf("Bandung", "Bandung", "Bandung Barat"), bandung.map { it.name })
    }

    @Test
    fun aQueryNeedsTwoCharactersAndIgnoresCaseAndPunctuation() {
        assertTrue(searchRegions("b").isEmpty())
        assertEquals(searchRegions("Kota Baru").map { it.name }, searchRegions("  kota-BARU ").map { it.name })
    }

    @Test
    fun fallbackSuggestionsCarryTheProvinceAndTheMatch() {
        val bandung = regionSuggestions("bandung").first()
        assertEquals(null, bandung.placeId, "no coordinates: the name is used as typed")
        assertEquals("Jawa Barat", bandung.secondary)
        val yogya = regionSuggestions("yogya").first()
        assertEquals(PlaceKind.CITY, yogya.kind)
        assertEquals(listOf(MatchRange(0, 5)), yogya.matchRanges)
    }

    @Test
    fun distanceDecidesWhetherTheCurrentLocationIsNearTheDestination() {
        // Jimbaran to Denpasar is about 12 km, Jakarta to Bali about 980 km
        assertTrue(withinKm(-8.790, 115.160, -8.650, 115.216, NEAR_DESTINATION_KM))
        assertFalse(withinKm(-6.200, 106.816, -8.650, 115.216, NEAR_DESTINATION_KM))
    }

    @Test
    fun aTripPlaceNeedsAnIdAndRealCoordinates() {
        assertEquals(emptyMap(), validateTripPlace(null))
        assertEquals(emptyMap(), validateTripPlace(TripPlaceDto("ChIJ", -8.65, 115.2)))
        assertEquals(mapOf("destinationPlace" to "INVALID_COORDINATES"), validateTripPlace(TripPlaceDto("ChIJ", 120.0, 0.0)))
        assertEquals(mapOf("destinationPlace" to "INVALID_PLACE_ID"), validateTripPlace(TripPlaceDto(" ", 0.0, 0.0)))
    }
}
