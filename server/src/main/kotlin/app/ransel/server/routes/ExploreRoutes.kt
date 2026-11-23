package app.ransel.server.routes

import app.ransel.server.ai.ExploreSuggestionService
import app.ransel.server.plugins.SuggestionsRateLimit
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.acceptLanguage
import app.ransel.server.service.requireVerifiedEmail
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.exploreRoutes(service: ExploreSuggestionService) {
    rateLimit(SuggestionsRateLimit) {
        get(ApiRoutes.EXPLORE_SUGGESTIONS) {
            requireVerifiedEmail(call.userId())
            val q = call.request.queryParameters
            // "en-US,en;q=0.9" -> en; anything but English falls back to Indonesian, the default UI language.
            val language = if (call.request.acceptLanguage()?.trim()?.lowercase()?.startsWith("en") == true) "en" else "id"
            // a trip that is not saved yet may send its budget to get the price level it starts with
            val draft = q["budgetMinor"]?.toLongOrNull()?.takeIf { it > 0 }?.let { minor ->
                val currency = q["currency"]?.trim()?.uppercase()?.takeIf { it.length == 3 } ?: return@let null
                ExploreSuggestionService.DraftBudget(minor, currency, q["days"]?.toIntOrNull()?.coerceIn(1, 366))
            }
            val response = service.suggestions(call.userId(), q["tripId"], q["destination"], q["month"], language, draft)
            call.respondData(HttpStatusCode.OK, response)
        }
    }
}
