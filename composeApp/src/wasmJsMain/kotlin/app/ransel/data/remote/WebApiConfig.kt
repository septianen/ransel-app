package app.ransel.data.remote

@JsFun("() => window.location.origin")
private external fun browserOrigin(): String

@JsFun("() => window.location.hostname")
private external fun browserHostname(): String

/** Optional override, set before the app script in `index.html`: `window.RANSEL_API_ORIGIN = "https://api.ransel.app"`. */
@JsFun("() => (typeof window.RANSEL_API_ORIGIN === 'string' ? window.RANSEL_API_ORIGIN : '')")
private external fun configuredOrigin(): String

/**
 * Where the API lives (scheme + host + port, no `/api/v1`):
 *  1. `window.RANSEL_API_ORIGIN` when set;
 *  2. `http://localhost:8080` while developing (`localhost` / `127.0.0.1`), where the Ktor server runs;
 *  3. otherwise the page's own origin (the web app is served behind the same reverse proxy as the API).
 */
actual fun apiOrigin(): String {
    val configured = configuredOrigin()
    if (configured.isNotBlank()) return configured
    val host = browserHostname()
    return if (host == "localhost" || host == "127.0.0.1") "http://$host:8080" else browserOrigin()
}

actual fun deviceName(): String = "Web"
