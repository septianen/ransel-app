package app.ransel.server

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.takeUnless { it.content == "null" && !it.isString }?.content

private fun image(width: Int, height: Int, format: String = "jpg"): ByteArray {
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    img.createGraphics().apply { color = Color(0x2E7D6B); fillRect(0, 0, width, height); dispose() }
    return ByteArrayOutputStream().also { ImageIO.write(img, format, it) }.toByteArray()
}

private suspend fun Api.upload(path: String, bytes: ByteArray, type: ContentType = ContentType.Image.JPEG): HttpResponse =
    client.put("/api/v1$path") {
        token?.let { bearerAuth(it) }
        contentType(type)
        setBody(bytes)
    }

private fun fileOf(url: String): Path = Path.of(TestDb.documentsDir, "images", url.substringAfterLast('/') + ".jpg")

class PhotoRoutesTest {
    @Test
    fun editorsChangeTheCoverViewersCannotAndTheFileIsShrunk() = apiTest {
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
        assertEquals(HttpStatusCode.Forbidden, upload("/trips/$trip/cover", image(100, 50)).status)

        use(editor)
        val set = upload("/trips/$trip/cover", image(3000, 2000))
        assertEquals(HttpStatusCode.OK, set.status)
        val url = set.data().jsonObject.str("coverUrl")!!
        assertTrue(url.startsWith("/files/images/"))

        // public, cached, and at most 1280 px on the long side
        val file = client.get("/api/v1$url")
        assertEquals(HttpStatusCode.OK, file.status)
        assertTrue(file.headers[HttpHeaders.CacheControl]!!.contains("immutable"))
        val stored = ImageIO.read(ByteArrayInputStream(file.bodyAsBytes()))
        assertEquals(1280 to 853, stored.width to stored.height)

        // replacing removes the old file; an old app saving the trip with coverUrl = null keeps the cover
        val second = upload("/trips/$trip/cover", image(800, 600)).data().jsonObject.str("coverUrl")!!
        assertFalse(Files.exists(fileOf(url)))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1$url").status)
        use(owner)
        put("/trips/$trip", """{"title":"Bali","destination":"Bali","startDate":"2026-11-20","endDate":"2026-11-23","baseCurrency":"IDR","coverUrl":null}""")
        assertEquals(second, get("/trips/$trip").data().jsonObject.getValue("trip").jsonObject.str("coverUrl"))

        val cleared = delete("/trips/$trip/cover").data().jsonObject
        assertNull(cleared.str("coverUrl"))
        assertFalse(Files.exists(fileOf(second)))
    }

    @Test
    fun onlySmallRealJpegsAreTaken() = apiTest {
        assertEquals(HttpStatusCode.UnsupportedMediaType, upload("/me/avatar", image(100, 100, "png"), ContentType.Image.PNG).status)
        // a PNG sent as JPEG, and something that is no image at all
        assertEquals("UNSUPPORTED_FILE", upload("/me/avatar", image(100, 100, "png")).errorCode())
        assertEquals("UNSUPPORTED_FILE", upload("/me/avatar", "not a photo".toByteArray()).errorCode())
        val big = upload("/me/avatar", ByteArray(1_000_001) { 1 })
        assertEquals(HttpStatusCode.PayloadTooLarge, big.status)
        assertEquals("FILE_TOO_LARGE", big.errorCode())
        assertNull(get("/me").data().jsonObject.str("avatarUrl"))
    }

    @Test
    fun theProfilePhotoShowsOnMeMembersSyncAndTheGuestPage() = apiTest {
        val owner = session()
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip))
        val code = post("/trips/$trip/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")!!
        signUp()
        post("/invites/$code/accept")
        val viewer = session()
        val since = get("/sync").data().jsonObject.str("serverTime")!!

        use(owner)
        val me = upload("/me/avatar", image(1000, 1000)).data().jsonObject
        val url = me.str("avatarUrl")!!
        val stored = ImageIO.read(ByteArrayInputStream(client.get("/api/v1$url").bodyAsBytes()))
        assertEquals(320, stored.width)
        assertEquals(url, get("/me").data().jsonObject.str("avatarUrl"))

        val members = get("/trips/$trip/members").data().jsonArray.map { it.jsonObject }
        assertEquals(url, members.single { it.str("userId") == owner.userId }.str("avatarUrl"))

        // the other member's next pull brings the new photo
        use(viewer)
        val synced = get("/sync?since=$since").data().jsonObject.getValue("changes").jsonObject.getValue("members").jsonArray.map { it.jsonObject }
        assertEquals(url, synced.single { it.str("userId") == owner.userId }.str("avatarUrl"))

        val page = client.get("/api/v1/guest/$code").data().jsonObject
        assertEquals(url, page.getValue("members").jsonArray.map { it.jsonObject }.single { it.str("hasAccount") == "true" && it.str("avatarUrl") != null }.str("avatarUrl"))

        use(owner)
        assertNull(delete("/me/avatar").data().jsonObject.str("avatarUrl"))
        assertFalse(Files.exists(fileOf(url)))
    }
}
