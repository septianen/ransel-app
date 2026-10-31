package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotosTest {
    @Test
    fun aLargePhotoShrinksToTheLongestSideAndKeepsItsShape() {
        assertEquals(1280 to 960, fitWithin(4032, 3024, PhotoKind.COVER.maxSide))
        assertEquals(240 to 320, fitWithin(3024, 4032, PhotoKind.AVATAR.maxSide))
        assertEquals(1280 to 1, fitWithin(10000, 2, 1280))
    }

    @Test
    fun aSmallPhotoKeepsItsSize() {
        assertEquals(800 to 600, fitWithin(800, 600, PhotoKind.COVER.maxSide))
        assertEquals(320 to 320, fitWithin(320, 320, PhotoKind.AVATAR.maxSide))
    }
}
