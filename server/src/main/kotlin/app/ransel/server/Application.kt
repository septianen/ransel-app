package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.PhotoSigner
import app.ransel.server.ai.PlacePhotoService
import app.ransel.server.ai.RecommendationService
import app.ransel.server.ai.aiServicesFrom
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
import app.ransel.server.routes.placePhotoRoutes
import app.ransel.server.routes.recommendationRoutes
import app.ransel.server.routes.syncRoutes
import app.ransel.server.routes.tripRoutes
import app.ransel.server.routes.wellKnownRoutes
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(
    config: AppConfig = AppConfig.fromEnv(),
    emailSender: EmailSender = LoggingEmailSender(config.isProduction),
    ai: AiServices? = null,
) {
    val pool = DatabaseFactory.connect(config)
    TemplateSeed.run()
    monitor.subscribe(ApplicationStopped) { (pool as? HikariDataSource)?.close() }

    val (aiServices, aiHttp) = if (ai != null) ai to null else aiServicesFrom(config)
    monitor.subscribe(ApplicationStopped) { aiHttp?.close() }
    val photoSigner = PhotoSigner(config.photoSigningSecret)
    val recommendations = RecommendationService(config, aiServices, photoSigner = photoSigner)
    val placePhotos = PlacePhotoService(aiServices.places, recommendations.metrics)
    // Its own scope on a real dispatcher: it must not run on (and be sped up by) the host application's scheduler.
    val maintenance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    monitor.subscribe(ApplicationStopped) { maintenance.cancel() }
    maintenance.launch {
        // Hourly: drop expired cache rows and log the AI counters (cost visibility, TRD "Observability").
        while (isActive) {
            delay(1.hours)
            runCatching { recommendations.purgeExpiredCache() }
            recommendations.metrics.logSummary()
        }
    }

    val tokens = TokenService(config)
    val hasher = PasswordHasher(config.bcryptCost)
    val auth = AuthService(config, hasher, tokens)
    config.seedUsers.forEach(auth::seed)
    val passwordReset = PasswordResetService(config, hasher, tokens, emailSender, this)

    configureCallLogging()
    configureCors(config.corsOrigins)
    configureSerialization()
    configureStatusPages()
    configureRateLimit(config)
    configureAuth(config, tokens)

    routing {
        wellKnownRoutes(config)
        route(ApiRoutes.BASE) {
            healthRoutes()
            authRoutes(auth, passwordReset)
            placePhotoRoutes(photoSigner, placePhotos)
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
                recommendationRoutes(recommendations)
            }
        }
    }
}
