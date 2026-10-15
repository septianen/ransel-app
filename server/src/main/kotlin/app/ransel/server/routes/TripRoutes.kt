package app.ransel.server.routes

import app.ransel.server.service.TripService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.UpdateTripRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.tripRoutes() {
    route(ApiRoutes.TRIPS) {
        get {
            call.respondData(HttpStatusCode.OK, TripService.list(call.userId()))
        }
        post {
            val body = call.receive<CreateTripRequest>()
            val id = bodyUuid(body.id, "id")
            call.respondData(HttpStatusCode.Created, TripService.create(call.userId(), id, body))
        }
        route("/{id}") {
            get {
                call.respondData(HttpStatusCode.OK, TripService.detail(call.userId(), call.tripId()))
            }
            put {
                val body = call.receive<UpdateTripRequest>()
                call.respondData(HttpStatusCode.OK, TripService.update(call.userId(), call.tripId(), body))
            }
            delete {
                TripService.delete(call.userId(), call.tripId())
                call.respondNoContent()
            }
        }
    }
}
