package app.ransel.server.plugins

import app.ransel.shared.api.ErrorCode
import io.ktor.http.HttpStatusCode

/** The only way a route or service fails on purpose. StatusPages turns it into an `ApiErrorResponse`. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    message: String,
    val details: Map<String, String>? = null,
) : RuntimeException(message)

fun tripNotFound() = ApiException(HttpStatusCode.NotFound, ErrorCode.TRIP_NOT_FOUND, "Trip not found")

fun notFound(what: String) = ApiException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, "$what not found")

fun forbidden() = ApiException(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, "Your role does not allow this action")

fun conflict(message: String) = ApiException(HttpStatusCode.Conflict, ErrorCode.CONFLICT, message)

fun validationFailed(details: Map<String, String>) =
    ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.VALIDATION_FAILED, "Validation failed", details)

/** Throws 422 `VALIDATION_FAILED` when [details] is not empty. */
fun requireValid(details: Map<String, String>) {
    if (details.isNotEmpty()) throw validationFailed(details)
}
