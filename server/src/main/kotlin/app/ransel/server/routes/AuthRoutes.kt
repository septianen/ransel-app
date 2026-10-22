package app.ransel.server.routes

import app.ransel.server.plugins.AuthRateLimit
import app.ransel.server.plugins.VerificationEmailRateLimit
import app.ransel.server.service.AuthService
import app.ransel.server.service.EmailVerificationService
import app.ransel.server.service.MeService
import app.ransel.server.service.PasswordResetService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.ForgotPasswordRequest
import app.ransel.shared.dto.LoginRequest
import app.ransel.shared.dto.RefreshRequest
import app.ransel.shared.dto.RegisterRequest
import app.ransel.shared.dto.ResetPasswordRequest
import app.ransel.shared.dto.UpdateMeRequest
import app.ransel.shared.dto.VerifyEmailRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post

/** Public: no token needed. Register and login are rate limited per client address. */
fun Route.authRoutes(auth: AuthService, passwordReset: PasswordResetService, verification: EmailVerificationService) {
    rateLimit(AuthRateLimit) {
        post(ApiRoutes.AUTH_REGISTER) {
            call.respondData(HttpStatusCode.Created, auth.register(call.receive<RegisterRequest>()))
        }
        post(ApiRoutes.AUTH_LOGIN) {
            call.respondData(HttpStatusCode.OK, auth.login(call.receive<LoginRequest>()))
        }
        post(ApiRoutes.AUTH_PASSWORD_FORGOT) {
            passwordReset.forgot(call.receive<ForgotPasswordRequest>().email)
            call.respondNoContent() // always, whether or not the email has an account
        }
    }
    post(ApiRoutes.AUTH_REFRESH) {
        call.respondData(HttpStatusCode.OK, auth.refresh(call.receive<RefreshRequest>().refreshToken))
    }
    post(ApiRoutes.AUTH_PASSWORD_RESET) {
        val body = call.receive<ResetPasswordRequest>()
        passwordReset.reset(body.token, body.newPassword)
        call.respondNoContent()
    }
    // The link carries a 256-bit token, so this needs neither a session nor a rate limit.
    post(ApiRoutes.AUTH_EMAIL_VERIFY) {
        call.respondData(HttpStatusCode.OK, verification.verify(call.receive<VerifyEmailRequest>().token))
    }
    post(ApiRoutes.AUTH_LOGOUT) {
        auth.logout(call.receive<RefreshRequest>().refreshToken)
        call.respondNoContent()
    }
}

/** Needs a token. */
fun Route.meRoutes(verification: EmailVerificationService) {
    rateLimit(VerificationEmailRateLimit) {
        post(ApiRoutes.AUTH_EMAIL_RESEND) {
            verification.send(call.userId())
            call.respondNoContent() // also when already verified: then nothing is sent
        }
    }
    get(ApiRoutes.ME) {
        call.respondData(HttpStatusCode.OK, MeService.get(call.userId()))
    }
    patch(ApiRoutes.ME) {
        call.respondData(HttpStatusCode.OK, MeService.update(call.userId(), call.receive<UpdateMeRequest>()))
    }
}
