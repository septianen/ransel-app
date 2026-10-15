package app.ransel.server.routes

import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.ItineraryService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.DayOrderRequest
import app.ransel.shared.dto.ItineraryItemInput
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate

fun Route.itineraryRoutes() {
    route("${ApiRoutes.TRIPS}/{id}/itinerary") {
        get {
            call.respondData(HttpStatusCode.OK, ItineraryService.list(call.userId(), call.tripId()))
        }
        post {
            val body = call.receive<ItineraryItemInput>()
            val itemId = bodyUuid(body.id, "id")
            call.respondData(HttpStatusCode.Created, ItineraryService.create(call.userId(), call.tripId(), itemId, body))
        }
        route("/{itemId}") {
            put {
                val body = call.receive<ItineraryItemInput>()
                val itemId = call.pathUuid("itemId", "Itinerary item")
                call.respondData(HttpStatusCode.OK, ItineraryService.replace(call.userId(), call.tripId(), itemId, body))
            }
            delete {
                ItineraryService.delete(call.userId(), call.tripId(), call.pathUuid("itemId", "Itinerary item"))
                call.respondNoContent()
            }
        }
    }
    put("${ApiRoutes.TRIPS}/{id}/days/{date}/order") {
        val date = runCatching { LocalDate.parse(call.parameters["date"].orEmpty()) }.getOrNull()
            ?: throw validationFailed(mapOf("date" to "INVALID_DATE"))
        val body = call.receive<DayOrderRequest>()
        call.respondData(HttpStatusCode.OK, ItineraryService.reorderDay(call.userId(), call.tripId(), date, body))
    }
}
