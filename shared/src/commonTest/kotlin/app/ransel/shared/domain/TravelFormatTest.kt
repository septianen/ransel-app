package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TravelFormatTest {
    @Test
    fun underAKilometreIsMetresToTheNearestFifty() {
        assertEquals(TravelDistance.Meters(600), travelDistanceOf(0.6))
        assertEquals(TravelDistance.Meters(50), travelDistanceOf(0.01))
        assertEquals(TravelDistance.Meters(950), travelDistanceOf(0.96))
        // 980 m is nearer 1000 m than 950 m: that reads as 1 km
        assertEquals(TravelDistance.Km(10), travelDistanceOf(0.98))
    }

    @Test
    fun oneToTenKilometresHaveOneDecimal() {
        assertEquals(TravelDistance.Km(52), travelDistanceOf(5.2))
        assertEquals(TravelDistance.Km(10), travelDistanceOf(1.0))
        assertEquals(TravelDistance.Km(99), travelDistanceOf(9.94))
        assertEquals(TravelDistance.WholeKm(10), travelDistanceOf(9.96))
        val km = TravelDistance.Km(52)
        assertEquals(5, km.whole)
        assertEquals(2, km.decimal)
    }

    @Test
    fun tenKilometresUpAreWhole() {
        assertEquals(TravelDistance.WholeKm(18), travelDistanceOf(18.4))
        assertEquals(TravelDistance.WholeKm(150), travelDistanceOf(149.6))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals(HoursMinutes(0, 45), hoursMinutes(45))
        assertEquals(HoursMinutes(1, 0), hoursMinutes(60))
        assertEquals(HoursMinutes(1, 10), hoursMinutes(70))
    }

    private fun p(i: Int) = MapsPoint(-6.0 - i * 0.01, 107.0 + i * 0.01)

    @Test
    fun aDayOfNineStopsIsOnePart() {
        val parts = dayRouteParts((1..9).map(::p), MapsTravelMode.TWO_WHEELER)
        assertEquals(1, parts.size)
        assertEquals(1, parts.single().from)
        assertEquals(9, parts.single().to)
        assertTrue("travelmode=two-wheeler" in parts.single().url)
    }

    @Test
    fun moreThanNineStopsAreCutAndTheLastStopStartsTheNextPart() {
        val parts = dayRouteParts((1..12).map(::p), MapsTravelMode.DRIVING)
        assertEquals(2, parts.size)
        assertEquals(1 to 9, parts[0].from to parts[0].to)
        assertEquals(9 to 12, parts[1].from to parts[1].to)
    }

    @Test
    fun aMobileBrowserTakesThreeStopsPerPart() {
        val parts = dayRouteParts((1..5).map(::p), MapsTravelMode.TRANSIT, DAY_ROUTE_MAX_POINTS_MOBILE_BROWSER)
        assertEquals(listOf(1 to 3, 3 to 5), parts.map { it.from to it.to })
    }

    @Test
    fun oneStopOrTheSamePlaceTwiceHasNoRoute() {
        assertEquals(emptyList(), dayRouteParts(listOf(p(1)), MapsTravelMode.DRIVING))
        assertEquals(emptyList(), dayRouteParts(listOf(p(1), p(1)), MapsTravelMode.DRIVING))
    }
}
