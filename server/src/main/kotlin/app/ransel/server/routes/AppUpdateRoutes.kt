package app.ransel.server.routes

import app.ransel.server.AppConfig
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.SettingsService
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.domain.appUpdateStatus
import app.ransel.shared.dto.AppUpdateDto
import app.ransel.shared.model.AppUpdateStatus
import app.ransel.shared.model.DevicePlatform
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /app-update?platform=&version=` (public): whether the installed app should offer or insist on an update, from
 * the minimum Karung sets per platform. Read from the live settings, so a change applies to the next launch. A
 * platform with no store link answers NONE: a dialog nobody can act on would lock people out.
 */
fun Route.appUpdateRoutes(settings: SettingsService) {
    get(ApiRoutes.APP_UPDATE) {
        val platform = call.request.queryParameters["platform"]?.let { raw -> DevicePlatform.entries.firstOrNull { it.name == raw } }
        val version = call.request.queryParameters["version"]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 }
        val problems = buildMap {
            if (platform == null) put("platform", "INVALID")
            if (version == null) put("version", "REQUIRED")
        }
        if (problems.isNotEmpty()) throw validationFailed(problems)

        val config: AppConfig.AppUpdate = settings.current.let { if (platform == DevicePlatform.ANDROID) it.androidUpdate else it.iosUpdate }
        val status = if (config.storeUrl.isBlank()) AppUpdateStatus.NONE else appUpdateStatus(version!!, config.minVersion, config.force)
        val body = if (status == AppUpdateStatus.NONE) AppUpdateDto(AppUpdateStatus.NONE)
        else AppUpdateDto(status, latestVersion = config.minVersion, storeUrl = config.storeUrl)
        call.respondData(HttpStatusCode.OK, body)
    }
}
