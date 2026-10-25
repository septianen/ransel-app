package app.ransel.server.routes

import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.CommentService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.CreateCommentRequest
import app.ransel.shared.dto.UpdateCommentRequest
import app.ransel.shared.model.CommentTarget
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.commentRoutes() {
    route("${ApiRoutes.TRIPS}/{id}/comments") {
        get {
            val kind = targetKind(call.request.queryParameters["targetKind"])
            val target = bodyUuid(call.request.queryParameters["targetId"].orEmpty(), "targetId")
            call.respondData(HttpStatusCode.OK, CommentService.list(call.userId(), call.tripId(), kind, target))
        }
        get("/counts") {
            val kind = targetKind(call.request.queryParameters["targetKind"])
            call.respondData(HttpStatusCode.OK, CommentService.counts(call.userId(), call.tripId(), kind))
        }
        post {
            val body = call.receive<CreateCommentRequest>()
            val id = bodyUuid(body.id, "id")
            call.respondData(HttpStatusCode.Created, CommentService.create(call.userId(), call.tripId(), id, body))
        }
    }
    route("/comments/{commentId}") {
        patch {
            val body = call.receive<UpdateCommentRequest>()
            val id = bodyUuid(call.parameters["commentId"].orEmpty(), "commentId")
            call.respondData(HttpStatusCode.OK, CommentService.update(call.userId(), id, body))
        }
        delete {
            CommentService.delete(call.userId(), bodyUuid(call.parameters["commentId"].orEmpty(), "commentId"))
            call.respondNoContent()
        }
    }
}

private fun targetKind(raw: String?): CommentTarget =
    CommentTarget.entries.firstOrNull { it.name == raw } ?: throw validationFailed(mapOf("targetKind" to "INVALID"))
