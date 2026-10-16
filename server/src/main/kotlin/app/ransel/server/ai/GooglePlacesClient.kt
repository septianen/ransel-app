package app.ransel.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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

    override suspend fun findPlaceId(name: String, destination: String, focusArea: String?): String? {
        val response = http.post("$baseUrl/places:searchText") {
            header("X-Goog-Api-Key", apiKey)
            header("X-Goog-FieldMask", "places.id")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("textQuery", listOfNotNull(name, focusArea?.takeIf { it.isNotBlank() }, destination).joinToString(", "))
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
        // priceLevel raises the call above Essentials; it is fetched once per grounded place and then cached with it.
        val mask = if (withRating) "location,formattedAddress,priceLevel,rating,photos" else "location,formattedAddress,priceLevel"
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
            priceLevel = priceLevelOf(body["priceLevel"]?.jsonPrimitive?.content),
            photoAttribution = if (withRating) body.firstPhoto()?.get("authorAttributions")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("displayName")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } else null,
        )
    }

    /** Two calls: the photo's resource name (Place Details, `photos`), then its bytes (Place Photo, 800 px wide). */
    override suspend fun photo(placeId: String): PlacePhoto? {
        val detailsResponse = http.get("$baseUrl/places/$placeId") {
            header("X-Goog-Api-Key", apiKey)
            header("X-Goog-FieldMask", "photos")
        }
        failOnQuota(detailsResponse.status)
        if (detailsResponse.status == HttpStatusCode.NotFound) return null
        if (!detailsResponse.status.isSuccess()) error("Places photo lookup HTTP ${detailsResponse.status.value}")
        val body: JsonObject = json.parseToJsonElement(detailsResponse.body<String>()).jsonObject
        val name = body.firstPhoto()?.get("name")?.jsonPrimitive?.content ?: return null

        val media = http.get("$baseUrl/$name/media") {
            header("X-Goog-Api-Key", apiKey)
            parameter("maxWidthPx", PHOTO_WIDTH_PX)
        }
        failOnQuota(media.status)
        if (media.status == HttpStatusCode.NotFound) return null
        if (!media.status.isSuccess()) error("Places photo HTTP ${media.status.value}")
        val type = media.contentType()?.withoutParameters()?.toString() ?: return null
        if (!type.startsWith("image/")) return null
        return PlacePhoto(media.body<ByteArray>(), type)
    }

    private fun JsonObject.firstPhoto(): JsonObject? = get("photos")?.jsonArray?.firstOrNull()?.jsonObject

    private fun failOnQuota(status: HttpStatusCode) {
        if (status == HttpStatusCode.TooManyRequests || status == HttpStatusCode.Forbidden) {
            throw PlacesQuotaException(status.value)
        }
    }

    private companion object {
        const val PHOTO_WIDTH_PX = 800

        /** Places (New) sends `PRICE_LEVEL_MODERATE`; UNSPECIFIED and unknown values mean "no price". */
        fun priceLevelOf(raw: String?): Int? = when (raw) {
            "PRICE_LEVEL_FREE" -> 0
            "PRICE_LEVEL_INEXPENSIVE" -> 1
            "PRICE_LEVEL_MODERATE" -> 2
            "PRICE_LEVEL_EXPENSIVE" -> 3
            "PRICE_LEVEL_VERY_EXPENSIVE" -> 4
            else -> null
        }
    }
}
