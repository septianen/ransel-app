package app.ransel.server.ai

import app.ransel.shared.dto.MatchRange
import app.ransel.shared.dto.NearbyAreaDto
import app.ransel.shared.dto.PlaceDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.PlaceSuggestionDto
import app.ransel.shared.dto.ViewportDto
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Destination search (TRD "Places"). Throws on transport errors; the service decides what to fall back to. */
interface PlaceSearch {
    suspend fun autocomplete(input: String, sessionToken: String?, near: Pair<Double, Double>?): List<PlaceSuggestionDto>

    /** Null when Google does not know the place. */
    suspend fun details(placeId: String, sessionToken: String?): PlaceDto?

    suspend fun reverse(lat: Double, lng: Double): List<NearbyAreaDto>
}

/**
 * Google Places Autocomplete (New), Place Details Essentials and the Geocoding API. Field masks keep every call in its
 * cheapest tier: details never ask for displayName (Pro); the name comes from the suggestion. Autocomplete takes five
 * primary types at most: airports are left out (owner decision 26 Oct 2026).
 */
class GooglePlaceSearch(
    private val http: HttpClient,
    private val apiKey: String,
    private val placesUrl: String = "https://places.googleapis.com/v1",
    private val geocodeUrl: String = "https://maps.googleapis.com/maps/api/geocode/json",
) : PlaceSearch {
    override suspend fun autocomplete(input: String, sessionToken: String?, near: Pair<Double, Double>?): List<PlaceSuggestionDto> {
        val response = http.post("$placesUrl/places:autocomplete") {
            header("X-Goog-Api-Key", apiKey)
            header("X-Goog-FieldMask", "suggestions.placePrediction.placeId,suggestions.placePrediction.structuredFormat,suggestions.placePrediction.types")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("input", input)
                    sessionToken?.let { put("sessionToken", it) }
                    put("languageCode", "id")
                    put("regionCode", "id")
                    put("includedPrimaryTypes", buildJsonArray { PRIMARY_TYPES.forEach { add(it) } })
                    putJsonObject("locationBias") {
                        if (near != null) {
                            putJsonObject("circle") {
                                putJsonObject("center") {
                                    put("latitude", near.first)
                                    put("longitude", near.second)
                                }
                                put("radius", NEAR_RADIUS_METERS)
                            }
                        } else {
                            // Indonesia first; places abroad still come up when nothing here matches
                            putJsonObject("rectangle") {
                                putJsonObject("low") { put("latitude", -11.2); put("longitude", 94.7) }
                                putJsonObject("high") { put("latitude", 6.3); put("longitude", 141.1) }
                            }
                        }
                    }
                }.toString(),
            )
        }
        if (!response.status.isSuccess()) error("Places autocomplete HTTP ${response.status.value}")
        return parseAutocomplete(response.body<String>())
    }

    override suspend fun details(placeId: String, sessionToken: String?): PlaceDto? {
        val response = http.get("$placesUrl/places/$placeId") {
            header("X-Goog-Api-Key", apiKey)
            header("X-Goog-FieldMask", "id,location,viewport,addressComponents,types")
            sessionToken?.let { parameter("sessionToken", it) }
            parameter("languageCode", "id")
        }
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.BadRequest) return null
        if (!response.status.isSuccess()) error("Places details HTTP ${response.status.value}")
        return parseDetails(placeId, response.body<String>())
    }

    override suspend fun reverse(lat: Double, lng: Double): List<NearbyAreaDto> {
        // The Geocoding API takes the key as a parameter; this request is never logged.
        val response = http.get(geocodeUrl) {
            parameter("latlng", "$lat,$lng")
            parameter("language", "id")
            parameter("result_type", "sublocality|administrative_area_level_3|administrative_area_level_4|locality|administrative_area_level_2")
            parameter("key", apiKey)
        }
        if (!response.status.isSuccess()) error("Geocoding HTTP ${response.status.value}")
        return parseReverse(response.body<String>())
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        val PRIMARY_TYPES = listOf("locality", "administrative_area_level_2", "administrative_area_level_1", "country", "tourist_attraction")
        private const val NEAR_RADIUS_METERS = 50_000.0

        fun kindOf(types: List<String>): PlaceKind = when {
            "locality" in types -> PlaceKind.CITY
            "administrative_area_level_2" in types -> PlaceKind.REGENCY
            "administrative_area_level_1" in types -> PlaceKind.PROVINCE
            "country" in types -> PlaceKind.COUNTRY
            "tourist_attraction" in types -> PlaceKind.ATTRACTION
            else -> PlaceKind.AREA
        }

        private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content
        private fun JsonObject.strings(key: String) = (this[key] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()

        internal fun parseAutocomplete(body: String): List<PlaceSuggestionDto> {
            val root = json.parseToJsonElement(body).jsonObject
            return (root["suggestions"] as? JsonArray).orEmpty().mapNotNull { s ->
                val p = s.jsonObject["placePrediction"]?.jsonObject ?: return@mapNotNull null
                val main = p["structuredFormat"]?.jsonObject?.get("mainText")?.jsonObject ?: return@mapNotNull null
                PlaceSuggestionDto(
                    placeId = p.str("placeId") ?: return@mapNotNull null,
                    name = main.str("text") ?: return@mapNotNull null,
                    secondary = p["structuredFormat"]?.jsonObject?.get("secondaryText")?.jsonObject?.str("text"),
                    kind = kindOf(p.strings("types")),
                    matchRanges = (main["matches"] as? JsonArray).orEmpty().mapNotNull { m ->
                        val o = m.jsonObject
                        // Google leaves startOffset out when it is 0
                        val end = o["endOffset"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                        MatchRange(o["startOffset"]?.jsonPrimitive?.intOrNull ?: 0, end)
                    },
                )
            }
        }

        internal fun parseDetails(placeId: String, body: String): PlaceDto? {
            val root = json.parseToJsonElement(body).jsonObject
            val location = root["location"]?.jsonObject ?: return null
            fun point(o: JsonObject?) = o?.let { (it["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null) to (it["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null) }
            val (lat, lng) = point(location) ?: return null
            val viewport = root["viewport"]?.jsonObject?.let { v ->
                val low = point(v["low"]?.jsonObject)
                val high = point(v["high"]?.jsonObject)
                if (low == null || high == null) null else ViewportDto(low.first, low.second, high.first, high.second)
            }
            val country = (root["addressComponents"] as? JsonArray).orEmpty()
                .map { it.jsonObject }
                .firstOrNull { "country" in it.strings("types") }
                ?.str("shortText")?.uppercase()?.takeIf { it.length == 2 }
            return PlaceDto(root.str("id") ?: placeId, lat, lng, viewport, country)
        }

        /**
         * Up to three levels: the neighbourhood or district ("Kemang"), its city or regency ("Jakarta Selatan"), and
         * the area around the city (`around`, shown as "Sekitar <city>"). "Kota"/"Kabupaten" prefixes are dropped from names.
         */
        internal fun parseReverse(body: String): List<NearbyAreaDto> {
            val root = json.parseToJsonElement(body).jsonObject
            val results = (root["results"] as? JsonArray).orEmpty().map { it.jsonObject }
            fun component(result: JsonObject, vararg types: String) = (result["address_components"] as? JsonArray).orEmpty()
                .map { it.jsonObject }
                .firstOrNull { c -> types.any { it in c.strings("types") } }
                ?.str("long_name")?.let(::shortName)

            val areaResult = results.firstOrNull { r ->
                r.strings("types").any { it in listOf("sublocality", "sublocality_level_1", "administrative_area_level_4", "administrative_area_level_3") }
            }
            val cityResult = results.firstOrNull { r -> r.strings("types").any { it == "locality" || it == "administrative_area_level_2" } }
            val city = cityResult?.let { component(it, "administrative_area_level_2", "locality") }
                ?: areaResult?.let { component(it, "administrative_area_level_2", "locality") }
            val province = (cityResult ?: areaResult)?.let { component(it, "administrative_area_level_1") }

            return buildList {
                areaResult?.let { r ->
                    val name = component(r, "sublocality_level_1", "sublocality", "administrative_area_level_4", "administrative_area_level_3")
                    if (name != null && name != city) add(NearbyAreaDto(r.str("place_id"), name, city, PlaceKind.AREA))
                }
                if (cityResult != null && city != null) {
                    add(NearbyAreaDto(cityResult.str("place_id"), city, province, kindOf(cityResult.strings("types"))))
                    add(NearbyAreaDto(cityResult.str("place_id"), city, province, PlaceKind.AREA, around = true))
                }
            }
        }

        private fun shortName(name: String): String = name.removePrefix("Kota ").removePrefix("Kabupaten ")
    }
}
