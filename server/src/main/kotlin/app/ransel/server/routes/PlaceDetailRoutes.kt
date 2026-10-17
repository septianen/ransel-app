package app.ransel.server.routes

import app.ransel.server.ai.PlaceDetailService
import app.ransel.server.plugins.SuggestionsRateLimit
import app.ransel.shared.api.ApiRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Opens a place again from the itinerary. Query: `name` (the item's title, used only when the cache has no row to
 * rebuild from) and `currency` (the trip's base currency, for the cost estimate). Signed in users only: the text
 * is our own and not tied to a trip. Shares the Explore form's per-user hourly limit because a missing row costs an LLM call.
 */
fun Route.placeDetailRoutes(service: PlaceDetailService) {
    rateLimit(SuggestionsRateLimit) {
        get("/places/{placeId}/detail") {
            val q = call.request.queryParameters
            val dto = service.get(call.parameters["placeId"].orEmpty(), q["name"], q["currency"])
            call.respondData(HttpStatusCode.OK, dto)
        }
    }
}
