package app.ransel.server.routes

import app.ransel.server.ai.RecommendationService
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.FeedbackRequest
import app.ransel.shared.dto.RecommendationRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import java.util.UUID

fun Route.recommendationRoutes(service: RecommendationService) {
    post(ApiRoutes.RECOMMENDATIONS) {
        val body = call.receive<RecommendationRequest>()
        call.respondData(HttpStatusCode.OK, service.recommend(call.userId(), body))
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
