package app.ransel.shared.domain

import app.ransel.shared.dto.TripOriginDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TravelCitiesTest {
    private fun origin(name: String) = TripOriginDto(name)

    @Test
    fun theTrainNeedsAStationAtBothEnds() {
        assertTrue(interviewCityFacts(origin("Subang"), "Bandung").trainBetweenCities)
        assertTrue(interviewCityFacts(origin("Jakarta"), "Yogyakarta").trainBetweenCities)
        assertFalse(interviewCityFacts(origin("Subang"), "Labuan Bajo").trainBetweenCities)
        assertFalse(interviewCityFacts(null, "Bandung").trainBetweenCities, "no origin")
    }

    @Test
    fun namesAreMatchedAsWholeWordsIgnoringCaseAndPrefixes() {
        assertTrue(cityIn(BUNDLED_CITY_LISTS.stationCities, "Kota Bandung"))
        assertTrue(cityIn(BUNDLED_CITY_LISTS.stationCities, "BANDUNG, Jawa Barat"))
        assertTrue(cityIn(BUNDLED_CITY_LISTS.stationCities, "Kabupaten Bandung Barat"), "Bandung is a word of it")
        assertFalse(cityIn(BUNDLED_CITY_LISTS.stationCities, "Bandungan"), "not a whole word")
        assertFalse(cityIn(BUNDLED_CITY_LISTS.stationCities, null))
        assertFalse(cityIn(BUNDLED_CITY_LISTS.stationCities, " "))
    }

    @Test
    fun theRentalCitiesAreBaliYogyakartaAndLombok() {
        listOf("Bali", "Ubud", "Denpasar", "Yogyakarta", "Daerah Istimewa Yogyakarta", "Lombok", "Mataram").forEach {
            assertTrue(interviewCityFacts(null, it).motorcycleRentalCity, it)
        }
        listOf("Bandung", "Jakarta", "Tokyo", "Labuan Bajo").forEach { assertFalse(interviewCityFacts(null, it).motorcycleRentalCity, it) }
    }

    @Test
    fun onlyJakartaHasPublicTransportOnTheMap() {
        assertTrue(interviewCityFacts(null, "Jakarta").publicTransportCity)
        assertTrue(interviewCityFacts(null, "Daerah Khusus Ibukota Jakarta").publicTransportCity)
        assertFalse(interviewCityFacts(null, "Yogyakarta").publicTransportCity)
        assertFalse(interviewCityFacts(null, "Bali").publicTransportCity)
    }

    @Test
    fun karungListsCanReplaceTheBundledOnes() {
        val lists = CityLists(setOf("kupang"), emptySet(), setOf("kupang"))
        val facts = interviewCityFacts(origin("Kupang"), "Kupang", lists)
        assertTrue(facts.trainBetweenCities && facts.publicTransportCity)
        assertFalse(interviewCityFacts(origin("Kupang"), "Jakarta", lists).publicTransportCity)
    }
}
