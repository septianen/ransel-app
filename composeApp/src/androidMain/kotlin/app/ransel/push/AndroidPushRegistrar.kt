package app.ransel.push

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.ransel.data.notification.PushRegistrar
import app.ransel.shared.model.DevicePlatform
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** Firebase Cloud Messaging. Only active when the build has a google-services.json (otherwise [available] is false). */
class AndroidPushRegistrar(private val context: Context) : PushRegistrar {
    override val platform: DevicePlatform = DevicePlatform.ANDROID

    override val appVersion: String? =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

    override val available: Boolean
        get() = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    override suspend fun token(): String? {
        if (!available) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(task.takeIf { it.isSuccessful }?.result)
            }
        }
    }

    override fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Android 13+: the system dialog, or the app's notification settings once the system no longer shows it (after the
     * second refusal; MainActivity decides). Older versions need none.
     */
    override fun requestPermission() {
        if (hasPermission()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val asked = prefs.getInt(KEY_REQUESTS, 0)
        val request = permissionRequest ?: return // no screen to show the dialog on right now: nothing happens
        if (!request(asked > 0)) openSystemSettings()
        prefs.edit().putInt(KEY_REQUESTS, asked + 1).apply()
    }

    /** At every app start while the permission is missing (owner, 3 Nov 2026), once per launch. */
    override fun shouldExplainPermission(): Boolean = !hasPermission() && !explainedThisLaunch

    override fun explanationDismissed() {
        explainedThisLaunch = true
    }

    override fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override fun deviceLanguage(): String =
        if (Locale.getDefault().language == "en") "en" else "id"

    companion object {
        private const val PREFS = "ransel_push"
        private const val KEY_REQUESTS = "permission_requests"

        /** The sheet was answered in this process: the next launch asks again. */
        @Volatile
        private var explainedThisLaunch = false

        /**
         * Set by MainActivity while it is on screen: shows the system permission dialog and returns true, or returns
         * false when the system would not show it any more (asked before and refused twice). The argument says
         * whether it was asked before.
         */
        @Volatile
        var permissionRequest: ((askedBefore: Boolean) -> Boolean)? = null
    }
}
