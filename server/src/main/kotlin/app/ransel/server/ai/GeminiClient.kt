package app.ransel.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Gemini Developer API (`generateContent`). The key travels in the `x-goog-api-key` header, never in the URL,
 * and is never logged. A blocked or truncated answer has no usable text and surfaces as an exception, which
 * the caller retries once before answering 503 AI_UNAVAILABLE.
 */
class GeminiClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    override val modelName: String,
) : LlmClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> {
        val request = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", parts(Prompt.system())) })
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", parts(Prompt.user(query)))
                        },
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", 0.7)
                    // Headroom: "thinking" models spend part of this budget before the visible answer.
                    put("maxOutputTokens", 8192)
                    put("responseMimeType", "application/json")
                },
            )
        }
        val response = http.post("$baseUrl/models/$modelName:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(request.toString())
        }
        if (!response.status.isSuccess()) error("LLM HTTP ${response.status.value}")

        val root: JsonObject = json.parseToJsonElement(response.body<String>()).jsonObject
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("LLM returned no candidates (blocked?)")
        val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
            ?.filter { it.jsonObject["thought"]?.jsonPrimitive?.content != "true" }
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("")
        if (text.isNullOrBlank()) error("LLM returned no text (finishReason=${candidate["finishReason"]?.jsonPrimitive?.content})")
        return LlmOutput.parsePlaces(text)
    }

    private fun parts(text: String) = buildJsonArray { add(buildJsonObject { put("text", text) }) }
}
