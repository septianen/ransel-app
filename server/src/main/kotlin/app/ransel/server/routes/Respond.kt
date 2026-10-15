package app.ransel.server.routes

import app.ransel.shared.api.ApiResponse
import app.ransel.shared.api.PageMeta
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Success body `{"data": ..., "meta": ...?}`. */
suspend inline fun <reified T : Any> ApplicationCall.respondData(
    status: HttpStatusCode,
    data: T,
    meta: PageMeta? = null,
) = respond(status, ApiResponse(data, meta))

/** 204 with an empty body. */
suspend fun ApplicationCall.respondNoContent() = respond(HttpStatusCode.NoContent)
