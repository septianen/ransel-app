package app.ransel.server.plugins

import app.ransel.shared.api.ApiError
import app.ransel.shared.api.ApiErrorResponse
import app.ransel.shared.api.ApiErrorText
import app.ransel.shared.api.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.acceptLanguage
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

/** Read by the request logger so every line carries `error.code`. */
val ErrorCodeKey = AttributeKey<String>("error.code")

private val log = LoggerFactory.getLogger("app.ransel.server.errors")

/** Karung's copy for an error code: (code, language "id"/"en", details) → text, or null for the app's own. */
fun interface ErrorTextSource {
    fun text(code: String, language: String, details: Map<String, String>?): ApiErrorText?
}

/** Set by the application module (ContentService); unset = errors carry no copy. */
val ErrorTextSourceKey = AttributeKey<ErrorTextSource>("error.text.source")

/** "id" when the client asked for Indonesian (`id`, or `in` from older Android), else "en". */
fun ApplicationCall.errorLanguage(): String {
    val first = request.acceptLanguage()?.split(',')?.firstOrNull()?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return if (first.startsWith("id") || first.startsWith("in")) "id" else "en"
}

/** [error] with Karung's copy for its code in the caller's language. Never fails: no copy is the fallback. */
fun ApplicationCall.withErrorText(error: ApiError): ApiError {
    val source = application.attributes.getOrNull(ErrorTextSourceKey) ?: return error
    val text = runCatching { source.text(error.code, errorLanguage(), error.details) }
        .onFailure { log.warn("error copy for {} unavailable: {}", error.code, it.javaClass.simpleName) }
        .getOrNull()
    return if (text == null) error else error.copy(text = text)
}

suspend fun ApplicationCall.respondError(status: HttpStatusCode, error: ApiError) {
    attributes.put(ErrorCodeKey, error.code)
    respond(status, ApiErrorResponse(withErrorText(error)))
}

/** Class names only (never the input), so a client or a test can tell a missing field from a wrong type. */
private fun causeChain(e: Throwable): String =
    generateSequence(e) { it.cause }.take(3).joinToString(" <- ") { it.javaClass.simpleName }

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            call.respondError(e.status, ApiError(e.code, e.message ?: e.code, e.details))
        }
        // Malformed JSON, wrong field types, unknown enum constants, bad UUIDs in the body.
        exception<BadRequestException> { call, e ->
            call.respondError(
                HttpStatusCode.BadRequest,
                ApiError(ErrorCode.VALIDATION_FAILED, "Malformed request body", mapOf("cause" to causeChain(e))),
            )
        }
        exception<ContentTransformationException> { call, e ->
            call.respondError(
                HttpStatusCode.BadRequest,
                ApiError(ErrorCode.VALIDATION_FAILED, "Malformed request body", mapOf("cause" to causeChain(e))),
            )
        }
        exception<SerializationException> { call, e ->
            call.respondError(
                HttpStatusCode.BadRequest,
                ApiError(ErrorCode.VALIDATION_FAILED, "Malformed request body", mapOf("cause" to causeChain(e))),
            )
        }
        // Sent by the RateLimit plugin (Retry-After is already set); give it the standard error body.
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respondError(
                HttpStatusCode.TooManyRequests,
                ApiError(ErrorCode.RATE_LIMITED, "Too many attempts, try again in a minute"),
            )
        }
        exception<Throwable> { call, e ->
            // Full detail stays in the log; the body never carries a stack trace or SQL text.
            log.error("Unhandled error on {} {}", call.request.local.method.value, call.request.local.uri, e)
            call.respondError(
                HttpStatusCode.InternalServerError,
                ApiError(ErrorCode.INTERNAL_ERROR, "Something went wrong"),
            )
        }
    }
}
