package app.ransel.server.routes

import app.ransel.server.service.ItineraryIdeaService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.InspirationCardDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** `/trips/{id}/itinerary/{itemId}/idea`: the idea card behind an item (GET VIEWER+, PUT EDITOR+). */
fun Route.itineraryIdeaRoutes(service: ItineraryIdeaService) {
    route("${ApiRoutes.TRIPS}/{id}/itinerary/{itemId}/idea") {
        get {
            call.respondData(HttpStatusCode.OK, service.get(call.userId(), call.tripId(), call.pathUuid("itemId", "Itinerary item")))
        }
        put {
            val body = call.receive<InspirationCardDto>()
            call.respondData(HttpStatusCode.OK, service.put(call.userId(), call.tripId(), call.pathUuid("itemId", "Itinerary item"), body))
        }
    }
}
