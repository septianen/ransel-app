package app.ransel.shared.domain

import app.ransel.shared.model.AlbumProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumLinksTest {
    private val id = "1AbCdEfGhIjKlMnOpQrStUvWxYz012345"

    @Test
    fun driveFolderLinksGiveTheFolderId() {
        val expected = AlbumLink(AlbumProvider.DRIVE_FOLDER, id)
        assertEquals(expected, parseAlbumLink("https://drive.google.com/drive/folders/$id"))
        assertEquals(expected, parseAlbumLink("https://drive.google.com/drive/folders/$id?usp=sharing"))
        assertEquals(expected, parseAlbumLink("  https://drive.google.com/drive/u/1/folders/$id/  "))
        assertEquals(expected, parseAlbumLink("https://drive.google.com/open?id=$id"))
        assertEquals(expected, parseAlbumLink("https://drive.google.com/folderview?id=$id&usp=sharing"))
        assertEquals(expected, parseAlbumLink("HTTPS://www.drive.google.com/drive/folders/$id#grid"))
    }

    @Test
    fun anOlderShareLinkKeepsItsResourceKey() {
        assertEquals(
            AlbumLink(AlbumProvider.DRIVE_FOLDER, id, "0-AbC_dEf"),
            parseAlbumLink("https://drive.google.com/drive/folders/$id?resourcekey=0-AbC_dEf&usp=sharing"),
        )
        assertEquals(
            AlbumLink(AlbumProvider.DRIVE_FOLDER, id),
            parseAlbumLink("https://drive.google.com/drive/folders/$id?resourcekey=bad%20key"),
            "a key with odd characters is dropped",
        )
    }

    @Test
    fun otherLinksOpenOutside() {
        assertEquals(AlbumProvider.GOOGLE_PHOTOS, parseAlbumLink("https://photos.app.goo.gl/AbCdEf123")?.provider)
        assertEquals(AlbumProvider.GOOGLE_PHOTOS, parseAlbumLink("https://photos.google.com/share/AF1Qip?key=x")?.provider)
        assertEquals(AlbumProvider.GOOGLE_PHOTOS, parseAlbumLink("https://goo.gl/photos/AbCdEf")?.provider)
        assertEquals(AlbumProvider.OTHER, parseAlbumLink("https://www.icloud.com/sharedalbum/#B0abc")?.provider)
        assertEquals(AlbumProvider.OTHER, parseAlbumLink("https://drive.google.com/file/d/$id/view")?.provider, "a single file is no gallery")
        assertEquals(AlbumProvider.OTHER, parseAlbumLink("https://drive.google.com/drive/folders/short")?.provider)
        assertEquals(AlbumProvider.OTHER, parseAlbumLink("https://goo.gl/maps/xyz")?.provider)
    }

    @Test
    fun anythingButAWebLinkIsRefused() {
        assertNull(parseAlbumLink(""))
        assertNull(parseAlbumLink("drive.google.com/drive/folders/$id"))
        assertNull(parseAlbumLink("ftp://example.com/album"))
        assertNull(parseAlbumLink("https://"))
    }

    @Test
    fun validationNamesTheBadFields() {
        assertEquals(emptyMap(), validateAlbum("https://drive.google.com/drive/folders/$id", "Foto Bali"))
        assertEquals(emptyMap(), validateAlbum("https://photos.app.goo.gl/x", null))
        assertEquals(mapOf("url" to "INVALID"), validateAlbum("bukan link", null))
        assertEquals(mapOf("url" to "INVALID"), validateAlbum("https://example.com/" + "a".repeat(ALBUM_URL_MAX), null))
        assertEquals(mapOf("title" to "TOO_LONG"), validateAlbum("https://example.com", "x".repeat(ALBUM_TITLE_MAX + 1)))
    }
}
