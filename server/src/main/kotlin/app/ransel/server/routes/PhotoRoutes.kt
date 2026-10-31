package app.ransel.server.routes

import app.ransel.server.plugins.PhotoRateLimit
import app.ransel.server.plugins.PhotoUploadRateLimit
import app.ransel.server.service.PhotoService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.domain.PHOTO_MAX_BYTES
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/** Trip covers and profile photos (B19.1); needs a token. The body is the JPEG itself. */
fun Route.photoRoutes(photos: PhotoService) {
    rateLimit(PhotoUploadRateLimit) {
        put(ApiRoutes.tripCover("{id}")) {
            call.respondData(HttpStatusCode.OK, photos.setCover(call.userId(), call.tripId(), call.photoType(), call.photoBytes()))
        }
        put(ApiRoutes.ME_AVATAR) {
            call.respondData(HttpStatusCode.OK, photos.setAvatar(call.userId(), call.photoType(), call.photoBytes()))
        }
    }
    delete(ApiRoutes.tripCover("{id}")) {
        call.respondData(HttpStatusCode.OK, photos.clearCover(call.userId(), call.tripId()))
    }
    delete(ApiRoutes.ME_AVATAR) {
        call.respondData(HttpStatusCode.OK, photos.clearAvatar(call.userId()))
    }
}

/** Public: an `<img>` or Coil cannot send Authorization; the random id is the credential. A bad id is a bare 404. */
fun Route.imageFileRoutes(photos: PhotoService) {
    rateLimit(PhotoRateLimit) {
        get(ApiRoutes.imageFile("{imageId}")) {
            val file = photos.file(call.parameters["imageId"].orEmpty())
            if (file == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            // a new photo gets a new id, so the bytes behind one never change
            call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
            call.response.header(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
            call.respondFile(file.toFile())
        }
    }
}

private fun ApplicationCall.photoType(): String = request.contentType().let { "${it.contentType}/${it.contentSubtype}" }

// one byte over the limit is enough to know the photo is too large
private suspend fun ApplicationCall.photoBytes(): ByteArray = receiveChannel().readRemaining(PHOTO_MAX_BYTES + 1L).readByteArray()
