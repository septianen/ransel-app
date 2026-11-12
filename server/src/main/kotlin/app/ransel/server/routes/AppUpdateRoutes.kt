package app.ransel.server.routes

import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.SettingsService
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.domain.appUpdateStatus
import app.ransel.shared.dto.AppUpdateDto
import app.ransel.shared.model.AppUpdateStatus
import app.ransel.shared.model.DevicePlatform
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /app-update?platform=&version=` (public): whether this Android install should offer or insist on an update.
 * Compared on `VERSION_NAME` ("1.2.3"), from the minimum and latest Karung sets. Read from the live settings, so a
 * change applies to the next launch. No store link answers NONE: a gate nobody can leave would lock people out.
 * iOS answers NONE (Android only). Not cached: Karung can raise the floor and the next cold start must see it.
 *
 * [AppUpdateDto.latestVersion] is the latest when set, otherwise the minimum, so an app from 1 Nov 2026 (which shows
 * that one field in its dialog) still names a real version.
 */
fun Route.appUpdateRoutes(settings: SettingsService) {
    get(ApiRoutes.APP_UPDATE) {
        val platform = call.request.queryParameters["platform"]?.let { raw -> DevicePlatform.entries.firstOrNull { it.name == raw } }
        val version = call.request.queryParameters["version"]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 }
        if (platform == null) throw validationFailed(mapOf("platform" to "INVALID"))
        call.response.header(HttpHeaders.CacheControl, "no-store")
        if (platform != DevicePlatform.ANDROID) {
            call.respondData(HttpStatusCode.OK, AppUpdateDto(AppUpdateStatus.NONE))
            return@get
        }
        if (version == null) throw validationFailed(mapOf("version" to "REQUIRED"))

        val config = settings.current.androidUpdate
        val status = if (config.storeUrl.isBlank()) AppUpdateStatus.NONE else appUpdateStatus(version, config.minVersion, config.latestVersion)
        val body = if (status == AppUpdateStatus.NONE) AppUpdateDto(AppUpdateStatus.NONE)
        else AppUpdateDto(
            status = status,
            latestVersion = config.latestVersion.ifBlank { config.minVersion },
            storeUrl = config.storeUrl,
            minVersion = config.minVersion.ifBlank { null },
            message = config.message.ifBlank { null },
        )
        call.respondData(HttpStatusCode.OK, body)
    }
}
