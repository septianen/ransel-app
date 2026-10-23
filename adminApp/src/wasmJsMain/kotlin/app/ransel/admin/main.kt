package app.ransel.admin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.AdminSession
import app.ransel.admin.data.EnvironmentChoice
import app.ransel.admin.data.LocalEnvironments
import app.ransel.admin.data.RefreshTokenStore
import app.ransel.admin.data.createHttpClient
import app.ransel.admin.data.refreshTokenKey
import app.ransel.admin.platform.currentEnvironment
import app.ransel.admin.platform.environments
import app.ransel.admin.platform.sessionGet
import app.ransel.admin.platform.sessionRemove
import app.ransel.admin.platform.sessionSet
import app.ransel.admin.platform.switchEnvironment

/** The refresh token in this tab's sessionStorage (no "remember me" in Karung), one per environment. */
private class SessionStorageTokenStore(private val key: String) : RefreshTokenStore {
    override fun read(): String? = sessionGet(key).takeIf { it.isNotEmpty() }
    override fun write(token: String) = sessionSet(key, token)
    override fun clear() = sessionRemove(key)
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Tokens stored under the single key of earlier versions belong to production; drop them.
    sessionRemove("karung.refreshToken")
    val all = environments()
    val environment = currentEnvironment(all)
    val api = AdminApi(createHttpClient(), environment.origin)
    val session = AdminSession(api, SessionStorageTokenStore(refreshTokenKey(environment)))
    val choice = EnvironmentChoice(all, environment, ::switchEnvironment)
    ComposeViewport {
        CompositionLocalProvider(LocalEnvironments provides choice) { AdminApp(api, session) }
    }
}
