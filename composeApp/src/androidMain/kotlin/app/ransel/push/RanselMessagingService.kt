package app.ransel.push

import app.ransel.data.analytics.CrashReporter
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.ransel.MainActivity
import app.ransel.R
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.notification.PushRegistration
import app.ransel.data.remote.RanselApi
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

const val CHANNEL_TRIP_ACTIVITY = "trip_activity"
const val CHANNEL_REMINDERS = "reminders"

/** Two channels, so the user can silence reminders without silencing joins and expenses. Safe to call repeatedly. */
fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_TRIP_ACTIVITY, context.getString(R.string.notification_channel_trip_activity), NotificationManager.IMPORTANCE_DEFAULT),
    )
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.notification_channel_reminders), NotificationManager.IMPORTANCE_HIGH),
    )
}

class RanselMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val koin = GlobalContext.getOrNull() ?: return
        scope.launch {
            runCatching { koin.get<PushRegistration>().onNewToken(token) }.onFailure(koin.get<CrashReporter>()::record)
        }
    }

    /**
     * Only reached while the app is open: with the app in the background the system shows the notification by itself
     * and tapping it starts MainActivity with the same data as extras.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val koin = GlobalContext.getOrNull()
        if (koin != null) scope.launch { runCatching { koin.get<NotificationsStore>().refresh(koin.get<RanselApi>()) } }
        val title = message.notification?.title ?: return
        val body = message.notification?.body.orEmpty()
        createNotificationChannels(this)
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pending = PendingIntent.getActivity(this, message.messageId.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val channel = if (message.data["type"]?.endsWith("REMINDER") == true) CHANNEL_REMINDERS else CHANNEL_TRIP_ACTIVITY
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_push_notif_large))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), notification) } // SecurityException when the permission was refused
    }
}
