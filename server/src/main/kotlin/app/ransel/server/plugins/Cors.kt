package app.ransel.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import java.net.URI

/** [origins] are full origins such as `http://localhost:8081` (the web app's dev server). */
fun Application.configureCors(origins: List<String>) {
    install(CORS) {
        origins.forEach { origin ->
            val uri = URI(origin)
            val host = if (uri.port > 0) "${uri.host}:${uri.port}" else uri.host
            allowHost(host, schemes = listOf(uri.scheme))
        }
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        // the guest page's token (public routes, no bearer)
        allowHeader(app.ransel.shared.api.ApiRoutes.GUEST_TOKEN_HEADER)
        allowNonSimpleContentTypes = true
    }
}
