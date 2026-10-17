package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceLinksTest {
    @Test
    fun plainNameKeepsUnreservedCharacters() {
        assertEquals("Tegenungan-1_2.3~", percentEncode("Tegenungan-1_2.3~"))
    }

    @Test
    fun spacesAndSymbolsArePercentEncoded() {
        assertEquals("Air%20Terjun%20%26%20Kolam%3F", percentEncode("Air Terjun & Kolam?"))
    }

    @Test
    fun nonAsciiUsesUtf8Bytes() {
        assertEquals("%C3%A9", percentEncode("é"))
        assertEquals("%E6%9D%B1%E4%BA%AC", percentEncode("東京"))
    }

    @Test
    fun mapsUrlCarriesNameAndPlaceId() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Pantai%20Melasti&query_place_id=ChIJabc_123",
            googleMapsPlaceUrl("Pantai Melasti", "ChIJabc_123"),
        )
    }
}
