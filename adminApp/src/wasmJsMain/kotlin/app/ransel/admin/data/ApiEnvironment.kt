package app.ransel.admin.data

import androidx.compose.runtime.staticCompositionLocalOf

/** One RANSEL server Karung can manage. [id] matches the server's `APP_ENV` (`production`, `staging`, `development`). */
data class ApiEnvironment(val id: String, val origin: String) {
    val isProduction: Boolean get() = id == PRODUCTION

    companion object {
        const val PRODUCTION = "production"
        const val STAGING = "staging"
        const val DEVELOPMENT = "development"
    }
}

/**
 * The servers Karung may talk to from this page (scheme + host + port, no `/api/v1`):
 *  - `karung.mietek.tech`: production and staging, picked on the sign-in screen or in the sidebar;
 *  - `localhost` / `127.0.0.1`: the local server on port 8080;
 *  - otherwise `window.RANSEL_API_ORIGIN` when set, else the page's own origin.
 * The first one is the default.
 */
fun environmentsFor(hostname: String, pageOrigin: String, configuredOrigin: String): List<ApiEnvironment> = when {
    hostname == "karung.mietek.tech" -> listOf(
        ApiEnvironment(ApiEnvironment.PRODUCTION, "https://ranselapi.mietek.tech"),
        ApiEnvironment(ApiEnvironment.STAGING, "https://ranselapi-dev.mietek.tech"),
    )
    configuredOrigin.isNotBlank() -> listOf(ApiEnvironment(ApiEnvironment.PRODUCTION, configuredOrigin))
    hostname == "localhost" || hostname == "127.0.0.1" -> listOf(ApiEnvironment(ApiEnvironment.DEVELOPMENT, "http://$hostname:8080"))
    else -> listOf(ApiEnvironment(ApiEnvironment.PRODUCTION, pageOrigin))
}

/** The environment stored for this tab, or the default when nothing (or an unknown id) is stored. */
fun List<ApiEnvironment>.selected(storedId: String?): ApiEnvironment = firstOrNull { it.id == storedId } ?: first()

/** sessionStorage key of the refresh token: one per environment, so a production token is never sent to staging. */
fun refreshTokenKey(environment: ApiEnvironment) = "karung.refreshToken.${environment.id}"

/**
 * What the screens need to show and change the environment. Switching signs out first (decided with the owner), then
 * reloads the page on the other server; [switchTo] does the reload, the caller signs out.
 */
data class EnvironmentChoice(
    val all: List<ApiEnvironment>,
    val current: ApiEnvironment,
    val switchTo: (ApiEnvironment) -> Unit,
) {
    val canSwitch: Boolean get() = all.size > 1
}

val LocalEnvironments = staticCompositionLocalOf {
    val local = ApiEnvironment(ApiEnvironment.DEVELOPMENT, "http://localhost:8080")
    EnvironmentChoice(listOf(local), local) {}
}
