package app.ransel.server

import app.ransel.server.push.LoggingPushSender
import app.ransel.server.service.NotificationService
import app.ransel.server.service.ReminderScheduler
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

/** A multipart upload as the signed-in account. */
private suspend fun Api.upload(tripId: String, id: String, bytes: ByteArray, name: String = "tiket.pdf", mime: String = "application/pdf", title: String = "Tiket pesawat CGK–DPS"): HttpResponse =
    client.submitFormWithBinaryData(
        url = "/api/v1/trips/$tripId/documents",
        formData = formData {
            append("meta", """{"id":"$id","title":"$title","category":"TRAVEL"}""", Headers.build { append(HttpHeaders.ContentType, "application/json") })
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, mime)
                append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
            })
        },
    ) { token?.let { bearerAuth(it) } }

/** Owner plus an EDITOR (e) and a VIEWER (v). Signed in as the owner. */
private suspend fun Api.docTrip(): Triple<String, Session, Pair<Session, Session>> {
    val owner = session()
    val id = UUID.randomUUID().toString()
    post("/trips", tripJson(id))
    val joined = listOf("EDITOR", "VIEWER").map { role ->
        use(owner)
        val code = post("/trips/$id/invites", """{"role":"$role","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        signUp()
        post("/invites/$code/accept")
        session()
    }
    use(owner)
    return Triple(id, owner, joined[0] to joined[1])
}

class DocumentRoutesTest {
    private val pdf = "%PDF-1.7 tiket".toByteArray()

    @Test
    fun anEditorUploadsEveryoneDownloadsWithASignedLink() = apiTest {
        val (trip, owner, members) = docTrip()
        val (editor, viewer) = members
        use(editor)
        val id = UUID.randomUUID().toString()
        val created = upload(trip, id, pdf)
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        assertEquals("tiket.pdf", created.data().jsonObject.str("fileName"))

        use(viewer)
        assertEquals(HttpStatusCode.Forbidden, upload(trip, UUID.randomUUID().toString(), pdf).status)
        val list = get("/trips/$trip/documents").data().jsonObject
        assertEquals(pdf.size.toString(), list.str("usedBytes"))
        assertEquals("FILE", list.getValue("items").jsonArray.single().jsonObject.str("kind"))
        val notified = get("/notifications").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }.single { it.str("type") == "DOCUMENT_ADDED" }
        assertEquals("Tiket pesawat CGK–DPS", notified.getValue("params").jsonObject.str("title"))

        val link = get("/trips/$trip/documents/$id/download").data().jsonObject.str("url")
        val file = client.get("/api/v1$link")
        assertEquals(HttpStatusCode.OK, file.status)
        assertContentEquals(pdf, file.bodyAsBytes())
        assertTrue(file.headers[HttpHeaders.ContentDisposition]!!.contains("tiket.pdf"))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1${link.replace(Regex("sig=[^&]+"), "sig=AAAA")}").status)

        // the viewer may not delete it; the owner may, and the link stops working
        assertEquals(HttpStatusCode.Forbidden, delete("/trips/$trip/documents/$id").status)
        use(owner)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/$trip/documents/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1$link").status)
        assertEquals("0", get("/trips/$trip/documents").data().jsonObject.str("usedBytes"))
    }

    @Test
    fun filesAreCheckedForTypeSizeAndTheTripLimit() = apiTest {
        val (trip, _, _) = docTrip()
        assertEquals("UNSUPPORTED_FILE", upload(trip, UUID.randomUUID().toString(), "halo".toByteArray(), "catatan.txt", "text/plain").errorCode())
        // a generic type with a known extension is fine
        assertEquals(HttpStatusCode.Created, upload(trip, UUID.randomUUID().toString(), byteArrayOf(1, 2, 3), "foto.jpg", "application/octet-stream").status)
        assertEquals("FILE_TOO_LARGE", upload(trip, UUID.randomUUID().toString(), ByteArray(20 * 1024 * 1024 + 1)).errorCode())

        TestDb.exec("UPDATE trip_documents SET size_bytes = 209715000 WHERE trip_id = '$trip'")
        val full = upload(trip, UUID.randomUUID().toString(), ByteArray(1024))
        assertEquals("STORAGE_FULL", full.errorCode())
        assertEquals("209715200", full.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("limitBytes"))
    }

    @Test
    fun linksAndTheStayWinnersBookingPage() = apiTest {
        val (trip, _, _) = docTrip()
        assertEquals("VALIDATION_FAILED", post("/trips/$trip/documents", """{"id":"${UUID.randomUUID()}","title":"Villa","url":"javascript:alert(1)"}""").errorCode())
        val link = post("/trips/$trip/documents", """{"id":"${UUID.randomUUID()}","title":"Voucher snorkeling","category":"ACTIVITY","url":"https://contoh.id/voucher"}""")
        assertEquals(HttpStatusCode.Created, link.status, link.bodyAsText())
        val linkId = link.data().jsonObject.str("id")
        assertEquals("https://contoh.id/voucher", get("/trips/$trip/documents/$linkId/download").data().jsonObject.str("url"))
        val renamed = patch("/trips/$trip/documents/$linkId", """{"title":"Voucher Nusa Penida","category":"OTHER"}""").data().jsonObject
        assertEquals("Voucher Nusa Penida", renamed.str("title"))

        val pollId = UUID.randomUUID().toString()
        val optionId = UUID.randomUUID().toString()
        post(
            "/trips/$trip/polls",
            """{"id":"$pollId","kind":"STAY","question":"Menginap di mana?","closesAt":"2099-01-01T00:00:00Z",""" +
                """"options":[{"id":"$optionId","label":"Villa Kayu","priceMinor":2250000,"priceUnit":"NIGHT","url":"https://booking.contoh.id/villa-kayu"}]}""",
        )
        TestDb.exec("UPDATE polls SET closes_at = now() - interval '1 minute' WHERE id = '$pollId'")
        assertEquals(HttpStatusCode.OK, post("/polls/$pollId/decide", """{"optionId":"$optionId"}""").status)
        val stay = get("/trips/$trip/documents").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }.single { it.str("category") == "STAY" }
        assertEquals("Villa Kayu", stay.str("title"))
        assertEquals("https://booking.contoh.id/villa-kayu", stay.str("url"))
    }

    @Test
    fun aTripTemplateCopiesTheItineraryByDayAndThePackingList() = apiTest {
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip, start = "2026-11-20", end = "2026-11-23"))
        post("/trips/$trip/itinerary", itemJson(day = "2026-11-22", start = "09:00", end = "10:00"))
        post("/trips/$trip/packing", """{"id":"${UUID.randomUUID()}","category":"OTHER","name":"Kompor","quantity":2}""")
        val id = UUID.randomUUID().toString()
        val created = post("/trip-templates", """{"id":"$id","name":"Bali bareng","tripId":"$trip"}""")
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val template = created.data().jsonObject
        assertEquals("4", template.str("dayCount"))
        assertEquals("2", template.getValue("items").jsonArray.single().jsonObject.str("dayIndex"))
        assertEquals("Kompor", template.getValue("packing").jsonArray.single().jsonObject.str("name"))
        assertEquals(listOf(id), get("/trip-templates").data().jsonArray.map { it.jsonObject.str("id") })

        // someone else's trip and someone else's template are not there
        signUp()
        assertEquals(HttpStatusCode.NotFound, post("/trip-templates", """{"id":"${UUID.randomUUID()}","name":"x","tripId":"$trip"}""").status)
        assertEquals(HttpStatusCode.NotFound, delete("/trip-templates/$id").status)
    }

    @Test
    fun theRecapIsAnnouncedTheMorningAfterTheTrip() = apiTest {
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip, start = "2026-11-20", end = "2026-11-23"))
        val scheduler = TestDb.config().let { ReminderScheduler(it, NotificationService(it, LoggingPushSender())) }
        fun at(date: String, time: String) = LocalDateTime.parse("${date}T$time").atZone(ZoneId.of("Asia/Jakarta")).toInstant()
        scheduler.tick(at("2026-11-23", "20:00")) // still the last day
        scheduler.tick(at("2026-11-24", "08:00")) // too early
        assertTrue(get("/notifications").data().jsonObject.getValue("items").jsonArray.none { it.jsonObject.str("type") == "RECAP_READY" })
        scheduler.tick(at("2026-11-24", "09:00"))
        scheduler.tick(at("2026-11-25", "09:00"))
        val recaps = get("/notifications").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }.filter { it.str("type") == "RECAP_READY" }
        assertEquals(1, recaps.size, "once, however many mornings pass")
    }
}
