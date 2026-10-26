package app.ransel.server.routes

import app.ransel.server.service.PackingService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.RemindMemberListRequest
import app.ransel.shared.dto.SendMemberListRequest
import app.ransel.shared.dto.SaveMemberListRequest
import app.ransel.shared.dto.PatchPersonalItemRequest
import app.ransel.shared.dto.CreatePersonalItemRequest
import app.ransel.server.service.MemberListService
import app.ransel.server.service.PersonalItemService
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.PatchPackingItemRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
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
            post("/bring") {
                call.respondData(HttpStatusCode.OK, PackingService.bring(call.userId(), call.tripId(), call.pathUuid("itemId", "Packing item")))
            }
            delete("/bring") {
                call.respondData(HttpStatusCode.OK, PackingService.unbring(call.userId(), call.tripId(), call.pathUuid("itemId", "Packing item")))
            }
        }
    }
    route("${ApiRoutes.TRIPS}/{id}/personal-items") {
        get { call.respondData(HttpStatusCode.OK, PersonalItemService.list(call.userId(), call.tripId())) }
        post {
            val body = call.receive<CreatePersonalItemRequest>()
            call.respondData(HttpStatusCode.Created, PersonalItemService.create(call.userId(), call.tripId(), bodyUuid(body.id, "id"), body))
        }
        patch("/{itemId}") {
            val body = call.receive<PatchPersonalItemRequest>()
            call.respondData(HttpStatusCode.OK, PersonalItemService.patch(call.userId(), call.tripId(), call.pathUuid("itemId", "Packing item"), body))
        }
        delete("/{itemId}") {
            PersonalItemService.delete(call.userId(), call.tripId(), call.pathUuid("itemId", "Packing item"))
            call.respondNoContent()
        }
    }
    route("${ApiRoutes.TRIPS}/{id}/member-list") {
        get { call.respondData(HttpStatusCode.OK, MemberListService.get(call.userId(), call.tripId())) }
        put {
            val body = call.receive<SaveMemberListRequest>()
            call.respondData(HttpStatusCode.OK, MemberListService.save(call.userId(), call.tripId(), body))
        }
        post("/send") {
            val body = call.receive<SendMemberListRequest>()
            call.respondData(HttpStatusCode.OK, MemberListService.send(call.userId(), call.tripId(), body))
        }
        get("/progress") { call.respondData(HttpStatusCode.OK, MemberListService.progress(call.userId(), call.tripId())) }
        post("/remind") {
            val body = call.receive<RemindMemberListRequest>()
            MemberListService.remind(call.userId(), call.tripId(), body.memberIds)
            call.respondNoContent()
        }
    }
}
