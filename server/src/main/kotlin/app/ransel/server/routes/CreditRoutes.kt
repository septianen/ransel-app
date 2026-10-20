package app.ransel.server.routes

import app.ransel.server.plugins.RedeemRateLimit
import app.ransel.server.service.CreditService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.RedeemVoucherRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.creditRoutes(service: CreditService) {
    get(ApiRoutes.CREDITS) { call.respondData(HttpStatusCode.OK, service.credits(call.userId())) }
    get(ApiRoutes.CREDITS_LEDGER) {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        call.respondData(HttpStatusCode.OK, service.ledger(call.userId(), limit))
    }
    get(ApiRoutes.CREDIT_PACKAGES) { call.respondData(HttpStatusCode.OK, service.packages()) }
    // Rate limited per user so a voucher code cannot be guessed.
    rateLimit(RedeemRateLimit) {
        post(ApiRoutes.CREDITS_REDEEM) {
            call.respondData(HttpStatusCode.OK, service.redeem(call.userId(), call.receive<RedeemVoucherRequest>().code))
        }
    }
}
