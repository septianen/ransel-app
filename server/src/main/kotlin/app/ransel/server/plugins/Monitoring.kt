package app.ransel.server.plugins

import app.ransel.server.service.ErrorCounter
import app.ransel.shared.api.ApiRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.PathSegmentOptionalParameterRouteSelector
import io.ktor.server.routing.PathSegmentParameterRouteSelector
import io.ktor.server.routing.PathSegmentTailcardRouteSelector
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingRoot
import io.ktor.util.AttributeKey
import java.util.UUID

/** The matched route as a template without the API prefix: `/trips/{id}/expenses`. */
val RouteTemplateKey = AttributeKey<String>("route.template")

/** Path segments of [node] and its parents; method, authentication and other selectors are left out. */
internal fun routeTemplate(node: RoutingNode): String {
    val segments = generateSequence(node) { it.parent }.mapNotNull { n ->
        when (val selector = n.selector) {
            is PathSegmentConstantRouteSelector -> selector.value
            is PathSegmentParameterRouteSelector -> "{${selector.name}}"
            is PathSegmentOptionalParameterRouteSelector -> "{${selector.name}?}"
            is PathSegmentTailcardRouteSelector -> "{...}"
            else -> null
        }
    }.toList().asReversed()
    val path = "/" + segments.joinToString("/")
    return path.removePrefix(ApiRoutes.BASE).ifEmpty { "/" }
}

/** Every response that carries an `error.code` (StatusPages, the auth challenge) is counted for Karung. */
fun Application.configureErrorCounting(errors: ErrorCounter) {
    monitor.subscribe(RoutingRoot.RoutingCallStarted) { call ->
        call.attributes.put(RouteTemplateKey, routeTemplate(call.route))
    }
    install(
        createApplicationPlugin("ErrorCounting") {
            on(ResponseSent) { call ->
                val code = call.attributes.getOrNull(ErrorCodeKey) ?: return@on
                val template = call.attributes.getOrNull(RouteTemplateKey)
                // no route matched: never the raw path, which could be anything a client sends
                val route = if (template == null) "-" else "${call.request.httpMethod.value} $template"
                val user = call.principal<JWTPrincipal>()?.payload?.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                errors.record(code, route, call.response.status()?.value ?: 0, user)
            }
        },
    )
}
