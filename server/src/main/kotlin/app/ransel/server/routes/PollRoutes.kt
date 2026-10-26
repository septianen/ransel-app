package app.ransel.server.routes

import app.ransel.server.service.PollService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.server.service.requireVerifiedEmail
import app.ransel.shared.dto.BulkPollOptionsRequest
import app.ransel.shared.dto.CreatePollRequest
import app.ransel.shared.dto.PollSearchRequest
import app.ransel.shared.dto.PollSearchResponse
import java.util.UUID
import app.ransel.shared.dto.DecidePollRequest
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.dto.ReopenPollRequest
import app.ransel.shared.dto.UpdatePollRequest
import app.ransel.shared.dto.VotePollRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.pollRoutes(appBaseUrl: String, search: suspend (UUID, UUID, PollSearchRequest) -> PollSearchResponse) {
    route("${ApiRoutes.TRIPS}/{id}/polls") {
        get { call.respondData(HttpStatusCode.OK, PollService.list(call.userId(), call.tripId())) }
        post {
            val body = call.receive<CreatePollRequest>()
            call.respondData(HttpStatusCode.Created, PollService.create(call.userId(), call.tripId(), body))
        }
    }
    route("/polls/{pollId}") {
        get { call.respondData(HttpStatusCode.OK, PollService.get(call.userId(), call.pollUuid())) }
        patch {
            val body = call.receive<UpdatePollRequest>()
            call.respondData(HttpStatusCode.OK, PollService.update(call.userId(), call.pollUuid(), body))
        }
        delete {
            PollService.delete(call.userId(), call.pollUuid())
            call.respondNoContent()
        }
        post("/options") {
            val body = call.receive<PollOptionInput>()
            call.respondData(HttpStatusCode.Created, PollService.addOption(call.userId(), call.pollUuid(), body))
        }
        route("/options/{optionId}") {
            patch {
                val body = call.receive<PollOptionInput>()
                val option = call.pathUuid("optionId", "Option")
                call.respondData(HttpStatusCode.OK, PollService.updateOption(call.userId(), call.pollUuid(), option, body))
            }
            delete {
                val option = call.pathUuid("optionId", "Option")
                call.respondData(HttpStatusCode.OK, PollService.deleteOption(call.userId(), call.pollUuid(), option))
            }
        }
        post("/options/bulk") {
            val body = call.receive<BulkPollOptionsRequest>()
            call.respondData(HttpStatusCode.Created, PollService.addOptions(call.userId(), call.pollUuid(), body.options))
        }
        post("/search") {
            val body = call.receive<PollSearchRequest>()
            requireVerifiedEmail(call.userId())
            call.respondData(HttpStatusCode.OK, search(call.userId(), call.pollUuid(), body))
        }
        put("/votes") {
            val body = call.receive<VotePollRequest>()
            call.respondData(HttpStatusCode.OK, PollService.vote(call.userId(), call.pollUuid(), body))
        }
        post("/share") { call.respondData(HttpStatusCode.OK, PollService.share(call.userId(), call.pollUuid(), appBaseUrl)) }
        delete("/share") {
            PollService.unshare(call.userId(), call.pollUuid())
            call.respondNoContent()
        }
        post("/close") { call.respondData(HttpStatusCode.OK, PollService.close(call.userId(), call.pollUuid())) }
        post("/decide") {
            val body = call.receive<DecidePollRequest>()
            call.respondData(HttpStatusCode.OK, PollService.decide(call.userId(), call.pollUuid(), body))
        }
        post("/reopen") {
            val body = call.receive<ReopenPollRequest>()
            call.respondData(HttpStatusCode.OK, PollService.reopen(call.userId(), call.pollUuid(), body))
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.pollUuid() = pathUuid("pollId", "Poll")
