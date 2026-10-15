package app.ransel.server.routes

import app.ransel.server.service.PackingService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.PatchPackingItemRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.packingRoutes() {
    route("${ApiRoutes.TRIPS}/{id}/packing") {
        get {
            call.respondData(HttpStatusCode.OK, PackingService.list(call.userId(), call.tripId()))
        }
        post {
            val body = call.receive<CreatePackingItemRequest>()
            val itemId = bodyUuid(body.id, "id")
            call.respondData(HttpStatusCode.Created, PackingService.create(call.userId(), call.tripId(), itemId, body))
        }
        post("/uncheck-all") {
            PackingService.uncheckAll(call.userId(), call.tripId())
            call.respondNoContent()
        }
        route("/{itemId}") {
            patch {
                val body = call.receive<PatchPackingItemRequest>()
                val itemId = call.pathUuid("itemId", "Packing item")
                call.respondData(HttpStatusCode.OK, PackingService.patch(call.userId(), call.tripId(), itemId, body))
            }
            delete {
                PackingService.delete(call.userId(), call.tripId(), call.pathUuid("itemId", "Packing item"))
                call.respondNoContent()
            }
        }
    }
}
