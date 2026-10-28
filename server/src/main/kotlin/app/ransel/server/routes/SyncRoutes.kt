package app.ransel.server.routes

import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.SyncService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.SyncPushRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID
import kotlin.time.Instant

fun Route.syncRoutes(sync: SyncService) {
    get(ApiRoutes.SYNC) {
        val params = call.request.queryParameters
        val since = params["since"]?.let {
            runCatching { Instant.parse(it) }.getOrNull() ?: throw validationFailed(mapOf("since" to "INVALID_TIMESTAMP"))
        }
        val tripLimit = params["tripLimit"]?.let {
            it.toIntOrNull()?.takeIf { n -> n > 0 } ?: throw validationFailed(mapOf("tripLimit" to "MUST_BE_POSITIVE"))
        }
        val afterTripId = params["afterTripId"]?.let {
            runCatching { UUID.fromString(it) }.getOrNull() ?: throw validationFailed(mapOf("afterTripId" to "INVALID_UUID"))
        }
        call.respondData(HttpStatusCode.OK, sync.pull(call.userId(), since, tripLimit, afterTripId))
    }
    post(ApiRoutes.SYNC) {
        call.respondData(HttpStatusCode.OK, sync.push(call.userId(), call.receive<SyncPushRequest>().changes))
    }
}
