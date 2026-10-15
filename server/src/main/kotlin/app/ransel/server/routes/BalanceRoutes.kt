package app.ransel.server.routes

import app.ransel.server.service.BalanceService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.CreateSettlementRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.balanceRoutes() {
    get("${ApiRoutes.TRIPS}/{id}/balances") {
        call.respondData(HttpStatusCode.OK, BalanceService.balances(call.userId(), call.tripId()))
    }
    get("${ApiRoutes.TRIPS}/{id}/settlements") {
        call.respondData(HttpStatusCode.OK, BalanceService.listSettlements(call.userId(), call.tripId()))
    }
    post("${ApiRoutes.TRIPS}/{id}/settlements") {
        val body = call.receive<CreateSettlementRequest>()
        val id = bodyUuid(body.id, "id")
        call.respondData(HttpStatusCode.Created, BalanceService.createSettlement(call.userId(), call.tripId(), id, body))
    }
}
