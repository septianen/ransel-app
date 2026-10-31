package app.ransel.server.routes

import app.ransel.server.plugins.AlbumItemsRateLimit
import app.ransel.server.plugins.GuestRateLimit
import app.ransel.server.service.AlbumService
import app.ransel.server.service.GuestService
import app.ransel.server.service.userId
import app.ransel.shared.dto.CreateAlbumRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Trip albums (behind authenticate). */
fun Route.albumRoutes(albums: AlbumService) {
    route("${app.ransel.shared.api.ApiRoutes.TRIPS}/{id}/albums") {
        get { call.respondData(HttpStatusCode.OK, albums.list(call.userId(), call.tripId())) }
        post {
            val body = call.receive<CreateAlbumRequest>()
            call.respondData(HttpStatusCode.Created, albums.create(call.userId(), call.tripId(), body))
        }
        delete("/{albumId}") {
            albums.delete(call.userId(), call.tripId(), call.pathUuid("albumId", "Album"))
            call.respondNoContent()
        }
        rateLimit(AlbumItemsRateLimit) {
            get("/{albumId}/items") {
                val page = albums.items(call.userId(), call.tripId(), call.pathUuid("albumId", "Album"), call.request.queryParameters["pageToken"])
                call.respondData(HttpStatusCode.OK, page)
            }
        }
    }
}

/** The guest page's galleries (public, like the page itself; no name needs to be picked). */
fun Route.guestAlbumRoutes(albums: AlbumService) {
    rateLimit(GuestRateLimit) {
        get("/guest/{code}/albums/{albumId}/items") {
            val tripId = GuestService.tripOf(call.parameters["code"].orEmpty())
            val page = albums.guestItems(tripId, call.pathUuid("albumId", "Album"), call.request.queryParameters["pageToken"])
            call.respondData(HttpStatusCode.OK, page)
        }
    }
}
