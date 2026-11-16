package app.ransel.server.routes

import app.ransel.server.ai.RecommendationService
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.CancelSearchResponse
import app.ransel.shared.dto.FeedbackRequest
import app.ransel.shared.dto.RecommendationRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.netty.NettyApplicationCall
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingPipelineCall
import io.ktor.utils.io.InternalAPI
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import app.ransel.server.service.requireVerifiedEmail
import java.util.UUID

fun Route.recommendationRoutes(service: RecommendationService) {
    post(ApiRoutes.RECOMMENDATIONS) {
        val body = call.receive<RecommendationRequest>()
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.OK, service.recommend(call.userId(), body, stillConnected = { call.clientIsConnected() }))
    }
    delete("/recommendations/searches/{searchId}") {
        val cancelled = service.cancelSearch(call.userId(), call.parameters["searchId"].orEmpty())
        call.respondData(HttpStatusCode.OK, CancelSearchResponse(cancelled))
    }
    post(ApiRoutes.RECOMMENDATION_FEEDBACK) {
        service.saveFeedback(call.userId(), call.receive<FeedbackRequest>())
        call.respondNoContent()
    }
    delete("/recommendations/feedback/{tripId}/{placeId}") {
        val tripId = runCatching { UUID.fromString(call.parameters["tripId"].orEmpty()) }.getOrNull()
            ?: throw validationFailed(mapOf("tripId" to "INVALID_UUID"))
        service.undoFeedback(call.userId(), tripId, call.parameters["placeId"].orEmpty())
        call.respondNoContent()
    }
}

/**
 * False once the app's connection is closed, so there is nobody to receive the answer. Only the Netty engine can say (the
 * test engine and anything else count as connected).
 */
@OptIn(InternalAPI::class)
private fun ApplicationCall.clientIsConnected(): Boolean {
    val engineCall = ((this as? RoutingCall)?.pipelineCall as? RoutingPipelineCall)?.engineCall ?: this
    return (engineCall as? NettyApplicationCall)?.context?.channel()?.isActive ?: true
}
