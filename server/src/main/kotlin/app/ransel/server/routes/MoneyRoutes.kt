package app.ransel.server.routes

import app.ransel.server.plugins.tripNotFound
import app.ransel.server.service.PaymentMethodService
import app.ransel.server.service.ReceiptService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.PaymentMethodInput
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.util.UUID

fun Route.paymentMethodRoutes() {
    route(ApiRoutes.ME_PAYMENT_METHODS) {
        get { call.respondData(HttpStatusCode.OK, PaymentMethodService.list(call.userId())) }
        post {
            val body = call.receive<PaymentMethodInput>()
            call.respondData(HttpStatusCode.Created, PaymentMethodService.create(call.userId(), body))
        }
        patch("/{methodId}") {
            val body = call.receive<PaymentMethodInput>()
            call.respondData(HttpStatusCode.OK, PaymentMethodService.update(call.userId(), call.pathUuid("methodId", "Payment method"), body))
        }
        delete("/{methodId}") {
            PaymentMethodService.delete(call.userId(), call.pathUuid("methodId", "Payment method"))
            call.respondNoContent()
        }
    }
}

fun Route.receiptRoutes(receipts: ReceiptService) {
    post(ApiRoutes.RECEIPTS_PARSE) {
        val tripId = call.request.queryParameters["tripId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw tripNotFound()
        // one byte over the limit is enough to know the photo is too large
        val bytes = call.receiveChannel().readRemaining(ReceiptService.MAX_BYTES + 1L).readByteArray()
        val type = call.request.contentType().let { "${it.contentType}/${it.contentSubtype}" }
        call.respondData(HttpStatusCode.OK, receipts.parse(call.userId(), tripId, bytes, type))
    }
}
