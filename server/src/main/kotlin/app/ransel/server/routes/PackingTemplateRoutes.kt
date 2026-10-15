package app.ransel.server.routes

import app.ransel.server.service.PackingTemplateService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.ApplyTemplateRequest
import app.ransel.shared.dto.CreatePackingTemplateRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.packingTemplateRoutes() {
    get(ApiRoutes.PACKING_TEMPLATES) {
        call.respondData(HttpStatusCode.OK, PackingTemplateService.list(call.userId()))
    }
    post(ApiRoutes.PACKING_TEMPLATES) {
        val body = call.receive<CreatePackingTemplateRequest>()
        call.respondData(HttpStatusCode.Created, PackingTemplateService.create(call.userId(), bodyUuid(body.id, "id"), body))
    }
    post("${ApiRoutes.TRIPS}/{id}/packing/apply-template") {
        val body = call.receive<ApplyTemplateRequest>()
        call.respondData(HttpStatusCode.Created, PackingTemplateService.apply(call.userId(), call.tripId(), body))
    }
}
