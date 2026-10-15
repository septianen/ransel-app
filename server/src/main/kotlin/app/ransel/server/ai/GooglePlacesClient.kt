package app.ransel.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Google Places API (New). Cost control lives in the field masks:
 *  - Text Search with `places.id` only is the free "IDs Only" tier;
 *  - Place Details with `location,formattedAddress` is Essentials; adding `rating` raises the call to Pro,
 *    so [details] only asks for it when told to.
 * The key goes in a header, never in a URL, and nothing here is ever returned to the app.
 */
class GooglePlacesClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://places.googleapis.com/v1",
) : PlacesClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun findPlaceId(name: String, destination: String): String? {
        val response = http.post("$baseUrl/places:searchText") {
            header("X-Goog-Api-Key", apiKey)
            header("X-Goog-FieldMask", "places.id")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("textQuery", "$name, $destination")
                    put("maxResultCount", 1)
                    put("languageCode", "id")
                }.toString(),
            )
        }
        if (!response.status.isSuccess()) error("Places search HTTP ${response.status.value}")
        val body: JsonObject = json.parseToJsonElement(response.body<String>()).jsonObject
        return body["places"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    override suspend fun details(placeId: String, withRating: Boolean): PlaceDetails? {
        val mask = if (withRating) "location,formattedAddress,rating" else "location,formattedAddress"
        val response = http.get("$baseUrl/places/$placeId") {
            header("X-Goog-Api-Key", apiKey)
            header("X-Goog-FieldMask", mask)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) error("Places details HTTP ${response.status.value}")
        val body: JsonObject = json.parseToJsonElement(response.body<String>()).jsonObject
        val location = body["location"]?.jsonObject ?: return null
        val lat = location["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lng = location["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        return PlaceDetails(
            placeId = placeId,
            lat = lat,
            lng = lng,
            address = body["formattedAddress"]?.jsonPrimitive?.content,
            rating = body["rating"]?.jsonPrimitive?.doubleOrNull,
        )
    }
}
