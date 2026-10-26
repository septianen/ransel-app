package app.ransel.server.routes

import app.ransel.server.plugins.GuestIdentityRateLimit
import app.ransel.server.plugins.GuestRateLimit
import app.ransel.server.service.GuestService
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.GuestIdentityRequest
import app.ransel.shared.dto.GuestTickRequest
import app.ransel.shared.dto.VotePollRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Public guest page (no bearer): the browser's guest token comes in a header. Rate limited per client address. */
fun Route.guestRoutes() {
    rateLimit(GuestRateLimit) {
        route("/guest/{code}") {
            get { call.respondData(HttpStatusCode.OK, GuestService.page(call.code(), call.guestToken())) }
            post("/polls/{pollId}/votes") {
                val body = call.receive<VotePollRequest>()
                call.respondData(HttpStatusCode.OK, GuestService.vote(call.code(), call.guestToken(), call.pathUuid("pollId", "Poll"), body))
            }
            patch("/personal-items/{itemId}") {
                val body = call.receive<GuestTickRequest>()
                call.respondData(HttpStatusCode.OK, GuestService.tick(call.code(), call.guestToken(), call.pathUuid("itemId", "Packing item"), body.isPacked))
            }
        }
    }
    rateLimit(GuestIdentityRateLimit) {
        post("/guest/{code}/identity") {
            val body = call.receive<GuestIdentityRequest>()
            call.respondData(HttpStatusCode.OK, GuestService.identify(call.code(), call.guestToken(), body))
        }
    }
}

private fun ApplicationCall.code() = parameters["code"].orEmpty()

private fun ApplicationCall.guestToken(): String? = request.headers[ApiRoutes.GUEST_TOKEN_HEADER]?.takeIf { it.isNotBlank() }
