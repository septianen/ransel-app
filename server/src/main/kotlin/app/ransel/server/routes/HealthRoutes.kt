package app.ransel.server.routes

import app.ransel.shared.api.ApiResponse
import app.ransel.shared.api.ApiRoutes
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun Route.healthRoutes() {
    get(ApiRoutes.HEALTH) {
        call.respond(ApiResponse(JsonObject(mapOf("status" to JsonPrimitive("ok")))))
    }
}
