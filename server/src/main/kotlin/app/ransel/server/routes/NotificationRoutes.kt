package app.ransel.server.routes

import app.ransel.server.plugins.requireValid
import app.ransel.server.service.NotificationService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.MarkReadRequest
import app.ransel.shared.dto.RegisterDeviceRequest
import app.ransel.shared.dto.UpdateNotificationSettingsRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post

fun Route.notificationRoutes(service: NotificationService) {
    post(ApiRoutes.DEVICES) {
        service.registerDevice(call.userId(), call.receive<RegisterDeviceRequest>())
        call.respondNoContent()
    }
    delete(ApiRoutes.DEVICES) {
        val token = call.request.queryParameters["token"].orEmpty()
        requireValid(if (token.isBlank()) mapOf("token" to "REQUIRED") else emptyMap())
        service.unregisterDevice(call.userId(), token)
        call.respondNoContent()
    }
    get(ApiRoutes.NOTIFICATIONS) {
        val q = call.request.queryParameters
        call.respondData(HttpStatusCode.OK, service.list(call.userId(), q["limit"]?.toIntOrNull() ?: 30, q["before"]))
    }
    post(ApiRoutes.NOTIFICATIONS_READ) {
        service.markRead(call.userId(), call.receive<MarkReadRequest>().ids)
        call.respondNoContent()
    }
    get(ApiRoutes.NOTIFICATION_SETTINGS) { call.respondData(HttpStatusCode.OK, service.settings(call.userId())) }
    patch(ApiRoutes.NOTIFICATION_SETTINGS) {
        call.respondData(HttpStatusCode.OK, service.updateSettings(call.userId(), call.receive<UpdateNotificationSettingsRequest>()))
    }
}
