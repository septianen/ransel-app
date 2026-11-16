package app.ransel.server

import app.ransel.shared.dto.SyncChanges
import app.ransel.shared.dto.SyncPullResponse
import app.ransel.shared.dto.SyncPushRequest
import app.ransel.shared.dto.SyncPushResponse
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.MainTravelMode
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val wire = Json { encodeDefaults = true; ignoreUnknownKeys = true }

private fun JsonObject.str(key: String) = this[key]?.takeIf { it.toString() != "null" }?.jsonPrimitive?.content

private const val ANSWERS = """"origin":{"name":"Subang","lat":-6.5694,"lng":107.7606,"countryCode":"id"},"departureMode":"OWN_MOTORCYCLE","localTransport":"RIDE_HAILING","mainTravelMode":"MOTORCYCLE""""

/** The "Buat trip" interview's answers on a trip (V8): REST create, update and read, and sync push and pull. */
class TripInterviewRoutesTest {
    @Test
    fun theAnswersAreStoredWithTheTripAndComeBack() = apiTest {
        val id = UUID.randomUUID().toString()
        val created = post("/trips", tripJson(id).dropLast(1) + ",$ANSWERS}")
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val trip = get("/trips/$id").data().jsonObject.getValue("trip").jsonObject
        val origin = trip.getValue("origin").jsonObject
        assertEquals("Subang", origin.str("name"))
        assertEquals("ID", origin.str("countryCode"), "upper-cased")
        assertEquals("OWN_MOTORCYCLE", trip.str("departureMode"))
        assertEquals("RIDE_HAILING", trip.str("localTransport"))
        assertEquals("MOTORCYCLE", trip.str("mainTravelMode"))
    }

    @Test
    fun aTripWithoutAnswersHasNoneOfThem() = apiTest {
        val id = UUID.randomUUID().toString()
        post("/trips", tripJson(id))
        val trip = get("/trips/$id").data().jsonObject.getValue("trip").jsonObject
        assertNull(trip.str("origin"))
        assertNull(trip.str("departureMode"))
        assertNull(trip.str("localTransport"))
        assertNull(trip.str("mainTravelMode"))
    }

    @Test
    fun anUpdateWithoutThemKeepsThemAndOneWithThemReplacesThem() = apiTest {
        val id = UUID.randomUUID().toString()
        post("/trips", tripJson(id).dropLast(1) + ",$ANSWERS}")

        // an older app edits the title and sends none of the answers
        put("/trips/$id", tripJson(id, title = "Ganti judul").replace(""""id":"$id",""", ""))
        val kept = get("/trips/$id").data().jsonObject.getValue("trip").jsonObject
        assertEquals("Ganti judul", kept.str("title"))
        assertEquals("OWN_MOTORCYCLE", kept.str("departureMode"))
        assertEquals("Subang", kept.getValue("origin").jsonObject.str("name"))

        // a new answer replaces the old one, the others stay
        val body = tripJson(id).replace(""""id":"$id",""", "").dropLast(1) +
            ""","origin":{"name":"Jakarta","lat":-6.2088,"lng":106.8456},"departureMode":"TRAIN"}"""
        assertEquals(HttpStatusCode.OK, put("/trips/$id", body).status)
        val changed = get("/trips/$id").data().jsonObject.getValue("trip").jsonObject
        assertEquals("Jakarta", changed.getValue("origin").jsonObject.str("name"))
        assertNull(changed.getValue("origin").jsonObject.str("placeId"))
        assertEquals("TRAIN", changed.str("departureMode"))
        assertEquals("RIDE_HAILING", changed.str("localTransport"))
        assertEquals("MOTORCYCLE", changed.str("mainTravelMode"))
    }

    @Test
    fun anOriginNeedsANameAndAPointInRange() = apiTest {
        val noName = post("/trips", tripJson().dropLast(1) + ""","origin":{"name":" ","lat":1.0,"lng":2.0}}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, noName.status)
        val farOff = post("/trips", tripJson().dropLast(1) + ""","origin":{"name":"Sini","lat":95.0,"lng":2.0}}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, farOff.status)
        val badCountry = post("/trips", tripJson().dropLast(1) + ""","origin":{"name":"Sini","lat":1.0,"lng":2.0,"countryCode":"IDN"}}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, badCountry.status)
    }

    @Test
    fun anUnknownAnswerIsRefusedAtTheDoor() = apiTest {
        val response = post("/trips", tripJson().dropLast(1) + ""","departureMode":"ROCKET"}""")
        assertTrue(response.status.value in 400..422, "refused, not stored and not a server error: ${response.status}")
    }

    @Test
    fun syncCarriesTheAnswersBothWaysAndKeepsThemWhenAnOldAppPushes() = apiTest {
        val id = UUID.randomUUID().toString()
        val now = Clock.System.now()
        fun trip(updatedAt: Instant, withAnswers: Boolean, title: String) = TripDto(
            id = id, ownerId = userId, title = title, destination = "Bandung",
            startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 22), baseCurrency = "IDR",
            origin = if (withAnswers) TripOriginDto("Subang", -6.5694, 107.7606, countryCode = "ID") else null,
            departureMode = if (withAnswers) DepartureMode.OWN_MOTORCYCLE else null,
            localTransport = if (withAnswers) LocalTransport.UNKNOWN else null,
            mainTravelMode = if (withAnswers) MainTravelMode.MOTORCYCLE else null,
            createdAt = now, updatedAt = updatedAt,
        )
        suspend fun push(dto: TripDto) {
            val response = post("/sync", wire.encodeToString(SyncPushRequest(SyncChanges(trips = listOf(dto)))))
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            val result = wire.decodeFromString<app.ransel.shared.api.ApiResponse<SyncPushResponse>>(response.bodyAsText()).data
            assertEquals(emptyList(), result.rejected.map { it.id })
        }
        push(trip(now, withAnswers = true, title = "Trip ke Bandung"))
        push(trip(now + 1.minutes, withAnswers = false, title = "Judul baru dari app lama"))

        val pulled = wire.decodeFromString<app.ransel.shared.api.ApiResponse<SyncPullResponse>>(get("/sync").bodyAsText()).data
        val synced = pulled.changes.trips.single { it.id == id }
        assertEquals("Judul baru dari app lama", synced.title)
        assertEquals("Subang", synced.origin?.name)
        assertEquals(DepartureMode.OWN_MOTORCYCLE, synced.departureMode)
        assertEquals(LocalTransport.UNKNOWN, synced.localTransport)
        assertEquals(MainTravelMode.MOTORCYCLE, synced.mainTravelMode)
    }
}
