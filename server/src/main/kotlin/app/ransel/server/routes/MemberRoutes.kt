package app.ransel.server.routes

import app.ransel.server.plugins.InvitePreviewRateLimit
import app.ransel.server.service.GuestService
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.InviteService
import app.ransel.server.service.MemberService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.AddMemberRequest
import app.ransel.shared.dto.CreateInviteRequest
import app.ransel.shared.dto.TransferOwnershipRequest
import app.ransel.shared.dto.UpdateMemberRoleRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.memberRoutes(invites: InviteService) {
    route("${ApiRoutes.TRIPS}/{id}/invites") {
        get { call.respondData(HttpStatusCode.OK, invites.list(call.userId(), call.tripId())) }
        post {
            val body = call.receive<CreateInviteRequest>()
            call.respondData(HttpStatusCode.Created, invites.create(call.userId(), call.tripId(), body))
        }
        delete("/{code}") {
            invites.revoke(call.userId(), call.tripId(), call.parameters["code"].orEmpty())
            call.respondNoContent()
        }
    }
    route("/invites/{code}") {
        post("/accept") {
            call.respondData(HttpStatusCode.OK, invites.accept(call.userId(), call.parameters["code"].orEmpty()))
        }
    }

    route("${ApiRoutes.TRIPS}/{id}/members") {
        get { call.respondData(HttpStatusCode.OK, MemberService.list(call.userId(), call.tripId())) }
        post {
            val body = call.receive<AddMemberRequest>()
            call.respondData(HttpStatusCode.Created, MemberService.addGuest(call.userId(), call.tripId(), body))
        }
        route("/{memberId}") {
            post("/claim") {
                MemberService.claim(call.userId(), call.tripId(), bodyUuid(call.parameters["memberId"].orEmpty(), "memberId"))
                call.respondNoContent()
            }
            delete("/guest-session") {
                val target = bodyUuid(call.parameters["memberId"].orEmpty(), "memberId")
                GuestService.release(call.userId(), call.tripId(), target)
                call.respondNoContent()
            }
            delete("/claim") {
                MemberService.undoClaim(call.userId(), call.tripId(), bodyUuid(call.parameters["memberId"].orEmpty(), "memberId"))
                call.respondNoContent()
            }
            patch {
                val body = call.receive<UpdateMemberRoleRequest>()
                val target = bodyUuid(call.parameters["memberId"].orEmpty(), "memberId")
                call.respondData(HttpStatusCode.OK, MemberService.updateRole(call.userId(), call.tripId(), target, body.role))
            }
            delete {
                val target = bodyUuid(call.parameters["memberId"].orEmpty(), "memberId")
                MemberService.remove(call.userId(), call.tripId(), target)
                call.respondNoContent()
            }
        }
    }
    post("${ApiRoutes.TRIPS}/{id}/leave") {
        MemberService.leave(call.userId(), call.tripId())
        call.respondNoContent()
    }
    post("${ApiRoutes.TRIPS}/{id}/transfer-ownership") {
        val body = call.receive<TransferOwnershipRequest>()
        val newOwner = bodyUuid(body.newOwnerUserId, "newOwnerUserId")
        MemberService.transferOwnership(call.userId(), call.tripId(), newOwner)
        call.respondNoContent()
    }
}

/** Public: a link opens in any browser, so no bearer token. Rate limited per client address. */
fun Route.invitePreviewRoutes(invites: InviteService) {
    rateLimit(InvitePreviewRateLimit) {
        get("/invites/{code}") { call.respondData(HttpStatusCode.OK, invites.preview(call.parameters["code"].orEmpty())) }
    }
}
