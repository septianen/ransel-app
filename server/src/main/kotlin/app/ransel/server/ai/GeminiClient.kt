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
import org.slf4j.LoggerFactory

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
    /** Tried when [modelName] is overloaded (429/5xx), within the same call. Null = no fallback. */
    private val fallbackModel: String? = null,
) : LlmClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val log = LoggerFactory.getLogger(GeminiClient::class.java)

    /** HTTP-level trouble that another model may not have; parse problems are not this. */
    private class Overloaded(message: String) : RuntimeException(message)

    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> =
        // 18 places with their detail text are several times longer than the names and one-line reasons they used to be.
        LlmOutput.parsePlaces(generate(Prompt.system(), Prompt.user(query), temperature = 0.7, maxTokens = SUGGEST_MAX_TOKENS))

    override suspend fun suggestInterests(destination: String, month: String, language: String): InterestSuggestions =
        LlmOutput.parseInterests(
            generate(Prompt.interestsSystem(), Prompt.interestsUser(destination, month, language), temperature = 0.4),
            coreLabels = CORE_LABELS,
        )

    override suspend fun describe(name: String, address: String?): LlmPlaceText =
        LlmOutput.parsePlaceText(generate(Prompt.detailSystem(), Prompt.detailUser(name, address), temperature = 0.4))

    override suspend fun readReceipt(image: ByteArray, mimeType: String): LlmReceipt {
        val user = buildJsonArray {
            add(
                buildJsonObject {
                    put(
                        "inlineData",
                        buildJsonObject {
                            put("mimeType", mimeType)
                            put("data", java.util.Base64.getEncoder().encodeToString(image))
                        },
                    )
                },
            )
            add(buildJsonObject { put("text", Prompt.receiptUser()) })
        }
        return LlmOutput.parseReceipt(generate(Prompt.receiptSystem(), user, temperature = 0.0, maxTokens = RECEIPT_MAX_TOKENS))
    }

    private suspend fun generate(system: String, user: String, temperature: Double, maxTokens: Int = DEFAULT_MAX_TOKENS): String =
        generate(system, parts(user), temperature, maxTokens)

    /** Tries the primary model, then the fallback when the first is overloaded (429/5xx). Returns the raw JSON text. */
    private suspend fun generate(system: String, user: kotlinx.serialization.json.JsonArray, temperature: Double, maxTokens: Int): String {
        val models = listOfNotNull(modelName, fallbackModel?.takeIf { it.isNotBlank() && it != modelName })
        var last: Overloaded? = null
        for (model in models) {
            try {
                return generateWith(model, system, user, temperature, maxTokens)
            } catch (e: Overloaded) {
                last = e
                log.warn("LLM model {} unavailable: {}", model, e.message)
            }
        }
        throw last ?: IllegalStateException("no LLM model configured")
    }

    private suspend fun generateWith(model: String, system: String, user: kotlinx.serialization.json.JsonArray, temperature: Double, maxTokens: Int): String {
        val request = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", parts(system)) })
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", user)
                        },
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", temperature)
                    // Headroom: "thinking" models spend part of this budget before the visible answer.
                    put("maxOutputTokens", maxTokens)
                    put("responseMimeType", "application/json")
                },
            )
        }
        val response = http.post("$baseUrl/models/$model:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(request.toString())
        }
        if (!response.status.isSuccess()) {
            val code = response.status.value
            if (code == 429 || code >= 500) throw Overloaded("HTTP $code")
            error("LLM HTTP $code")
        }

        val root: JsonObject = json.parseToJsonElement(response.body<String>()).jsonObject
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("LLM returned no candidates (blocked?)")
        val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
            ?.filter { it.jsonObject["thought"]?.jsonPrimitive?.content != "true" }
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("")
        val finish = candidate["finishReason"]?.jsonPrimitive?.content
        if (text.isNullOrBlank()) error("LLM returned no text (finishReason=$finish)")
        // A cut-off answer is unparseable JSON: say why in the log instead of only "SerializationException".
        if (finish != null && finish != "STOP") log.warn("LLM model {} finishReason={} (answer may be cut off)", model, finish)
        return text
    }

    private companion object {
        const val DEFAULT_MAX_TOKENS = 8192
        const val SUGGEST_MAX_TOKENS = 16384
        const val RECEIPT_MAX_TOKENS = 4096

        /** Core interest names in both languages: the model is told to skip them, this is the safety net. */
        val CORE_LABELS = setOf(
            "nature", "alam", "food", "kuliner", "culture", "budaya", "history", "sejarah", "shopping", "belanja",
            "adventure", "petualangan", "nightlife", "hiburan malam", "photo spots", "spot foto", "relax", "santai",
        )
    }

    private fun parts(text: String) = buildJsonArray { add(buildJsonObject { put("text", text) }) }
}
