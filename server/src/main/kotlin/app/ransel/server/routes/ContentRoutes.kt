package app.ransel.server.routes

import app.ransel.server.service.ContentService
import app.ransel.shared.api.ApiRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /content?version=` (public): the remote content the apps will read from stage E. 204 when the app already has
 * that version; a short cache so a publish reaches everyone within minutes.
 */
fun Route.contentRoutes(content: ContentService) {
    get(ApiRoutes.CONTENT) {
        val known = call.request.queryParameters["version"]?.toIntOrNull()
        val now = content.content(known)
        call.response.header(HttpHeaders.CacheControl, "public, max-age=300")
        if (now == null) call.respond(HttpStatusCode.NoContent) else call.respondData(HttpStatusCode.OK, now)
    }
}
