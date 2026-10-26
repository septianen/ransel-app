package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.GooglePlaceSearch
import app.ransel.server.ai.InterestSuggestions
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlaceSearch
import app.ransel.server.ai.PlacesClient
import app.ransel.shared.dto.MatchRange
import app.ransel.shared.dto.NearbyAreaDto
import app.ransel.shared.dto.PlaceDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.PlaceSuggestionDto
import app.ransel.shared.dto.ViewportDto
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private class FakePlaceSearch : PlaceSearch {
    var autocompleteCalls = 0
    var detailsCalls = 0
    var slowMillis = 0L
    var broken = false
    var lastNear: Pair<Double, Double>? = null
    var lastSession: String? = null

    override suspend fun autocomplete(input: String, sessionToken: String?, near: Pair<Double, Double>?): List<PlaceSuggestionDto> {
        autocompleteCalls++
        lastNear = near
        lastSession = sessionToken
        if (slowMillis > 0) delay(slowMillis)
        if (broken) error("google down")
        return listOf(PlaceSuggestionDto("ChIJ-bajo", "Labuan Bajo", "Manggarai Barat, Nusa Tenggara Timur", PlaceKind.CITY, listOf(MatchRange(0, 3))))
    }

    override suspend fun details(placeId: String, sessionToken: String?): PlaceDto? {
        detailsCalls++
        if (broken) error("google down")
        return if (placeId == "ChIJ-bajo") PlaceDto(placeId, -8.49, 119.88, ViewportDto(-8.6, 119.8, -8.4, 120.0), "ID") else null
    }

    override suspend fun reverse(lat: Double, lng: Double): List<NearbyAreaDto> {
        if (broken) error("google down")
        return listOf(NearbyAreaDto("p1", "Kemang", "Jakarta Selatan", PlaceKind.AREA), NearbyAreaDto("p2", "Jakarta Selatan", "DKI Jakarta", PlaceKind.CITY))
    }
}

private object QuietLlm : LlmClient {
    override val modelName = "fake"
    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> = emptyList()
    override suspend fun suggestInterests(destination: String, month: String, language: String) = InterestSuggestions(emptyList(), 30.0, 90.0)
}

private object NoPlacesClient : PlacesClient {
    override suspend fun findPlaceId(name: String, destination: String): String? = null
    override suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails? = null
    override suspend fun photo(placeId: String): PlacePhoto? = null
}

class PlaceRoutesTest {
    private val session = "3f1e9c7a-1111-4a2b-9c3d-123456789abc"

    @Test
    fun suggestionsComeFromGoogleOnceAndThenFromTheCache() {
        val search = FakePlaceSearch()
        apiTest(ai = AiServices(QuietLlm, NoPlacesClient, search)) {
            val first = get("/places/autocomplete?q=Lab&session=$session")
            assertEquals(HttpStatusCode.OK, first.status, first.bodyAsText())
            val body = first.data().jsonObject
            assertEquals("false", body.str("fallback"))
            val s = body.getValue("suggestions").jsonArray.single().jsonObject
            assertEquals("Labuan Bajo", s.str("name"))
            assertEquals("CITY", s.str("kind"))
            assertEquals(session, search.lastSession)

            get("/places/autocomplete?q=%20lab%20&session=$session")
            assertEquals(1, search.autocompleteCalls, "the same query in other case and spacing is cached")

            // too short: nothing, and Google is not asked
            assertTrue(get("/places/autocomplete?q=L").data().jsonObject.getValue("suggestions").jsonArray.isEmpty())
            assertEquals(1, search.autocompleteCalls)
        }
    }

    @Test
    fun aBrokenOrSlowGoogleFallsBackToTheBuiltInList() {
        val search = FakePlaceSearch().apply { broken = true }
        apiTest(ai = AiServices(QuietLlm, NoPlacesClient, search)) {
            val body = get("/places/autocomplete?q=bandung").data().jsonObject
            assertEquals("true", body.str("fallback"))
            val first = body.getValue("suggestions").jsonArray.first().jsonObject
            assertEquals("Bandung", first.str("name"))
            assertEquals("Jawa Barat", first.str("secondary"))
            assertTrue("placeId" !in first || first["placeId"].toString() == "null")

            search.broken = false
            search.slowMillis = 3_000
            val slow = get("/places/autocomplete?q=yogya").data().jsonObject
            assertEquals("true", slow.str("fallback"))
            assertEquals("Yogyakarta", slow.getValue("suggestions").jsonArray.first().jsonObject.str("name"))
        }
    }

    @Test
    fun aBiasPointIsPassedOnAndAWrongOneRefused() {
        val search = FakePlaceSearch()
        apiTest(ai = AiServices(QuietLlm, NoPlacesClient, search)) {
            get("/places/autocomplete?q=pantai&near=-8.65,115.21")
            assertEquals(-8.65 to 115.21, search.lastNear)
            assertEquals(HttpStatusCode.UnprocessableEntity, get("/places/autocomplete?q=pantai&near=200,1").status)
        }
    }

    @Test
    fun detailsAreCachedUnknownPlacesAre404AndAnOutageIs503() {
        val search = FakePlaceSearch()
        apiTest(ai = AiServices(QuietLlm, NoPlacesClient, search)) {
            val place = get("/places/ChIJ-bajo?session=$session").data().jsonObject
            assertEquals("-8.49", place.str("lat"))
            assertEquals("ID", place.str("countryCode"))
            get("/places/ChIJ-bajo")
            assertEquals(1, search.detailsCalls)
            assertEquals("NOT_FOUND", get("/places/ChIJ-nothing").errorCode())
            search.broken = true
            assertEquals("PLACES_UNAVAILABLE", get("/places/ChIJ-other").errorCode())
            assertEquals("PLACES_UNAVAILABLE", get("/places/reverse?lat=-6.26&lng=106.81").errorCode())
        }
    }

    @Test
    fun reverseGivesAreasAndChecksTheCoordinates() {
        apiTest(ai = AiServices(QuietLlm, NoPlacesClient, FakePlaceSearch())) {
            val areas = get("/places/reverse?lat=-6.26&lng=106.81").data().jsonObject.getValue("areas").jsonArray
            assertEquals(listOf("Kemang", "Jakarta Selatan"), areas.map { it.jsonObject.str("name") })
            assertEquals(HttpStatusCode.UnprocessableEntity, get("/places/reverse?lat=-99&lng=0").status)
            assertEquals(HttpStatusCode.UnprocessableEntity, get("/places/reverse?lat=x").status)
        }
    }

    @Test
    fun withoutAPlacesKeySuggestionsStillWork() = apiTest {
        val body = get("/places/autocomplete?q=malang").data().jsonObject
        assertEquals("true", body.str("fallback"))
        assertEquals("Malang", body.getValue("suggestions").jsonArray.first().jsonObject.str("name"))
        assertEquals("PLACES_UNAVAILABLE", get("/places/ChIJ-bajo").errorCode())
    }

    @Test
    fun aTripKeepsItsPlaceUntilTheNameChanges() = apiTest {
        val id = UUID.randomUUID().toString()
        val place = """{"placeId":"ChIJ-bajo","lat":-8.49,"lng":119.88,"viewport":{"lowLat":-8.6,"lowLng":119.8,"highLat":-8.4,"highLng":120.0}}"""
        val created = post("/trips", tripJson(id, destination = "Labuan Bajo").dropLast(1) + ""","destinationPlace":$place}""")
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        assertEquals("ChIJ-bajo", created.data().jsonObject.getValue("destinationPlace").jsonObject.str("placeId"))

        // an older app sends no place: kept while the name stays
        put("/trips/$id", tripJson(id, title = "Bajo!", destination = "Labuan Bajo").replace(""""id":"$id",""", ""))
        val kept = get("/trips/$id").data().jsonObject.getValue("trip").jsonObject
        assertEquals("ChIJ-bajo", kept.getValue("destinationPlace").jsonObject.str("placeId"))

        // a typed new destination drops the old point
        put("/trips/$id", tripJson(id, destination = "Flores").replace(""""id":"$id",""", ""))
        val cleared = get("/trips/$id").data().jsonObject.getValue("trip").jsonObject
        assertTrue(cleared["destinationPlace"] == null || cleared["destinationPlace"].toString() == "null")

        val bad = post("/trips", tripJson(UUID.randomUUID().toString()).dropLast(1) + ""","destinationPlace":{"placeId":"x","lat":95,"lng":0}}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
    }

    @Test
    fun popularOnlyShowsPlacesPickedByEnoughGroups() {
        apiTest(ai = AiServices(QuietLlm, NoPlacesClient, FakePlaceSearch())) {
            val place = """{"placeId":"ChIJ-bajo","lat":-8.49,"lng":119.88}"""
            // two owners: not enough
            repeat(2) {
                signUp()
                post("/trips", tripJson(destination = "Labuan Bajo").dropLast(1) + ""","destinationPlace":$place}""")
            }
            assertTrue(get("/places/popular").data().jsonArray.isEmpty())
        }
        apiTest(ai = AiServices(QuietLlm, NoPlacesClient, FakePlaceSearch())) {
            val place = """{"placeId":"ChIJ-bajo","lat":-8.49,"lng":119.88}"""
            repeat(3) {
                signUp()
                post("/trips", tripJson(destination = "Labuan Bajo").dropLast(1) + ""","destinationPlace":$place}""")
            }
            val popular = get("/places/popular").data().jsonArray.single().jsonObject
            assertEquals("Labuan Bajo", popular.str("name"))
        }
    }

    @Test
    fun googleAnswersAreReadIntoSuggestionsPlacesAndAreas() {
        val suggestions = GooglePlaceSearch.parseAutocomplete(
            """{"suggestions":[{"placePrediction":{"placeId":"ChIJ1","types":["locality","political"],
            "structuredFormat":{"mainText":{"text":"Labuan Bajo","matches":[{"endOffset":3}]},"secondaryText":{"text":"Manggarai Barat, Nusa Tenggara Timur"}}}},
            {"queryPrediction":{"text":{"text":"lab"}}}]}""",
        )
        assertEquals(1, suggestions.size)
        assertEquals(PlaceKind.CITY, suggestions.single().kind)
        assertEquals(listOf(MatchRange(0, 3)), suggestions.single().matchRanges)

        val place = GooglePlaceSearch.parseDetails(
            "ChIJ1",
            """{"id":"ChIJ1","location":{"latitude":-8.49,"longitude":119.88},"viewport":{"low":{"latitude":-8.6,"longitude":119.8},"high":{"latitude":-8.4,"longitude":120.0}},
            "addressComponents":[{"longText":"Indonesia","shortText":"ID","types":["country","political"]}]}""",
        )!!
        assertEquals("ID", place.countryCode)
        assertEquals(ViewportDto(-8.6, 119.8, -8.4, 120.0), place.viewport)
        assertNull(GooglePlaceSearch.parseDetails("x", """{"id":"x"}"""))

        val areas = GooglePlaceSearch.parseReverse(
            """{"status":"OK","results":[
            {"place_id":"a1","types":["sublocality","sublocality_level_1","political"],"address_components":[
              {"long_name":"Kemang","types":["sublocality","sublocality_level_1","political"]},
              {"long_name":"Kota Jakarta Selatan","types":["administrative_area_level_2","political"]},
              {"long_name":"Daerah Khusus Ibukota Jakarta","types":["administrative_area_level_1","political"]}]},
            {"place_id":"c1","types":["administrative_area_level_2","political"],"address_components":[
              {"long_name":"Kota Jakarta Selatan","types":["administrative_area_level_2","political"]},
              {"long_name":"Daerah Khusus Ibukota Jakarta","types":["administrative_area_level_1","political"]}]}]}""",
        )
        assertEquals(listOf("Kemang", "Jakarta Selatan", "Jakarta Selatan"), areas.map { it.name })
        assertEquals(listOf(false, false, true), areas.map { it.around })
        assertEquals("Daerah Khusus Ibukota Jakarta", areas[1].secondary)
    }
}
