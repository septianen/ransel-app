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
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.response.respond
import kotlin.time.Duration.Companion.minutes

const val JWT_AUTH = "jwt"
val AuthRateLimit = RateLimitName("auth")

fun Application.configureAuth(config: AppConfig, tokens: TokenService) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "ransel"
            verifier(tokens.verifier)
            validate { credential -> credential.payload.subject?.let { JWTPrincipal(credential.payload) } }
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
    }
}
