package app.ransel.server

import app.ransel.server.ai.GeminiClient
import app.ransel.server.ai.GooglePlacesClient
import app.ransel.server.ai.LlmQuery
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.Category
import app.ransel.shared.model.Interest
import app.ransel.shared.model.TravelStyle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

private val query = LlmQuery(
    destination = "Bali", focusArea = null, days = 4, startDate = "2026-11-20", priceLevel = PriceLevel.MEDIUM, priceGuide = null,
    interests = listOf(Interest.FOOD, Interest.NATURE), extraInterests = emptyList(),
    style = TravelStyle.SANTAI, notes = "tanpa babi", liked = listOf("Tanah Lot"), skipped = listOf("Pantai Kuta"), count = 15,
)

class GeminiClientTest {
    private fun client(captured: MutableList<HttpRequestData>, text: String?, status: HttpStatusCode = HttpStatusCode.OK, raw: String? = null): GeminiClient {
        val body = raw ?: """{"candidates":[{"content":{"parts":[{"text":${kotlinx.serialization.json.JsonPrimitive(text.orEmpty())}}]},"finishReason":"STOP"}]}"""
        val http = HttpClient(MockEngine { request ->
            captured += request
            respond(body, status, jsonHeaders)
        })
        return GeminiClient(http, "https://llm.test/v1beta", "g-llm-secret", "gemini-test")
    }

    @Test
    fun sendsKeyOnlyInTheHeaderAndAsksForJson() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val result = client(seen, """{"places":[{"name":"Tanah Lot","category":"ATTRACTION","reason":"Pura di laut","priceLevel":1,"suggestedDurationMin":90}]}""").suggest(query)

        assertEquals("Tanah Lot", result.single().name)
        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://llm.test/v1beta/models/gemini-test:generateContent", request.url.toString())
        assertEquals("g-llm-secret", request.headers["x-goog-api-key"])
        assertFalse(request.url.toString().contains("g-llm-secret"))

        val sent = kotlinx.serialization.json.Json.parseToJsonElement(String(request.body.toByteArray())).jsonObject
        assertEquals("application/json", sent.getValue("generationConfig").jsonObject.getValue("responseMimeType").jsonPrimitive.content)
        assertTrue("travel planner" in sent.getValue("systemInstruction").jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content)
        val user = sent.getValue("contents").jsonArray[0].jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content
        assertTrue("Bali" in user && "tanpa babi" in user && "Tanah Lot" in user && "Pantai Kuta" in user)
    }

    @Test
    fun toleratesCodeFencesBareArraysAndMissingFields() = runBlocking {
        val fenced = "```json\n{\"places\":[{\"name\":\"Kopi Seniman\",\"category\":\"FOOD\"}]}\n```"
        val c = client(mutableListOf(), fenced).suggest(query).single()
        assertEquals(Category.FOOD, c.category)
        assertNull(c.suggestedDurationMin)

        val bare = client(mutableListOf(), """[{"name":"Pura Uluwatu"}]""").suggest(query).single()
        assertEquals(Category.ATTRACTION, bare.category) // default when the model leaves it out
    }

    @Test
    fun ignoresThoughtPartsAndJoinsTheRest() = runBlocking {
        val raw = """{"candidates":[{"content":{"parts":[
            {"text":"Let me think about Bali...","thought":true},
            {"text":"{\"places\":[{\"name\":"},
            {"text":"\"Tanah Lot\"}]}"}]}}]}"""
        assertEquals("Tanah Lot", client(mutableListOf(), null, raw = raw).suggest(query).single().name)
    }

    @Test
    fun overloadedPrimaryFallsBackToTheSecondModel() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val inner = """{"places":[{"name":"Tanah Lot"}]}"""
        val ok = """{"candidates":[{"content":{"parts":[{"text":${kotlinx.serialization.json.JsonPrimitive(inner)}}]}}]}"""
        val http = HttpClient(MockEngine { request ->
            seen += request
            if (request.url.encodedPath.contains("primary")) respond("""{"error":{"message":"high demand"}}""", HttpStatusCode.ServiceUnavailable, jsonHeaders)
            else respond(ok, HttpStatusCode.OK, jsonHeaders)
        })
        val client = GeminiClient(http, "https://llm.test/v1beta", "k", "primary", fallbackModel = "backup")

        assertEquals("Tanah Lot", client.suggest(query).single().name)
        assertEquals(listOf("primary", "backup"), seen.map { it.url.encodedPath.substringAfter("models/").substringBefore(":") })
    }

    @Test
    fun clientErrorsAreNotRetriedOnTheFallback() {
        val seen = mutableListOf<HttpRequestData>()
        val http = HttpClient(MockEngine { request ->
            seen += request
            respond("""{"error":{"message":"bad key"}}""", HttpStatusCode.Forbidden, jsonHeaders)
        })
        val client = GeminiClient(http, "https://llm.test/v1beta", "k", "primary", fallbackModel = "backup")
        assertFailsWith<Exception> { runBlocking { client.suggest(query) } }
        assertEquals(1, seen.size)
    }

    @Test
    fun blockedEmptyGarbageAndHttpErrorsThrowSoTheCallerCanRetry() {
        val blocked = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        assertFailsWith<Exception> { runBlocking { client(mutableListOf(), null, raw = blocked).suggest(query) } }
        assertFailsWith<Exception> { runBlocking { client(mutableListOf(), "Maaf, saya tidak bisa.").suggest(query) } }
        assertFailsWith<Exception> { runBlocking { client(mutableListOf(), "{}", HttpStatusCode.TooManyRequests).suggest(query) } }
    }
}

class GooglePlacesClientTest {
    private fun client(captured: MutableList<HttpRequestData>, vararg responses: Pair<HttpStatusCode, String>): GooglePlacesClient {
        val queue = ArrayDeque(responses.toList())
        val http = HttpClient(MockEngine { request ->
            captured += request
            val (status, body) = queue.removeFirst()
            respond(body, status, jsonHeaders)
        })
        return GooglePlacesClient(http, "g-secret", "https://places.test/v1")
    }

    @Test
    fun searchUsesTheFreeIdsOnlyMaskAndKeepsTheKeyOutOfTheUrl() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val id = client(seen, HttpStatusCode.OK to """{"places":[{"id":"ChIJabc"}]}""").findPlaceId("Tanah Lot", "Bali")

        assertEquals("ChIJabc", id)
        val request = seen.single()
        assertEquals("places.id", request.headers["X-Goog-FieldMask"])
        assertEquals("g-secret", request.headers["X-Goog-Api-Key"])
        assertFalse(request.url.toString().contains("g-secret"))
        assertTrue("Tanah Lot, Bali" in String(request.body.toByteArray()))
    }

    @Test
    fun noMatchIsNullNotAnError() = runBlocking {
        assertNull(client(mutableListOf(), HttpStatusCode.OK to "{}").findPlaceId("Tempat Karangan", "Bali"))
    }

    @Test
    fun photoCreditIsOnlyRequestedWhenAskedForAndNeverTheEnterpriseFields() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val body = """{"location":{"latitude":-8.62,"longitude":115.09},"formattedAddress":"Beraban, Tabanan","photos":[{"name":"places/ChIJabc/photos/R","authorAttributions":[{"displayName":"Made Wijaya"}]}]}"""
        val c = client(seen, HttpStatusCode.OK to body, HttpStatusCode.OK to body)

        val plain = c.details("ChIJabc", withPhotoCredit = false)!!
        val credited = c.details("ChIJabc", withPhotoCredit = true)!!

        assertEquals("location,formattedAddress", seen[0].headers["X-Goog-FieldMask"])
        assertEquals("location,formattedAddress,photos", seen[1].headers["X-Goog-FieldMask"])
        assertTrue(seen.none { "rating" in it.headers["X-Goog-FieldMask"].orEmpty() || "priceLevel" in it.headers["X-Goog-FieldMask"].orEmpty() })
        assertEquals(-8.62, plain.lat)
        assertNull(plain.photoAttribution)
        assertEquals("Beraban, Tabanan", credited.address)
        assertEquals("Made Wijaya", credited.photoAttribution)
    }

    @Test
    fun unknownPlaceIsNullAndServerErrorsThrow() {
        runBlocking {
            assertNull(client(mutableListOf(), HttpStatusCode.NotFound to "{}").details("nope", false))
        }
        assertFailsWith<Exception> { runBlocking { client(mutableListOf(), HttpStatusCode.Forbidden to "{}").details("x", false) } }
    }

    @Test
    fun photoNeedsTwoCallsAndNeverPutsTheKeyInTheUrl() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val http = HttpClient(MockEngine { request ->
            seen += request
            if (request.url.encodedPath.endsWith("/media")) {
                respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
            } else {
                respond("""{"photos":[{"name":"places/ChIJabc/photos/REF1"}]}""", HttpStatusCode.OK, jsonHeaders)
            }
        })
        val photo = GooglePlacesClient(http, "g-secret", "https://places.test/v1").photo("ChIJabc")!!

        assertEquals("image/jpeg", photo.contentType)
        assertEquals(listOf<Byte>(1, 2, 3), photo.bytes.toList())
        assertEquals("photos", seen[0].headers["X-Goog-FieldMask"])
        assertEquals("/v1/places/ChIJabc/photos/REF1/media", seen[1].url.encodedPath)
        assertTrue(seen.none { it.url.toString().contains("g-secret") })
    }

    @Test
    fun placeWithoutPhotosIsNullAndQuotaRejectionsAreTyped() {
        runBlocking { assertNull(client(mutableListOf(), HttpStatusCode.OK to "{}").photo("ChIJabc")) }
        assertFailsWith<app.ransel.server.ai.PlacesQuotaException> {
            runBlocking { client(mutableListOf(), HttpStatusCode.TooManyRequests to "{}").photo("ChIJabc") }
        }
    }
}

class ExploreParsingTest {
    @Test
    fun interestsAreCleanedAndNeedAPriceGuide() {
        val tooLong = "x".repeat(41)
        val answer = """{"interests":["Sakura"," Onsen ","sakura","Kuliner","$tooLong","Ryokan"],"priceGuide":{"lowMaxUsd":40,"midMaxUsd":120}}"""
        val parsed = app.ransel.server.ai.LlmOutput.parseInterests("```json\n$answer\n```", coreLabels = setOf("kuliner"))
        assertEquals(listOf("Sakura", "Onsen", "Ryokan"), parsed.interests)
        assertEquals(3, parsed.interests.size, "duplicates, core labels and over-long labels are dropped")
        assertEquals(120.0, parsed.midMaxUsd)
    }

    @Test
    fun badAnswersThrowSoTheCallerRetries() {
        assertFailsWith<Exception> { app.ransel.server.ai.LlmOutput.parseInterests("""{"interests":["a","b"],"priceGuide":{"lowMaxUsd":1,"midMaxUsd":2}}""") }
        assertFailsWith<Exception> { app.ransel.server.ai.LlmOutput.parseInterests("""{"interests":["a","b","c"],"priceGuide":{"lowMaxUsd":5,"midMaxUsd":2}}""") }
        assertFailsWith<Exception> { app.ransel.server.ai.LlmOutput.parseInterests("not json") }
    }

    @Test
    fun ecbRatesConvertThroughEuro() {
        val xml = """<Cube><Cube time='2026-10-16'><Cube currency='USD' rate='1.08'/><Cube currency='IDR' rate='17280.0'/><Cube currency='JPY' rate='162'/></Cube></Cube>"""
        val rates = app.ransel.server.ai.EcbFxRates.parse(xml) + ("EUR" to 1.0)
        // Rp1.600.000 (0 decimals) = EUR 92.59 = USD 100
        assertEquals(100.0, app.ransel.server.ai.EcbFxRates.convert(rates, 1_600_000, "IDR")!!, 0.01)
        assertNull(app.ransel.server.ai.EcbFxRates.convert(rates, 100, "XYZ"))
    }
}

