package app.ransel.server

import app.ransel.server.db.DatabaseFactory
import app.ransel.server.db.TemplateSeed
import app.ransel.server.plugins.JWT_AUTH
import app.ransel.server.plugins.configureAuth
import app.ransel.server.plugins.configureCallLogging
import app.ransel.server.plugins.configureCors
import app.ransel.server.plugins.configureRateLimit
import app.ransel.server.plugins.configureSerialization
import app.ransel.server.plugins.configureStatusPages
import app.ransel.server.routes.authRoutes
import app.ransel.server.routes.balanceRoutes
import app.ransel.server.routes.expenseRoutes
import app.ransel.server.routes.healthRoutes
import app.ransel.server.routes.itineraryRoutes
import app.ransel.server.routes.memberRoutes
import app.ransel.server.routes.meRoutes
import app.ransel.server.routes.packingRoutes
import app.ransel.server.routes.packingTemplateRoutes
import app.ransel.server.routes.syncRoutes
import app.ransel.server.routes.tripRoutes
import app.ransel.server.security.EmailSender
import app.ransel.server.security.LoggingEmailSender
import app.ransel.server.security.PasswordHasher
import app.ransel.server.security.TokenService
import app.ransel.server.service.AuthService
import app.ransel.server.service.InviteService
import app.ransel.server.service.PasswordResetService
import app.ransel.server.service.SyncService
import app.ransel.shared.api.ApiRoutes
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(
    config: AppConfig = AppConfig.fromEnv(),
    emailSender: EmailSender = LoggingEmailSender(config.isProduction),
) {
    val pool = DatabaseFactory.connect(config)
    TemplateSeed.run()
    monitor.subscribe(ApplicationStopped) { (pool as? HikariDataSource)?.close() }

    val tokens = TokenService(config)
    val hasher = PasswordHasher(config.bcryptCost)
    val auth = AuthService(config, hasher, tokens)
    config.seedUser?.let(auth::seed)
    val passwordReset = PasswordResetService(config, hasher, tokens, emailSender, this)

    configureCallLogging()
    configureCors(config.corsOrigins)
    configureSerialization()
    configureStatusPages()
    configureRateLimit(config)
    configureAuth(config, tokens)

    routing {
        route(ApiRoutes.BASE) {
            healthRoutes()
            authRoutes(auth, passwordReset)
            authenticate(JWT_AUTH) {
                meRoutes()
                tripRoutes()
                itineraryRoutes()
                packingRoutes()
                expenseRoutes()
                syncRoutes(SyncService())
                memberRoutes(InviteService(config))
                balanceRoutes()
                packingTemplateRoutes()
            }
        }
    }
}
