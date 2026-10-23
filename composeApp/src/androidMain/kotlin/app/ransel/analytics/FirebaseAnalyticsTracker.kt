package app.ransel.analytics

import android.content.Context
import android.os.Bundle
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Firebase Analytics, debug and release (debug reports as its own app, app.ransel.debug). Silent without a
 * google-services.json (no Firebase app).
 */
class FirebaseAnalyticsTracker(context: Context) : Analytics {
    private val firebase: FirebaseAnalytics? =
        if (runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)) FirebaseAnalytics.getInstance(context)
        else null

    override fun screen(name: String) {
        firebase?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
        })
    }

    override fun event(event: AnalyticsEvent) {
        firebase?.logEvent(event.name, event.params.toBundle())
    }

    override fun setUser(userId: String?) {
        firebase?.setUserId(userId)
    }

    override fun setUserProperty(name: String, value: String?) {
        firebase?.setUserProperty(name, value)
    }
}

private fun Map<String, Any>.toBundle() = Bundle().apply {
    for ((key, value) in this@toBundle) {
        when (value) {
            is Int -> putLong(key, value.toLong())
            is Long -> putLong(key, value)
            is Double -> putDouble(key, value)
            // Analytics has no boolean type: 1/0 can be summed and filtered in reports.
            is Boolean -> putLong(key, if (value) 1 else 0)
            else -> putString(key, value.toString())
        }
    }
}
