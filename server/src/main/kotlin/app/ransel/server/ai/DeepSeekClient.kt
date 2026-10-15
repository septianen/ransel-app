package app.ransel.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * DeepSeek (OpenAI-compatible chat completions). The key travels in the Authorization header only and is never
 * logged. JSON is "soft": the model is asked for it, so parsing is lenient and a failure surfaces as an exception
 * for the caller's single retry.
 */
class DeepSeekClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    override val modelName: String,
) : LlmClient {
    private val lenient = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> {
        val request = buildJsonObject {
            put("model", modelName)
            put("temperature", 0.7)
            put("max_tokens", 3000)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put(
                "messages",
                buildJsonArray {
                    add(message("system", Prompt.system()))
                    add(message("user", Prompt.user(query)))
                },
            )
        }
        val response = http.post("$baseUrl/chat/completions") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(request.toString())
        }
        if (!response.status.isSuccess()) error("LLM HTTP ${response.status.value}")

        val envelope: JsonObject = lenient.parseToJsonElement(response.body<String>()).jsonObject
        val content = envelope.getValue("choices").jsonArray.first().jsonObject
            .getValue("message").jsonObject.getValue("content").jsonPrimitive.content
        return parsePlaces(content)
    }

    /** Accepts `{"places":[...]}`, a bare array, and output wrapped in a markdown code fence. */
    internal fun parsePlaces(content: String): List<LlmCandidate> {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root: JsonElement = lenient.parseToJsonElement(cleaned)
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root["places"]?.jsonArray ?: error("no places array")
            else -> error("unexpected JSON")
        }
        return array.map { lenient.decodeFromJsonElement(LlmCandidate.serializer(), it) }
    }

    private fun message(role: String, content: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", content)
    }
}
