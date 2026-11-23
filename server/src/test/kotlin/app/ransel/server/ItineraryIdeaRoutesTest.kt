package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.InterestSuggestions
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmPlaceText
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlacesClient
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Counts every AI call: reading or saving an idea must make none. */
private class NoAiLlm : LlmClient {
    override val modelName = "fake"
    val calls = AtomicInteger()
    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> { calls.incrementAndGet(); return emptyList() }
    override suspend fun suggestInterests(destination: String, month: String, language: String): InterestSuggestions {
        calls.incrementAndGet(); return InterestSuggestions(emptyList(), 1.0, 2.0)
    }
    override suspend fun describe(name: String, address: String?): LlmPlaceText { calls.incrementAndGet(); return LlmPlaceText() }
}

/** Ids are `pid-<text>`; only "Taman Nasional Komodo, NTT" has a point. */
private class IdeaPlaces : PlacesClient {
    val lookups = AtomicInteger()
    override suspend fun findPlaceId(name: String, destination: String): String? { lookups.incrementAndGet(); return "pid-$name" }
    override suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails? =
        if (placeId == "pid-Taman Nasional Komodo, NTT") PlaceDetails(placeId, -8.55, 119.49, "Komodo, NTT") else null
    override suspend fun photo(placeId: String): PlacePhoto? = null
}

class ItineraryIdeaRoutesTest {
    private val card = """{"id":"c1","activity":"Marine wildlife photography · Taman Nasional Komodo, NTT","region":"Taman Nasional Komodo, NTT",""" +
        """"countryCode":"ID","why":"Pari manta dan penyu.","access":"Kapal dari Labuan Bajo","bestSeason":"Apr–Des","level":"Menengah",""" +
        """"requirements":["Lisensi selam"],"costRange":{"minMinor":1500000,"maxMinor":3000000,"currency":"IDR"},""" +
        """"photoUrl":"/places/photo/evil?exp=1&sig=x","safetyLine":"Selalu dengan pemandu."}"""

    private suspend fun Api.tripWithItem(): Pair<String, String> {
        val trip = UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }
        val item = UUID.randomUUID().toString().also { post("/trips/$trip/itinerary", itemJson(it, title = "Marine wildlife photography", day = null, start = null, end = null)) }
        return trip to item
    }

    @Test
    fun savingKeepsTheCardFindsThePointAndReadingCallsNothing() {
        val llm = NoAiLlm()
        val places = IdeaPlaces()
        apiTest(ai = AiServices(llm, places)) {
            val (trip, item) = tripWithItem()
            // creating the trip may warm its interest suggestions; only saving and reading the idea count here
            val aiBefore = llm.calls.get()
            assertEquals(HttpStatusCode.NotFound, get("/trips/$trip/itinerary/$item/idea").status)

            val saved = put("/trips/$trip/itinerary/$item/idea", card)
            assertEquals(HttpStatusCode.OK, saved.status, saved.jsonBody().toString())
            val body = saved.data().jsonObject
            assertEquals(-8.55, body.getValue("lat").jsonPrimitive.content.toDouble())
            assertEquals("pid-Taman Nasional Komodo, NTT", body.getValue("placeId").jsonPrimitive.content)
            val c = body.getValue("card").jsonObject
            assertEquals("Marine wildlife photography", c.getValue("activity").jsonPrimitive.content, "the region is cut from the title")
            // the photo is the server's own lookup, signed; the link the app sent is not trusted
            assertTrue(c.getValue("photoUrl").jsonPrimitive.content.startsWith("/places/photo/pid-Marine"))
            assertEquals("Lisensi selam", c.getValue("requirements").jsonArray.single().jsonPrimitive.content)

            val lookups = places.lookups.get()
            val read = get("/trips/$trip/itinerary/$item/idea")
            assertEquals(HttpStatusCode.OK, read.status)
            assertEquals("Pari manta dan penyu.", read.data().jsonObject.getValue("card").jsonObject.getValue("why").jsonPrimitive.content)
            assertEquals(lookups, places.lookups.get(), "reading makes no Places call")
            assertEquals(aiBefore, llm.calls.get(), "saving and reading make no AI call")
        }
    }

    @Test
    fun aRegionWithoutAPointIsStillSavedAndAViewerReadsButCannotSave() {
        apiTest(ai = AiServices(NoAiLlm(), IdeaPlaces())) {
            val owner = session()
            val (trip, item) = tripWithItem()
            val other = card.replace("Taman Nasional Komodo, NTT", "Nowhere")
            val saved = put("/trips/$trip/itinerary/$item/idea", other).data().jsonObject
            assertTrue(saved["lat"] == null || saved.getValue("lat").toString() == "null")

            val code = post("/trips/$trip/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""")
                .data().jsonObject.getValue("code").jsonPrimitive.content
            signUp()
            post("/invites/$code/accept")
            assertEquals(HttpStatusCode.OK, get("/trips/$trip/itinerary/$item/idea").status)
            assertEquals(HttpStatusCode.Forbidden, put("/trips/$trip/itinerary/$item/idea", card).status)
            use(owner)
        }
    }

    @Test
    fun anItemOfAnotherTripIsNotFound() {
        apiTest(ai = AiServices(NoAiLlm(), IdeaPlaces())) {
            val (trip, _) = tripWithItem()
            val (_, otherItem) = tripWithItem()
            assertEquals(HttpStatusCode.NotFound, put("/trips/$trip/itinerary/$otherItem/idea", card).status)
        }
    }
}
