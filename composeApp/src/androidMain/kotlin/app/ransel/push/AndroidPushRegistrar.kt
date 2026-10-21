package app.ransel.push

import android.Manifest
import android.content.Context
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

    /** Android 13+ only, and only once per install: the system stops showing the dialog after repeated refusals anyway. */
    override fun requestPermission() {
        if (hasPermission()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        val ask = permissionRequest ?: return // no screen to show the dialog on right now; try again next time
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        ask()
    }

    override fun deviceLanguage(): String =
        if (Locale.getDefault().language == "en") "en" else "id"

    companion object {
        private const val PREFS = "ransel_push"
        private const val KEY_ASKED = "permission_asked"

        /** Set by MainActivity while it is on screen: shows the system permission dialog. */
        @Volatile
        var permissionRequest: (() -> Unit)? = null
    }
}
