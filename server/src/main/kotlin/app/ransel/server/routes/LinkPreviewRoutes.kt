package app.ransel.server.routes

import app.ransel.server.plugins.LinkPreviewRateLimit
import app.ransel.server.service.LinkPreviewService
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.LinkPreviewRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.linkPreviewRoutes(service: LinkPreviewService) {
    rateLimit(LinkPreviewRateLimit) {
        post(ApiRoutes.LINK_PREVIEW) {
            val body = call.receive<LinkPreviewRequest>()
            call.respondData(HttpStatusCode.OK, service.preview(body.url))
        }
    }
}
