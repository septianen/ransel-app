package app.ransel.server.routes

import app.ransel.server.service.TripTasteService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.TripTasteDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** `/trips/{id}/taste`: the trip's Seleramu (GET VIEWER+, PUT EDITOR+). */
fun Route.tripTasteRoutes() {
    route("${ApiRoutes.TRIPS}/{id}/taste") {
        get {
            call.respondData(HttpStatusCode.OK, TripTasteService.get(call.userId(), call.tripId()))
        }
        put {
            val body = call.receive<TripTasteDto>()
            call.respondData(HttpStatusCode.OK, TripTasteService.put(call.userId(), call.tripId(), body))
        }
    }
}
