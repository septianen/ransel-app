package app.ransel.analytics

import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject

// window.ranselAnalytics is defined by index.html on the production host only (Firebase JS SDK); elsewhere it is
// undefined and every call does nothing.
@JsFun("(name, params) => { if (window.ranselAnalytics) window.ranselAnalytics.event(name, JSON.parse(params)); }")
private external fun jsEvent(name: String, params: String)

@JsFun("(name) => { if (window.ranselAnalytics) window.ranselAnalytics.screen(name); }")
private external fun jsScreen(name: String)

@JsFun("(id) => { if (window.ranselAnalytics) window.ranselAnalytics.user(id === '' ? null : id); }")
private external fun jsUser(id: String)

@JsFun("(name, value) => { if (window.ranselAnalytics) window.ranselAnalytics.property(name, value === '' ? null : value); }")
private external fun jsUserProperty(name: String, value: String)

/** Google Analytics for Firebase on the web (no Crashlytics: it has no web SDK). */
object WebAnalytics : Analytics {
    override fun screen(name: String) = jsScreen(name)

    override fun event(event: AnalyticsEvent) {
        val params = JsonObject(
            event.params.mapValues { (_, value) ->
                when (value) {
                    is Number -> JsonPrimitive(value)
                    // Same as Android: booleans as 1/0.
                    is Boolean -> JsonPrimitive(if (value) 1 else 0)
                    else -> JsonPrimitive(value.toString())
                }
            },
        )
        jsEvent(event.name, params.toString())
    }

    override fun setUser(userId: String?) = jsUser(userId.orEmpty())

    override fun setUserProperty(name: String, value: String?) = jsUserProperty(name, value.orEmpty())
}
