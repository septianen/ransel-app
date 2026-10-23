package app.ransel.data.notification

import app.ransel.shared.model.DevicePlatform
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.concurrent.Volatile

/**
 * The notification permission only: push itself needs the Firebase iOS SDK in the Xcode project (see
 * docs/tasks/frontend.md F7.x), so [available] stays false and nothing is registered with the server yet.
 */
class IosPushRegistrar : PushRegistrar {
    override val platform: DevicePlatform = DevicePlatform.IOS
    override val appVersion: String? = null
    override val available = false

    // UNUserNotificationCenter answers asynchronously; hasPermission() reads the last known answer.
    @Volatile
    private var granted = false

    init {
        refreshPermission()
    }

    override suspend fun token(): String? = null

    override fun hasPermission(): Boolean = granted

    /** The system dialog; iOS shows it only once per install and answers at once afterwards. */
    override fun requestPermission() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { allowed, _ -> granted = allowed }
    }

    override fun shouldExplainPermission(): Boolean = !granted && !defaults.boolForKey(KEY_EXPLAINED)

    override fun explanationDismissed() = defaults.setBool(true, KEY_EXPLAINED)

    override fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }

    override fun deviceLanguage(): String =
        if ((NSLocale.preferredLanguages.firstOrNull() as? String)?.startsWith("en") == true) "en" else "id"

    private fun refreshPermission() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            granted = settings?.authorizationStatus.let {
                it == UNAuthorizationStatusAuthorized || it == UNAuthorizationStatusProvisional || it == UNAuthorizationStatusEphemeral
            }
        }
    }

    private val defaults get() = NSUserDefaults.standardUserDefaults

    private companion object {
        const val KEY_EXPLAINED = "ransel_push_permission_explained"
    }
}
