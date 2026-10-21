package app.ransel.admin.platform

/*
 * The few browser APIs Karung needs. Everything else is Compose.
 */

@JsFun("() => window.location.origin")
private external fun browserOrigin(): String

@JsFun("() => window.location.hostname")
private external fun browserHostname(): String

/** Optional override, set before the app script in `index.html`: `window.RANSEL_API_ORIGIN = "https://ranselapi.mietek.tech"`. */
@JsFun("() => (typeof window.RANSEL_API_ORIGIN === 'string' ? window.RANSEL_API_ORIGIN : '')")
private external fun configuredOrigin(): String

/**
 * Where the API lives (scheme + host + port, no `/api/v1`), same rules as the traveller web app:
 *  1. `window.RANSEL_API_ORIGIN` when set (production: `index.html` sets it for karung.mietek.tech);
 *  2. `http://localhost:8080` while developing;
 *  3. otherwise the page's own origin.
 */
fun apiOrigin(): String {
    val configured = configuredOrigin()
    if (configured.isNotBlank()) return configured
    val host = browserHostname()
    return if (host == "localhost" || host == "127.0.0.1") "http://$host:8080" else browserOrigin()
}

// sessionStorage, not localStorage: Karung has no "remember me" (admin-spec "Privacy and security"); the session survives
// a reload but not closing the tab.
@JsFun("(key) => { const v = window.sessionStorage.getItem(key); return v === null ? '' : v; }")
external fun sessionGet(key: String): String

@JsFun("(key, value) => window.sessionStorage.setItem(key, value)")
external fun sessionSet(key: String, value: String)

@JsFun("(key) => window.sessionStorage.removeItem(key)")
external fun sessionRemove(key: String)

@JsFun("() => crypto.randomUUID()")
external fun randomUuid(): String

/** Saves [text] as a file through a temporary object URL (the CSV export needs the bearer token, so no plain link). */
@JsFun(
    """(fileName, text, mime) => {
        const url = URL.createObjectURL(new Blob([text], { type: mime }));
        const a = document.createElement('a');
        a.href = url; a.download = fileName;
        document.body.appendChild(a); a.click(); a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }""",
)
external fun downloadText(fileName: String, text: String, mime: String)

@JsFun("() => window.location.hash")
external fun locationHash(): String

@JsFun("(hash) => { if (window.location.hash !== hash) window.location.hash = hash; }")
external fun setLocationHash(hash: String)

@JsFun("(callback) => window.addEventListener('hashchange', () => callback())")
external fun onHashChange(callback: () -> Unit)
