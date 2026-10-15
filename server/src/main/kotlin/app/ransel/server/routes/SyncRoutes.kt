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
import kotlin.time.Instant

fun Route.syncRoutes(sync: SyncService) {
    get(ApiRoutes.SYNC) {
        val since = call.request.queryParameters["since"]?.let {
            runCatching { Instant.parse(it) }.getOrNull() ?: throw validationFailed(mapOf("since" to "INVALID_TIMESTAMP"))
        }
        call.respondData(HttpStatusCode.OK, sync.pull(call.userId(), since))
    }
    post(ApiRoutes.SYNC) {
        call.respondData(HttpStatusCode.OK, sync.push(call.userId(), call.receive<SyncPushRequest>().changes))
    }
}
