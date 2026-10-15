package app.ransel.server.routes

import app.ransel.server.service.ExpenseService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.ExpenseInput
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import app.ransel.server.plugins.validationFailed
import io.ktor.server.application.ApplicationCall

fun Route.expenseRoutes() {
    route("${ApiRoutes.TRIPS}/{id}/expenses") {
        get {
            call.respondData(HttpStatusCode.OK, ExpenseService.list(call.userId(), call.tripId()))
        }
        post {
            val body = call.receive<ExpenseInput>()
            val expenseId = bodyUuid(body.id, "id")
            call.respondData(HttpStatusCode.Created, ExpenseService.create(call.userId(), call.tripId(), expenseId, body))
        }
        get("/summary") {
            call.respondData(HttpStatusCode.OK, ExpenseService.summary(call.userId(), call.tripId()))
        }
        route("/{expenseId}") {
            put {
                val body = call.receive<ExpenseInput>()
                val expenseId = call.pathUuid("expenseId", "Expense")
                if (body.id != expenseId.toString()) throw validationFailed(mapOf("id" to "MUST_MATCH_PATH"))
                call.respondData(HttpStatusCode.OK, ExpenseService.replace(call.userId(), call.tripId(), expenseId, body))
            }
            delete {
                ExpenseService.delete(call.userId(), call.tripId(), call.pathUuid("expenseId", "Expense"))
                call.respondNoContent()
            }
        }
    }
}
