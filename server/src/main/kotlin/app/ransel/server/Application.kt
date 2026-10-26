package app.ransel.server

import app.ransel.server.ai.AiMetrics
import app.ransel.server.ai.AiServices
import app.ransel.server.ai.EcbFxRates
import app.ransel.server.ai.ExploreSuggestionService
import app.ransel.server.ai.FxRates
import app.ransel.server.ai.NoFxRates
import app.ransel.server.ai.PhotoSigner
import app.ransel.server.ai.PlaceDetailService
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
import app.ransel.server.routes.adminRoutes
import app.ransel.server.routes.authRoutes
import app.ransel.server.routes.creditRoutes
import app.ransel.server.routes.balanceRoutes
import app.ransel.server.routes.kittyRoutes
import app.ransel.server.routes.paymentMethodRoutes
import app.ransel.server.routes.placeRoutes
import app.ransel.server.routes.fxRoutes
import app.ransel.server.service.FxRateService
import app.ransel.server.service.PlaceSearchService
import app.ransel.server.routes.receiptRoutes
import app.ransel.server.service.ReceiptService
import app.ransel.server.routes.exploreRoutes
import app.ransel.server.routes.expenseRoutes
import app.ransel.server.routes.healthRoutes
import app.ransel.server.routes.itineraryRoutes
import app.ransel.server.routes.commentRoutes
import app.ransel.server.routes.invitePreviewRoutes
import app.ransel.server.routes.linkPreviewRoutes
import app.ransel.server.routes.memberRoutes
import app.ransel.server.routes.pollRoutes
import app.ransel.server.routes.meRoutes
import app.ransel.server.routes.packingRoutes
import app.ransel.server.routes.packingTemplateRoutes
import app.ransel.server.routes.placeDetailRoutes
import app.ransel.server.routes.placePhotoRoutes
import app.ransel.server.routes.recommendationRoutes
import app.ransel.server.routes.votingRoutes
import app.ransel.server.routes.syncRoutes
import app.ransel.server.routes.tripRoutes
import app.ransel.server.routes.wellKnownRoutes
import app.ransel.server.security.EmailSender
import app.ransel.server.security.LoggingEmailSender
import app.ransel.server.security.PasswordHasher
import app.ransel.server.security.SmtpEmailSender
import app.ransel.server.security.TokenService
import app.ransel.server.service.AdminService
import app.ransel.server.service.AuthService
import app.ransel.server.service.EmailVerificationService
import app.ransel.server.service.CreditService
import app.ransel.server.net.LinkFetcher
import app.ransel.server.net.SafeLinkFetcher
import app.ransel.server.service.InviteService
import app.ransel.server.service.LinkPreviewService
import app.ransel.server.service.PasswordResetService
import app.ransel.server.service.TripEvents
import app.ransel.server.service.SyncService
import app.ransel.shared.api.ApiRoutes
import com.zaxxer.hikari.HikariDataSource
import app.ransel.server.push.FcmPushSender
import app.ransel.server.push.LoggingPushSender
import app.ransel.server.push.PushSender
import app.ransel.server.routes.notificationRoutes
import app.ransel.server.service.NotificationService
import app.ransel.server.service.Notify
import app.ransel.server.service.ReminderScheduler
import app.ransel.server.service.RoundService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.engine.okhttp.OkHttp
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
    emailSender: EmailSender = config.smtp?.let(::SmtpEmailSender) ?: LoggingEmailSender(config.isProduction),
    ai: AiServices? = null,
    /** Null = ECB rates when the real AI clients are used, none in tests. */
    fx: FxRates? = null,
    /** Null = FCM when it is configured, otherwise pushes are only logged. */
    push: PushSender? = null,
    /** Null = the SSRF-safe fetcher; tests pass a fake so they need no network. */
    linkFetcher: LinkFetcher? = null,
) {
    val pool = DatabaseFactory.connect(config)
    TemplateSeed.run()
    monitor.subscribe(ApplicationStopped) { (pool as? HikariDataSource)?.close() }

    val (aiServices, aiHttp) = if (ai != null) ai to null else aiServicesFrom(config)
    monitor.subscribe(ApplicationStopped) { aiHttp?.close() }
    val photoSigner = PhotoSigner(config.photoSigningSecret)
    val aiMetrics = AiMetrics()
    val placePhotos = PlacePhotoService(aiServices.places, aiMetrics)
    // Its own scope on a real dispatcher: it must not run on (and be sped up by) the host application's scheduler.
    val maintenance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    monitor.subscribe(ApplicationStopped) { maintenance.cancel() }
    val linkPreviews = LinkPreviewService(linkFetcher ?: SafeLinkFetcher())

    // Explore form: destination interests (cached per destination + month) and the price level from a trip budget.
    val fxHttp = if (fx == null && ai == null) HttpClient(OkHttp) else null
    monitor.subscribe(ApplicationStopped) { fxHttp?.close() }
    val ecb = fxHttp?.let { EcbFxRates(it) }
    val suggestions = ExploreSuggestionService(aiServices.llm, fx ?: ecb ?: NoFxRates, aiMetrics)
    val fxRates = fx ?: ecb ?: NoFxRates
    val placeDetails = PlaceDetailService(aiServices, photoSigner, fxRates, aiMetrics)
    val credits = CreditService(config)
    val rounds = RoundService(config, photoSigner)
    val recommendations = RecommendationService(config, aiServices, aiMetrics, photoSigner, suggestions, fxRates, placeDetails, credits, rounds)
    // Expense rates: the ECB 90-day history, once a day (tests have no network: they insert rows).
    val fxDaily = FxRateService(fxHttp?.let { http -> suspend { http.get(FxRateService.HISTORY_URL).body<String>() } })
    if (ecb != null) {
        maintenance.launch {
            while (isActive) {
                ecb.refresh()
                fxDaily.refresh()
                delay(24.hours)
            }
        }
    }
    // A new or moved trip gets its destination interests ready before the user opens Explore.
    TripEvents.onTripSaved = { destination, startDate ->
        maintenance.launch {
            runCatching { suggestions.warm(destination, "%04d-%02d".format(startDate.year, startDate.monthNumber)) }
        }
    }
    maintenance.launch {
        // Hourly: drop expired cache rows and log the AI counters (cost visibility, TRD "Observability").
        while (isActive) {
            delay(1.hours)
            runCatching { recommendations.purgeExpiredCache() }
            runCatching { suggestions.purgeExpired() }
            runCatching { placeDetails.purgeExpired() }
            runCatching { Notify.service?.purge() }
            runCatching { linkPreviews.purge() }
            recommendations.metrics.logSummary()
        }
    }

    val pushHttp = if (push == null && config.fcmServiceAccountJson.isNotBlank()) HttpClient(OkHttp) else null
    monitor.subscribe(ApplicationStopped) { pushHttp?.close() }
    val pushSender = push ?: pushHttp?.let { FcmPushSender(it, config.fcmProjectId, config.fcmServiceAccountJson) } ?: LoggingPushSender()
    val notifications = NotificationService(config, pushSender)
    Notify.service = notifications
    monitor.subscribe(ApplicationStopped) { Notify.service = null }
    val reminders = ReminderScheduler(config, notifications)
    maintenance.launch {
        // The outbox: pushes of notifications that already committed.
        while (isActive) {
            runCatching { notifications.dispatchPending() }.onFailure { environment.log.warn("push dispatch failed: {}", it.javaClass.simpleName) }
            delay(config.pushDispatchIntervalMillis)
        }
    }
    maintenance.launch {
        while (isActive) {
            runCatching { reminders.tick() }.onFailure { environment.log.warn("reminder tick failed: {}", it.javaClass.simpleName) }
            runCatching { rounds.tick() }.onFailure { environment.log.warn("voting tick failed: {}", it.javaClass.simpleName) }
            delay(60_000)
        }
    }

    val tokens = TokenService(config)
    val hasher = PasswordHasher(config.bcryptCost)
    val verification = EmailVerificationService(config, tokens, emailSender, credits, this)
    val auth = AuthService(config, hasher, tokens, verification)
    config.seedUsers.forEach(auth::seed)
    val passwordReset = PasswordResetService(config, hasher, tokens, emailSender, this)
    val admin = AdminService(config, credits, passwordReset, hasher, tokens)

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
            authRoutes(auth, passwordReset, verification)
            placePhotoRoutes(photoSigner, placePhotos)
            val invites = InviteService(config)
            invitePreviewRoutes(invites)
            authenticate(JWT_AUTH) {
                meRoutes(verification)
                tripRoutes()
                itineraryRoutes()
                packingRoutes()
                expenseRoutes()
                syncRoutes(SyncService())
                memberRoutes(invites)
                commentRoutes()
                pollRoutes { user, poll, body -> recommendations.searchForPoll(user, poll, body) }
                linkPreviewRoutes(linkPreviews)
                balanceRoutes()
                kittyRoutes()
                paymentMethodRoutes()
                receiptRoutes(ReceiptService(aiServices, credits))
                packingTemplateRoutes()
                recommendationRoutes(recommendations)
                votingRoutes(rounds)
                creditRoutes(credits)
                notificationRoutes(notifications)
                exploreRoutes(suggestions)
                placeRoutes(PlaceSearchService(aiServices.placeSearch))
                fxRoutes(fxDaily)
                placeDetailRoutes(placeDetails)
                adminRoutes(admin)
            }
        }
    }
}
