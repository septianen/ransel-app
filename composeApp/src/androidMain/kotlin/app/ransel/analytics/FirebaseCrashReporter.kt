package app.ransel.analytics

import android.content.Context
import app.ransel.data.analytics.CrashReporter
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crashlytics. Uncaught crashes are reported by the SDK itself; this adds the user id and non-fatal errors. Debug and
 * release (debug reports as its own app, app.ransel.debug). Silent without a google-services.json.
 */
class FirebaseCrashReporter(context: Context) : CrashReporter {
    private val crashlytics: FirebaseCrashlytics? =
        if (runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)) FirebaseCrashlytics.getInstance()
        else null

    override fun setUser(userId: String?) {
        crashlytics?.setUserId(userId.orEmpty())
    }

    override fun record(error: Throwable) {
        crashlytics?.recordException(error)
    }

    override fun log(message: String) {
        crashlytics?.log(message)
    }

    override fun setKey(key: String, value: String) {
        crashlytics?.setCustomKey(key, value)
    }
}
