package app.ransel.server.routes

import app.ransel.server.ai.PhotoSigner
import app.ransel.server.ai.PlacePhotoService
import app.ransel.server.plugins.PhotoRateLimit
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Outside the bearer block on purpose: an `<img>` loader cannot send Authorization. The HMAC link is the credential.
 * Every failure is a bare 404 so a bad link and a place without a photo look the same and nothing is leaked.
 */
fun Route.placePhotoRoutes(signer: PhotoSigner, photos: PlacePhotoService) {
    rateLimit(PhotoRateLimit) {
        get("/places/photo/{placeId}") {
            val placeId = call.parameters["placeId"].orEmpty()
            val valid = placeId.isNotEmpty() && placeId.length <= 256 &&
                signer.verify(placeId, call.request.queryParameters["exp"]?.toLongOrNull(), call.request.queryParameters["sig"])
            val photo = if (valid) photos.photo(placeId) else null
            if (photo == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.response.header("Cache-Control", "public, max-age=86400")
                call.respondBytes(photo.bytes, ContentType.parse(photo.contentType))
            }
        }
    }
}
