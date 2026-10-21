package app.ransel.server.routes

import app.ransel.server.plugins.AdminRateLimit
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.AdminPrincipal
import app.ransel.server.service.AdminService
import app.ransel.server.service.UserFilter
import app.ransel.server.service.userId
import app.ransel.shared.api.AdminRoutes
import app.ransel.shared.api.PageMeta
import app.ransel.shared.dto.AdjustCreditsRequest
import app.ransel.shared.model.AdminAction
import app.ransel.shared.model.UserStatus
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.datetime.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * Admin API (docs/specs/admin-spec.md). Sits inside `authenticate(JWT_AUTH)`; every handler starts with [admin], which
 * answers 403 for any account that is not in `ADMIN_EMAILS`.
 */
fun Route.adminRoutes(service: AdminService) {
    fun ApplicationCall.admin(): AdminPrincipal = service.requireAdmin(userId())
    fun ApplicationCall.user() = pathUuid("userId", "User")
    fun ApplicationCall.page() = request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    rateLimit(AdminRateLimit) {
        get(AdminRoutes.ME) { call.respondData(HttpStatusCode.OK, service.me(call.admin())) }

        get(AdminRoutes.USERS) {
            call.admin()
            val page = call.page()
            val (rows, total) = service.users(call.userFilter(), page, AdminService.PAGE_SIZE)
            call.respondData(HttpStatusCode.OK, rows, PageMeta(page, AdminService.PAGE_SIZE, total))
        }
        get(AdminRoutes.USERS_EXPORT) {
            val admin = call.admin()
            val csv = service.exportUsers(admin, call.userFilter())
            val day = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Asia/Jakarta")).format(java.time.Instant.now())
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "ransel-users-$day.csv").toString(),
            )
            call.respondText(csv, ContentType.Text.CSV.withParameter("charset", "utf-8"))
        }
        get(AdminRoutes.user("{userId}")) {
            call.admin()
            call.respondData(HttpStatusCode.OK, service.user(call.user()))
        }
        get(AdminRoutes.userLedger("{userId}")) {
            call.admin()
            val page = call.page()
            val (rows, total) = service.ledger(call.user(), page, AdminService.PAGE_SIZE)
            call.respondData(HttpStatusCode.OK, rows, PageMeta(page, AdminService.PAGE_SIZE, total))
        }
        post(AdminRoutes.userCredits("{userId}")) {
            val admin = call.admin()
            val body = call.receive<AdjustCreditsRequest>()
            call.respondData(HttpStatusCode.OK, service.adjustCredits(admin, call.user(), body))
        }
        post(AdminRoutes.userSignOutAll("{userId}")) {
            val admin = call.admin()
            call.respondData(HttpStatusCode.OK, service.signOutAll(admin, call.user()))
        }
        post(AdminRoutes.userDisable("{userId}")) {
            val admin = call.admin()
            call.respondData(HttpStatusCode.OK, service.disable(admin, call.user()))
        }
        post(AdminRoutes.userEnable("{userId}")) {
            val admin = call.admin()
            call.respondData(HttpStatusCode.OK, service.enable(admin, call.user()))
        }
        post(AdminRoutes.userPasswordReset("{userId}")) {
            val admin = call.admin()
            service.sendPasswordReset(admin, call.user())
            call.respondNoContent()
        }
        get(AdminRoutes.AUDIT) {
            call.admin()
            val page = call.page()
            val action = call.request.queryParameters["action"]?.let { raw ->
                AdminAction.entries.firstOrNull { it.name == raw } ?: throw validationFailed(mapOf("action" to "UNKNOWN"))
            }
            val (rows, total) = service.auditLog(call.request.queryParameters["targetId"], action, page, AdminService.PAGE_SIZE)
            call.respondData(HttpStatusCode.OK, rows, PageMeta(page, AdminService.PAGE_SIZE, total))
        }
    }
}

/** Query of GET /admin/users and its export; a bad value is 422 on that parameter. */
private fun ApplicationCall.userFilter(): UserFilter {
    val q = request.queryParameters
    val errors = mutableMapOf<String, String>()
    val status = q["status"]?.let { raw -> UserStatus.entries.firstOrNull { it.name == raw } ?: null.also { errors["status"] = "UNKNOWN" } }
    val platform = q["platform"]?.let { raw -> raw.takeIf { it in setOf("ANDROID", "IOS", "WEB") } ?: null.also { errors["platform"] = "UNKNOWN" } }
    fun date(name: String) = q[name]?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() ?: null.also { errors[name] = "INVALID_DATE" } }
    val from = date("joinedFrom")
    val to = date("joinedTo")
    val noCredits = q["noCredits"]?.let { raw ->
        raw.toBooleanStrictOrNull() ?: false.also { errors["noCredits"] = "INVALID_BOOLEAN" }
    } ?: false
    if (errors.isNotEmpty()) throw validationFailed(errors)
    return UserFilter(q["query"], status, platform, from, to, noCredits)
}
