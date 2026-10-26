package app.ransel.server.routes

import app.ransel.server.service.KittyService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.KittyDepositInput
import app.ransel.shared.dto.KittyInput
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.kittyRoutes() {
    route("${ApiRoutes.TRIPS}/{id}/kitty") {
        get { call.respondData(HttpStatusCode.OK, KittyService.overview(call.userId(), call.tripId())) }
        put {
            val body = call.receive<KittyInput>()
            call.respondData(HttpStatusCode.OK, KittyService.put(call.userId(), call.tripId(), body))
        }
        delete {
            KittyService.delete(call.userId(), call.tripId())
            call.respondNoContent()
        }
        post("/deposits") {
            val body = call.receive<KittyDepositInput>()
            call.respondData(HttpStatusCode.Created, KittyService.addDeposit(call.userId(), call.tripId(), body))
        }
        delete("/deposits/{depositId}") {
            KittyService.deleteDeposit(call.userId(), call.tripId(), call.pathUuid("depositId", "Deposit"))
            call.respondNoContent()
        }
    }
}
