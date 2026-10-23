package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.FxRates
import app.ransel.server.ai.InterestSuggestions
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmOutput
import app.ransel.server.ai.LlmPlaceText
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlacesClient
import app.ransel.shared.dto.OpeningHoursLine
import app.ransel.shared.model.Category
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scripted LLM for the place detail text: [candidates] for a search, [text] for a single place. */
private class DetailLlm(
    var candidates: List<LlmCandidate>,
    var text: LlmPlaceText = LlmPlaceText(about = "Dibangun ulang", highlights = listOf("Tip"), costUsd = 10.0),
    var describeFails: Boolean = false,
) : LlmClient {
    override val modelName = "fake-model"
    val describeCalls = AtomicInteger()

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> = candidates

    override suspend fun suggestInterests(destination: String, month: String, language: String) =
        InterestSuggestions(listOf("A", "B", "C"), lowMaxUsd = 30.0, midMaxUsd = 90.0)

    override suspend fun describe(name: String, address: String?): LlmPlaceText {
        describeCalls.incrementAndGet()
        if (describeFails) error("boom")
        return text
    }
}

/** Place ids are `pid-<Name>`; only [known] names exist. */
private class DetailPlaces(private val known: Set<String>) : PlacesClient {
    override suspend fun findPlaceId(name: String, destination: String): String? =
        if (name in known) "pid-$name" else null

    override suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails? {
        val name = placeId.removePrefix("pid-")
        return if (name in known) PlaceDetails(placeId, -8.5, 115.2, "Jl. $name", null) else null
    }

    override suspend fun photo(placeId: String): PlacePhoto? = null
}

/** 1 USD = 16.000 IDR (IDR has no decimals). */
private object DetailFx : FxRates {
    override fun toUsd(amountMinor: Long, currency: String) = if (currency == "IDR") amountMinor / 16_000.0 else null
    override fun fromUsd(usd: Double, currency: String): Long? = if (currency == "IDR") Math.round(usd * 16_000) else null
}

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

class PlaceDetailTest {
    private val withText = LlmCandidate(
        name = "Kuta",
        category = Category.ATTRACTION,
        reason = "Cocok",
        priceLevel = 1,
        costUsd = 15.0,
        suggestedDurationMin = 90,
        about = "Pantai terkenal.",
        highlights = listOf("Datang pagi", "Bawa topi"),
        openingHours = listOf(OpeningHoursLine("Setiap hari", "08.00 – 18.00")),
        bestTime = "Pagi",
    )

    private fun request(tripId: String) =
        """{"tripId":"$tripId","priceLevel":"MEDIUM","interests":["NATURE"],"extraInterests":[],"style":"SANTAI","newSearch":true,"excludePlaceIds":[]}"""

    private suspend fun Api.trip(): String =
        UUID.randomUUID().toString().also { post("/trips", tripJson(it, start = "2026-11-20", end = "2026-11-23", destination = "Bali")) }

    @Test
    fun searchCarriesTheDetailTextAndTheItineraryCanReopenItWithoutAnotherLlmCall() {
        val llm = DetailLlm(listOf(withText))
        apiTest(ai = AiServices(llm, DetailPlaces(setOf("Kuta"))), fx = DetailFx) {
            val item = post("/recommendations", request(trip())).data().jsonObject.getValue("groups").jsonArray
                .flatMap { it.jsonObject.getValue("items").jsonArray }.single().jsonObject
            assertEquals("Pantai terkenal.", item.str("about"))
            assertEquals(listOf("Datang pagi", "Bawa topi"), item.getValue("highlights").jsonArray.map { it.jsonPrimitive.content })
            assertEquals("08.00 – 18.00", item.getValue("openingHours").jsonArray.single().jsonObject.str("hours"))
            assertEquals("Pagi", item.str("bestTime"))
            assertEquals("240000", item.str("estimatedCostMinor"))
            assertEquals("IDR", item.str("costCurrency"))

            val detail = get("/places/pid-Kuta/detail?currency=IDR")
            assertEquals(HttpStatusCode.OK, detail.status)
            val body = detail.data().jsonObject
            assertEquals("Kuta", body.str("name"))
            assertEquals("Pantai terkenal.", body.str("about"))
            assertEquals("240000", body.str("estimatedCostMinor"))
            assertTrue(body.str("photoUrl").startsWith("/places/photo/pid-Kuta?exp="))
            assertEquals(0, llm.describeCalls.get(), "text saved at search time, nothing to rebuild")
        }
    }

    @Test
    fun aCandidateWithoutDetailTextStillWorks() {
        val llm = DetailLlm(listOf(withText.copy(about = null, highlights = emptyList(), openingHours = emptyList(), bestTime = null)))
        apiTest(ai = AiServices(llm, DetailPlaces(setOf("Kuta")))) {
            val item = post("/recommendations", request(trip())).data().jsonObject.getValue("groups").jsonArray
                .flatMap { it.jsonObject.getValue("items").jsonArray }.single().jsonObject
            assertNull(item["about"]?.takeIf { it.toString() != "null" })
            assertTrue(item.getValue("highlights").jsonArray.isEmpty())
            // nothing was saved, and the place was not asked about: 404 without a name to rebuild from
            assertEquals(HttpStatusCode.NotFound, get("/places/pid-Kuta/detail").status)
        }
    }

    @Test
    fun aMissingRowIsRebuiltOnceFromTheItemTitleAndThenServedFromTheCache() {
        val llm = DetailLlm(emptyList())
        apiTest(ai = AiServices(llm, DetailPlaces(setOf("TanahLot"))), fx = DetailFx) {
            val first = get("/places/pid-TanahLot/detail?name=TanahLot&currency=IDR")
            assertEquals(HttpStatusCode.OK, first.status)
            val body = first.data().jsonObject
            assertEquals("Dibangun ulang", body.str("about"))
            assertEquals("160000", body.str("estimatedCostMinor"))
            assertEquals("Jl. TanahLot", body.str("address"))

            assertEquals(HttpStatusCode.OK, get("/places/pid-TanahLot/detail").status)
            assertEquals(1, llm.describeCalls.get())
        }
    }

    @Test
    fun anUnknownPlaceOrAMissingNameIs404() {
        apiTest(ai = AiServices(DetailLlm(emptyList()), DetailPlaces(setOf("TanahLot")))) {
            assertEquals(HttpStatusCode.NotFound, get("/places/pid-Nowhere/detail?name=Nowhere").status)
            assertEquals(HttpStatusCode.NotFound, get("/places/pid-TanahLot/detail").status)
        }
    }

    @Test
    fun aFailingLlmStillAnswersWithWhatIsKnown() {
        val llm = DetailLlm(emptyList(), describeFails = true)
        apiTest(ai = AiServices(llm, DetailPlaces(setOf("TanahLot")))) {
            val response = get("/places/pid-TanahLot/detail?name=TanahLot")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.data().jsonObject
            assertEquals("TanahLot", body.str("name"))
            assertTrue(body["about"] == null || body.getValue("about").toString() == "null")
        }
    }

    @Test
    fun aMalformedHoursLineDoesNotCostTheWholeSearch() {
        val parsed = LlmOutput.parsePlaces(
            """{"places":[{"name":"Kuta","about":"Pantai","openingHours":[{"label":"Setiap hari"}],"highlights":["a"]},{"name":"Uluwatu"}]}""",
        )
        assertEquals(listOf("Kuta", "Uluwatu"), parsed.map { it.name })
        assertTrue(parsed.first().openingHours.isEmpty(), "the broken detail is dropped, the place is kept")
    }

    @Test
    fun placeTextIsTrimmedAndCapped() {
        val text = LlmOutput.parsePlaceText(
            """{"about":"  Pantai.  ","highlights":["1","2","3","4"," "],"openingHours":[{"label":"x","hours":" "},{"label":"Setiap hari","hours":"08.00"}],"bestTime":" ","costUsd":-3}""",
        )
        assertEquals("Pantai.", text.about)
        assertEquals(listOf("1", "2", "3"), text.highlights)
        assertEquals(listOf(OpeningHoursLine("Setiap hari", "08.00")), text.openingHours)
        assertNull(text.bestTime)
        assertNull(text.costUsd)
    }
}
