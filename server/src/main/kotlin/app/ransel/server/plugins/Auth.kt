package app.ransel.server.plugins

import app.ransel.server.AppConfig
import app.ransel.server.security.TokenService
import app.ransel.shared.api.ApiError
import app.ransel.shared.api.ApiErrorResponse
import app.ransel.shared.api.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.response.respond
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

const val JWT_AUTH = "jwt"
val AuthRateLimit = RateLimitName("auth")
val PhotoRateLimit = RateLimitName("photo")
val InvitePreviewRateLimit = RateLimitName("invite-preview")
val InviteAcceptRateLimit = RateLimitName("invite-accept")
val LinkPreviewRateLimit = RateLimitName("link-preview")
val SuggestionsRateLimit = RateLimitName("suggestions")
val RedeemRateLimit = RateLimitName("redeem")
val AdminRateLimit = RateLimitName("admin")
val VerificationEmailRateLimit = RateLimitName("verification-email")
val PlaceSearchRateLimit = RateLimitName("place-search")
val GuestRateLimit = RateLimitName("guest")
val GuestIdentityRateLimit = RateLimitName("guest-identity")

/** [onAuthenticated] gets the user of every request with a valid token (`users.last_seen_at`). */
fun Application.configureAuth(config: AppConfig, tokens: TokenService, onAuthenticated: (UUID) -> Unit = {}) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "ransel"
            verifier(tokens.verifier)
            validate { credential ->
                credential.payload.subject?.let { subject ->
                    runCatching { UUID.fromString(subject) }.getOrNull()?.let(onAuthenticated)
                    JWTPrincipal(credential.payload)
                }
            }
            challenge { _, _ ->
                call.attributes.put(ErrorCodeKey, ErrorCode.UNAUTHORIZED)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorResponse(ApiError(ErrorCode.UNAUTHORIZED, "Sign in required")),
                )
            }
        }
    }
}

/** Behind Caddy the client address arrives in X-Forwarded-For (Caddy replaces untrusted values). */
fun Application.configureRateLimit(config: AppConfig) {
    install(XForwardedHeaders)
    install(RateLimit) {
        register(AuthRateLimit) {
            rateLimiter(limit = config.authRateLimitPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(SuggestionsRateLimit) {
            rateLimiter(limit = config.suggestionsRateLimitPerHour, refillPeriod = 1.hours)
            // Per user (the JWT subject), falling back to the client address; the route sits behind authenticate.
            requestKey { call -> call.principal<JWTPrincipal>()?.payload?.subject ?: call.request.origin.remoteHost }
        }
        register(RedeemRateLimit) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<JWTPrincipal>()?.payload?.subject ?: call.request.origin.remoteHost }
        }
        // Per user: the app counts down 45 s between resends; this stops a loop from flooding someone's inbox.
        register(VerificationEmailRateLimit) {
            rateLimiter(limit = 5, refillPeriod = 1.hours)
            requestKey { call -> call.principal<JWTPrincipal>()?.payload?.subject ?: call.request.origin.remoteHost }
        }
        // Per admin: Karung is a handful of people; this only stops a runaway script.
        register(AdminRateLimit) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<JWTPrincipal>()?.payload?.subject ?: call.request.origin.remoteHost }
        }
        // Each call makes the server fetch a page: per user, 30 a minute.
        register(LinkPreviewRateLimit) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<JWTPrincipal>()?.payload?.subject ?: call.request.origin.remoteHost }
        }
        // Every key press after the second character can reach Google: per user, 60 a minute.
        register(PlaceSearchRateLimit) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<JWTPrincipal>()?.payload?.subject ?: call.request.origin.remoteHost }
        }
        // The public invite preview needs no sign-in, so it is limited per address.
        register(InvitePreviewRateLimit) {
            rateLimiter(limit = config.invitePreviewRateLimitPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        // Per user: people join a handful of trips; this stops a script from guessing codes.
        register(InviteAcceptRateLimit) {
            rateLimiter(limit = 10, refillPeriod = 10.minutes)
            requestKey { call -> call.principal<JWTPrincipal>()?.payload?.subject ?: call.request.origin.remoteHost }
        }
        // The guest page is public: per address. Picking or writing a name is rarer, so it is held tighter.
        register(GuestRateLimit) {
            rateLimiter(limit = 120, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(GuestIdentityRateLimit) {
            rateLimiter(limit = 10, refillPeriod = 10.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(PhotoRateLimit) {
            rateLimiter(limit = config.photoRateLimitPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}
