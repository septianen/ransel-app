package app.ransel.server.routes

import app.ransel.server.plugins.ApiException
import app.ransel.server.service.RoundService
import app.ransel.server.service.userId
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.DecideRequest
import app.ransel.shared.dto.VoteRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

/** Itinerary voting (docs/specs/voting-spec.md). Paths mirror `ApiRoutes.rounds/round/roundVote/roundClose/roundDecide`. */
fun Route.votingRoutes(service: RoundService) {
    get("/trips/{id}/rounds") {
        call.respondData(HttpStatusCode.OK, service.list(call.tripId(), call.userId()))
    }
    get("/trips/{id}/rounds/{roundId}") {
        call.respondData(HttpStatusCode.OK, service.get(call.tripId(), call.roundId(), call.userId()))
    }
    put("/trips/{id}/rounds/{roundId}/votes/{placeId}") {
        val body = call.receive<VoteRequest>()
        service.vote(call.tripId(), call.roundId(), call.parameters["placeId"].orEmpty(), call.userId(), body.vote)
        call.respondNoContent()
    }
    delete("/trips/{id}/rounds/{roundId}/votes/{placeId}") {
        service.undo(call.tripId(), call.roundId(), call.parameters["placeId"].orEmpty(), call.userId())
        call.respondNoContent()
    }
    post("/trips/{id}/rounds/{roundId}/close") {
        service.close(call.tripId(), call.roundId(), call.userId())
        call.respondNoContent()
    }
    post("/trips/{id}/rounds/{roundId}/decide") {
        val body = call.receive<DecideRequest>()
        service.decide(call.tripId(), call.roundId(), call.userId(), body.placeIds)
        call.respondNoContent()
    }
}

/** A malformed round id can only be a round that does not exist. */
private fun ApplicationCall.roundId(): UUID =
    runCatching { UUID.fromString(parameters["roundId"].orEmpty()) }.getOrNull()
        ?: throw ApiException(HttpStatusCode.NotFound, ErrorCode.ROUND_NOT_FOUND, "Voting round not found")
