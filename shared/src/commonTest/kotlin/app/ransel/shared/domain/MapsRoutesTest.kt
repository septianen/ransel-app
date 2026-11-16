package app.ransel.shared.domain

import app.ransel.shared.model.MainTravelMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapsRoutesTest {
    private val a = MapsPoint(-6.9175, 107.6191)
    private val b = MapsPoint(-6.8, 107.7)
    private fun spread(n: Int) = List(n) { MapsPoint(-6.0 - it * 0.01, 107.0 + it * 0.01) }

    @Test
    fun theUrlHasOriginDestinationAndTheTravelMode() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=-6.9175%2C107.6191&destination=-6.8%2C107.7&travelmode=two-wheeler",
            mapsDirectionsUrl(listOf(a, b), MapsTravelMode.TWO_WHEELER),
        )
    }

    @Test
    fun theStopsInBetweenAreWaypointsSeparatedByAnEncodedPipe() {
        val url = mapsDirectionsUrl(listOf(a, MapsPoint(-6.85, 107.65), MapsPoint(-6.83, 107.66), b), MapsTravelMode.DRIVING)!!
        assertTrue(url.endsWith("&travelmode=driving&waypoints=-6.85%2C107.65%7C-6.83%2C107.66"), url)
    }

    @Test
    fun everyTravelModeUsesGooglesName() {
        assertEquals(
            listOf("driving", "walking", "bicycling", "two-wheeler", "transit"),
            MapsTravelMode.entries.map { it.parameter },
        )
        listOf(MapsTravelMode.TWO_WHEELER, MapsTravelMode.TRANSIT, MapsTravelMode.WALKING).forEach {
            assertTrue("travelmode=${it.parameter}" in mapsDirectionsUrl(listOf(a, b), it)!!)
        }
    }

    @Test
    fun theUrlNeverUsesNavigateAndNeverHasAKey() {
        val url = mapsDirectionsUrl(listOf(a, b), MapsTravelMode.DRIVING)!!
        assertTrue("dir_action" !in url && "key=" !in url)
    }

    @Test
    fun oneUrlTakesNineWaypointsAndNoMore() {
        assertTrue(mapsDirectionsUrl(spread(11), MapsTravelMode.DRIVING) != null)
        assertNull(mapsDirectionsUrl(spread(12), MapsTravelMode.DRIVING))
        assertNull(mapsDirectionsUrl(spread(1), MapsTravelMode.DRIVING))
        assertNull(mapsDirectionsUrl(emptyList(), MapsTravelMode.DRIVING))
    }

    @Test
    fun coordinatesAreFixedDecimalsNeverScientific() {
        val url = mapsDirectionsUrl(listOf(MapsPoint(0.0, 0.000001), MapsPoint(-0.0000001, 100.0)), MapsTravelMode.WALKING)!!
        assertTrue("origin=0%2C0.000001" in url, url)
        assertTrue("destination=0%2C100" in url, url)
        val long = mapsDirectionsUrl(listOf(MapsPoint(-6.123456789, 107.987654321), b), MapsTravelMode.WALKING)!!
        assertTrue("origin=-6.123457%2C107.987654" in long, long)
    }

    // ── a day ───────────────────────────────────────────────────────────────

    @Test
    fun aDayWithinTheLimitIsOneUrl() {
        assertEquals(1, mapsDayRouteUrls(spread(11), MapsTravelMode.DRIVING).size)
        assertEquals(1, mapsDayRouteUrls(spread(2), MapsTravelMode.DRIVING).size)
    }

    @Test
    fun aLongDayIsCutAndEachUrlStartsWhereTheLastEnded() {
        val points = spread(12)
        val urls = mapsDayRouteUrls(points, MapsTravelMode.DRIVING)
        assertEquals(2, urls.size)
        assertTrue("destination=${decimalPair(points[10])}" in urls[0])
        assertTrue("origin=${decimalPair(points[10])}" in urls[1])
        assertTrue("destination=${decimalPair(points[11])}" in urls[1])
    }

    @Test
    fun aMobileBrowserGetsThreeWaypointsPerUrl() {
        val points = spread(9) // 5 points per URL: 3 waypoints between an origin and a destination
        val urls = mapsDayRouteUrls(points, MapsTravelMode.TRANSIT, MAPS_MAX_WAYPOINTS_MOBILE_BROWSER)
        assertEquals(2, urls.size)
        urls.forEach { url -> assertTrue(url.substringAfter("waypoints=", "").split("%7C").size <= 3, url) }
        assertTrue("origin=${decimalPair(points[4])}" in urls[1], "the second URL starts at the fifth point")
        assertTrue("destination=${decimalPair(points[8])}" in urls[1])
    }

    @Test
    fun everyPointIsVisitedOnceInOrderAcrossTheUrls() {
        val points = spread(25)
        val urls = mapsDayRouteUrls(points, MapsTravelMode.DRIVING, 3)
        val visited = urls.flatMapIndexed { index, url ->
            val parts = listOf(url.substringAfter("origin=").substringBefore("&")) +
                url.substringAfter("waypoints=", "").takeIf { it.isNotEmpty() }?.split("%7C").orEmpty() +
                url.substringAfter("destination=").substringBefore("&")
            if (index == 0) parts else parts.drop(1)
        }
        assertEquals(points.map(::decimalPair), visited)
    }

    @Test
    fun noUrlIsLongerThanGoogleAllows() {
        mapsDayRouteUrls(spread(80), MapsTravelMode.TWO_WHEELER).forEach { assertTrue(it.length <= MAPS_MAX_URL_LENGTH, "${it.length}") }
    }

    @Test
    fun samePlaceTwiceInARowIsOnePointAndOnePointIsNoRoute() {
        assertEquals(emptyList(), mapsDayRouteUrls(listOf(a, a, a), MapsTravelMode.DRIVING))
        assertEquals(emptyList(), mapsDayRouteUrls(listOf(a), MapsTravelMode.DRIVING))
        val urls = mapsDayRouteUrls(listOf(a, a, b), MapsTravelMode.DRIVING)
        assertEquals(1, urls.size)
        assertTrue("waypoints" !in urls[0])
    }

    @Test
    fun theMainModeMapsToGoogleModes() {
        assertEquals(MapsTravelMode.TWO_WHEELER, MainTravelMode.MOTORCYCLE.mapsMode())
        assertEquals(MapsTravelMode.DRIVING, MainTravelMode.CAR.mapsMode())
        assertEquals(MapsTravelMode.TRANSIT, MainTravelMode.PUBLIC_TRANSPORT.mapsMode())
        assertEquals(MapsTravelMode.WALKING, LegKind.WALK.mapsMode(MainTravelMode.CAR))
        assertEquals(MapsTravelMode.TWO_WHEELER, LegKind.FAR.mapsMode(MainTravelMode.MOTORCYCLE))
        assertEquals(MapsTravelMode.TRANSIT, LegKind.PUBLIC_TRANSPORT.mapsMode(MainTravelMode.MOTORCYCLE))
    }

    /** The "lat,lng" of a point as the URL writes it. */
    private fun decimalPair(p: MapsPoint): String {
        val url = mapsDirectionsUrl(listOf(p, MapsPoint(p.lat + 1, p.lng)), MapsTravelMode.DRIVING)!!
        return url.substringAfter("origin=").substringBefore("&")
    }

    @Test
    fun withoutAModeMapsPicksOne() {
        val url = mapsDirectionsUrl(listOf(MapsPoint(-6.9, 107.6), MapsPoint(-7.3, 112.7)), null)!!
        assertTrue("travelmode" !in url)
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1&origin="))
    }
}
