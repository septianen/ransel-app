package app.ransel.server

import app.ransel.server.db.DatabaseFactory
import app.ransel.server.plugins.configureCallLogging
import app.ransel.server.plugins.configureCors
import app.ransel.server.plugins.configureSerialization
import app.ransel.server.plugins.configureStatusPages
import app.ransel.server.routes.healthRoutes
import app.ransel.shared.api.ApiRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.fromEnv()) {
    DatabaseFactory.connect(config)
    configureCallLogging()
    configureCors(config.corsOrigins)
    configureSerialization()
    configureStatusPages()
    routing {
        route(ApiRoutes.BASE) {
            healthRoutes()
        }
    }
}
