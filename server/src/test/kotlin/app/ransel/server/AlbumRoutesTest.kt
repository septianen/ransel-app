package app.ransel.server

import app.ransel.server.drive.DriveFolders
import app.ransel.server.drive.DriveImage
import app.ransel.server.drive.DrivePage
import app.ransel.server.drive.unavailable
import app.ransel.server.plugins.ApiException
import app.ransel.shared.api.ErrorCode
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.takeUnless { !it.isString && it.content == "null" }?.content

private const val PUBLIC = "1PublicFolderAbcdefghijk"
private const val PRIVATE = "1PrivateFolderAbcdefghij"
private const val BROKEN = "1BrokenFolderAbcdefghijk"
private const val OLD = "1OldShareFolderAbcdefghi"

/** Drive with one public folder of 3 photos in two pages, one private folder and one Drive cannot reach. */
private class FakeDrive : DriveFolders {
    val listings = AtomicInteger()
    var seenResourceKey: String? = null

    override suspend fun requireFolder(folderId: String, resourceKey: String?) {
        if (folderId == PRIVATE) throw ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.ALBUM_NOT_PUBLIC, "private")
    }

    override suspend fun images(folderId: String, resourceKey: String?, pageToken: String?): DrivePage {
        listings.incrementAndGet()
        seenResourceKey = resourceKey
        return when {
            folderId == BROKEN -> throw unavailable("quota")
            folderId == PRIVATE -> throw ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.ALBUM_NOT_PUBLIC, "private")
            pageToken == null -> DrivePage(listOf(DriveImage("img1", "pantai.jpg", 4000, 3000), DriveImage("img2", "tegak.jpg", 4000, 3000, rotation = 1)), "next")
            else -> DrivePage(listOf(DriveImage("img3", "sunset.heic", null, null)), null)
        }
    }
}

private fun folder(id: String) = "https://drive.google.com/drive/folders/$id?usp=sharing"

private suspend fun Api.addAlbum(trip: String, url: String, title: String? = null, id: String = UUID.randomUUID().toString()) =
    post("/trips/$trip/albums", """{"id":"$id","url":"$url"${title?.let { ""","title":"$it"""" }.orEmpty()}}""")

class AlbumRoutesTest {
    @Test
    fun editorsAddAlbumsViewersOnlyLookAndOutsideLinksNeedNoDrive() = apiTest(drive = FakeDrive()) {
        val owner = session()
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip))
        val (editor, viewer) = listOf("EDITOR", "VIEWER").map { role ->
            use(owner)
            val code = post("/trips/$trip/invites", """{"role":"$role","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
            signUp()
            post("/invites/$code/accept")
            session()
        }

        use(viewer)
        assertEquals(HttpStatusCode.Forbidden, addAlbum(trip, folder(PUBLIC)).status)

        use(editor)
        val drive = addAlbum(trip, folder(PUBLIC), "Foto Bali")
        assertEquals(HttpStatusCode.Created, drive.status)
        assertEquals("DRIVE_FOLDER", drive.data().jsonObject.str("provider"))
        val photosId = UUID.randomUUID().toString()
        val photos = addAlbum(trip, "https://photos.app.goo.gl/AbCdEf", id = photosId).data().jsonObject
        assertEquals("GOOGLE_PHOTOS", photos.str("provider"))
        assertNull(photos.str("title"))
        assertEquals(photosId, addAlbum(trip, "https://photos.app.goo.gl/AbCdEf", id = photosId).data().jsonObject.str("id"), "a retried POST gives the same album")

        assertEquals("ALBUM_NOT_PUBLIC", addAlbum(trip, folder(PRIVATE)).errorCode())
        assertEquals("VALIDATION_FAILED", addAlbum(trip, "bukan link").errorCode())

        use(viewer)
        val list = get("/trips/$trip/albums").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(listOf("GOOGLE_PHOTOS", "DRIVE_FOLDER"), list.map { it.str("provider") }, "newest first, the private folder was not saved")
        assertEquals(HttpStatusCode.Forbidden, delete("/trips/$trip/albums/$photosId").status, "only who added it, or the owner")

        use(owner)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/$trip/albums/$photosId").status)
        assertEquals(1, get("/trips/$trip/albums").data().jsonObject.getValue("items").jsonArray.size)

        signUp()
        assertEquals(HttpStatusCode.NotFound, get("/trips/$trip/albums").status, "not a member")
    }

    @Test
    fun aDriveFolderListsItsPhotosPageByPageFromTheCache() = apiTest(drive = FakeDrive().also { fake = it }) {
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip))
        val album = addAlbum(trip, folder(PUBLIC)).data().jsonObject.str("id")
        val outside = addAlbum(trip, "https://www.icloud.com/sharedalbum/#B0abc").data().jsonObject.str("id")

        val first = get("/trips/$trip/albums/$album/items").data().jsonObject
        val items = first.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(listOf("img1", "img2"), items.map { it.str("id") })
        assertEquals("https://lh3.googleusercontent.com/d/img1=w400", items[0].str("thumbnailUrl"))
        assertEquals("https://lh3.googleusercontent.com/d/img1=w2048", items[0].str("imageUrl"))
        assertEquals(listOf("3000", "4000"), listOf(items[1].str("width"), items[1].str("height")), "a quarter turn swaps the sides")
        assertEquals("next", first.str("nextPageToken"))

        val second = get("/trips/$trip/albums/$album/items?pageToken=next").data().jsonObject
        assertEquals(listOf("img3"), second.getValue("items").jsonArray.map { it.jsonObject.str("id") })
        assertNull(second.str("nextPageToken"))

        get("/trips/$trip/albums/$album/items")
        assertEquals(2, fake.listings.get(), "the first page came from the cache")

        assertEquals("ALBUM_NOT_FOUND", get("/trips/$trip/albums/$outside/items").errorCode(), "an outside link has no gallery")
    }

    @Test
    fun drivePrivateOrUnreachableAndAnOldShareLink() = apiTest(drive = FakeDrive().also { fake = it }) {
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip))
        val broken = addAlbum(trip, folder(BROKEN)).data().jsonObject.str("id")
        val unavailable = get("/trips/$trip/albums/$broken/items")
        assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
        assertEquals("ALBUM_UNAVAILABLE", unavailable.errorCode())

        // a folder made private after it was added
        val later = addAlbum(trip, folder(PUBLIC)).data().jsonObject.str("id")
        TestDb.exec("UPDATE trip_albums SET drive_folder_id = '$PRIVATE' WHERE id = '$later'")
        assertEquals("ALBUM_NOT_PUBLIC", get("/trips/$trip/albums/$later/items").errorCode())

        val old = addAlbum(trip, "https://drive.google.com/drive/folders/$OLD?resourcekey=0-AbC").data().jsonObject.str("id")
        get("/trips/$trip/albums/$old/items")
        assertEquals("0-AbC", fake.seenResourceKey)
    }

    @Test
    fun theGuestPageShowsTheAlbumsAndTheirPhotos() = apiTest(drive = FakeDrive()) {
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip))
        val album = addAlbum(trip, folder(PUBLIC), "Foto Bali").data().jsonObject.str("id")
        val code = post("/trips/$trip/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")

        token = null
        val page = get("/guest/$code").data().jsonObject
        assertEquals(listOf("Foto Bali"), page.getValue("albums").jsonArray.map { it.jsonObject.str("title") })
        val items = client.get("/api/v1/guest/$code/albums/$album/items")
        assertEquals(HttpStatusCode.OK, items.status)
        assertTrue(items.data().jsonObject.getValue("items").jsonArray.isNotEmpty())

        assertEquals(HttpStatusCode.NotFound, get("/guest/NOPE-12345678/albums/$album/items").status)
        val otherTrip = UUID.randomUUID().toString()
        assertEquals("ALBUM_NOT_FOUND", get("/guest/$code/albums/$otherTrip/items").errorCode(), "an album of another trip")
    }

    private companion object {
        lateinit var fake: FakeDrive
    }
}
