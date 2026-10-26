package app.ransel.server.routes

import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.FxRateService
import app.ransel.shared.api.ApiRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.datetime.LocalDate

fun Route.fxRoutes(service: FxRateService) {
    get(ApiRoutes.FX_RATE) {
        val q = call.request.queryParameters
        val date = q["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: throw validationFailed(mapOf("date" to "INVALID_DATE")) }
            ?: throw validationFailed(mapOf("date" to "REQUIRED"))
        call.respondData(HttpStatusCode.OK, service.rate(q["from"].orEmpty(), q["to"].orEmpty(), date))
    }
}
