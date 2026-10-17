package app.ransel.server.ai

import app.ransel.server.AppConfig
import app.ransel.server.plugins.ApiException
import app.ransel.shared.api.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode

/** Without keys the server still starts; Explore answers 503 AI_UNAVAILABLE and everything else works. */
private object NotConfiguredLlm : LlmClient {
    override val modelName = "none"
    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> = throw notConfigured()
    override suspend fun suggestInterests(destination: String, month: String, language: String): InterestSuggestions =
        throw notConfigured()
    override suspend fun describe(name: String, address: String?): LlmPlaceText = throw notConfigured()
}

private object NotConfiguredPlaces : PlacesClient {
    override suspend fun findPlaceId(name: String, destination: String, focusArea: String?): String? = throw notConfigured()
    override suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails? = throw notConfigured()
    override suspend fun photo(placeId: String): PlacePhoto? = throw notConfigured()
}

private fun notConfigured() =
    ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.AI_UNAVAILABLE, "Recommendations are not configured")

/** Builds the real clients from config; [http] is returned so the caller can close it on shutdown. */
fun aiServicesFrom(config: AppConfig): Pair<AiServices, HttpClient?> {
    if (config.llmApiKey.isBlank() || config.placesApiKey.isBlank()) {
        return AiServices(NotConfiguredLlm, NotConfiguredPlaces) to null
    }
    val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 50_000
            // generateContent answers in one piece, so the first byte only arrives when the whole answer is ready (the
            // detail text made it several times longer). OkHttp's default read timeout is 10 s: that was the
            // SocketTimeoutException. The chain: 50 s per LLM attempt, two attempts at most (100 s), plus Places, inside
            // nginx's proxy_read_timeout (120 s) and the app's 120 s search timeout.
            socketTimeoutMillis = 50_000
            connectTimeoutMillis = 10_000
        }
    }
    return AiServices(
        llm = GeminiClient(http, config.llmBaseUrl, config.llmApiKey, config.llmModel, config.llmFallbackModel),
        places = GooglePlacesClient(http, config.placesApiKey),
    ) to http
}
