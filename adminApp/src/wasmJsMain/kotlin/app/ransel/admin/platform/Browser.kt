package app.ransel.admin.platform

import app.ransel.admin.data.ApiEnvironment
import app.ransel.admin.data.environmentsFor
import app.ransel.admin.data.selected

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

@JsFun("() => window.location.reload()")
private external fun reloadPage()

private const val ENVIRONMENT_KEY = "karung.environment"

/** The servers this page may use (see [environmentsFor]). */
fun environments(): List<ApiEnvironment> = environmentsFor(browserHostname(), browserOrigin(), configuredOrigin())

/** The environment chosen in this tab (sessionStorage, like the session), production by default. */
fun currentEnvironment(all: List<ApiEnvironment> = environments()): ApiEnvironment =
    all.selected(sessionGet(ENVIRONMENT_KEY).takeIf { it.isNotEmpty() })

/** Remembers [environment] for this tab and reloads on the users list (user ids differ per database). */
fun switchEnvironment(environment: ApiEnvironment) {
    sessionSet(ENVIRONMENT_KEY, environment.id)
    setLocationHash("#/users")
    reloadPage()
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
