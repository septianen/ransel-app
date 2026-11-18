package app.ransel.server.routes

import app.ransel.server.service.TravelModeService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.TravelModeInput
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.travelModeRoutes() {
    route("${ApiRoutes.TRIPS}/{id}/travel-modes") {
        get {
            call.respondData(HttpStatusCode.OK, TravelModeService.list(call.userId(), call.tripId()))
        }
        put {
            val body = call.receive<TravelModeInput>()
            bodyUuid(body.id, "id")
            call.respondData(HttpStatusCode.OK, TravelModeService.upsert(call.userId(), call.tripId(), body))
        }
        delete("/{modeId}") {
            TravelModeService.delete(call.userId(), call.tripId(), call.pathUuid("modeId", "Travel mode"))
            call.respondNoContent()
        }
    }
}
