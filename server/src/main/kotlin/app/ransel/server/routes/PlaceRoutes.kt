package app.ransel.server.routes

import app.ransel.server.plugins.PlaceSearchRateLimit
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.PlaceSearchService
import app.ransel.shared.api.ApiRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Destination search. The static paths are registered before `{placeId}`, and Ktor prefers them anyway. */
fun Route.placeRoutes(service: PlaceSearchService) {
    rateLimit(PlaceSearchRateLimit) {
        get(ApiRoutes.PLACES_AUTOCOMPLETE) {
            val q = call.request.queryParameters
            val near = q["near"]?.let { raw ->
                val parts = raw.split(',').mapNotNull { it.trim().toDoubleOrNull() }
                if (parts.size != 2 || parts[0] !in -90.0..90.0 || parts[1] !in -180.0..180.0) throw validationFailed(mapOf("near" to "INVALID"))
                parts[0] to parts[1]
            }
            call.respondData(HttpStatusCode.OK, service.autocomplete(q["q"].orEmpty(), q["session"], near))
        }
        get(ApiRoutes.PLACES_REVERSE) {
            val q = call.request.queryParameters
            val lat = q["lat"]?.toDoubleOrNull()
            val lng = q["lng"]?.toDoubleOrNull()
            if (lat == null || lng == null) throw validationFailed(mapOf("lat" to "REQUIRED"))
            call.respondData(HttpStatusCode.OK, service.reverse(lat, lng))
        }
        get("/places/{placeId}") {
            call.respondData(HttpStatusCode.OK, service.details(call.parameters["placeId"].orEmpty(), call.request.queryParameters["session"]))
        }
    }
    get(ApiRoutes.PLACES_POPULAR) {
        call.respondData(HttpStatusCode.OK, service.popular())
    }
}
