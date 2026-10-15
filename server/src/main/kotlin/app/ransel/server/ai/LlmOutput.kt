package app.ransel.server.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

/** The model is asked for JSON but not forced to the schema ("soft" JSON), so parsing is lenient. */
object LlmOutput {
    private val lenient = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** Accepts `{"places":[...]}`, a bare array, and output wrapped in a markdown code fence. */
    fun parsePlaces(content: String): List<LlmCandidate> {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root: JsonElement = lenient.parseToJsonElement(cleaned)
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root["places"]?.jsonArray ?: error("no places array")
            else -> error("unexpected JSON")
        }
        return array.map { lenient.decodeFromJsonElement(LlmCandidate.serializer(), it) }
    }
}
