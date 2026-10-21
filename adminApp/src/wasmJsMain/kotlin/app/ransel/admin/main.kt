package app.ransel.admin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.AdminSession
import app.ransel.admin.data.RefreshTokenStore
import app.ransel.admin.data.createHttpClient
import app.ransel.admin.platform.apiOrigin
import app.ransel.admin.platform.sessionGet
import app.ransel.admin.platform.sessionRemove
import app.ransel.admin.platform.sessionSet

/** The refresh token in this tab's sessionStorage (no "remember me" in Karung). */
private object SessionStorageTokenStore : RefreshTokenStore {
    private const val KEY = "karung.refreshToken"
    override fun read(): String? = sessionGet(KEY).takeIf { it.isNotEmpty() }
    override fun write(token: String) = sessionSet(KEY, token)
    override fun clear() = sessionRemove(KEY)
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val api = AdminApi(createHttpClient(), apiOrigin())
    val session = AdminSession(api, SessionStorageTokenStore)
    ComposeViewport { AdminApp(api, session) }
}
