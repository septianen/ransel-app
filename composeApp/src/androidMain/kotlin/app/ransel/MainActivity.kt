package app.ransel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import app.ransel.data.invite.InviteLinks
import app.ransel.data.notification.NotificationTarget
import app.ransel.push.AndroidPushRegistrar
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val inviteLinks: InviteLinks by inject()
    private val notificationTarget: NotificationTarget by inject()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            offerInvite(intent)
            offerNotification(intent)
        }
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 33) {
            AndroidPushRegistrar.permissionRequest = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    override fun onStop() {
        AndroidPushRegistrar.permissionRequest = null
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        offerInvite(intent)
        offerNotification(intent)
    }

    /** A notification was tapped: its data (type, tripId, notificationId, and roundId + stage for voting) arrives as intent extras. */
    private fun offerNotification(intent: Intent?) {
        if (intent?.getStringExtra("type") == null) return
        val tripId = intent.getStringExtra("tripId") ?: return
        notificationTarget.offer(tripId, intent.getStringExtra("roundId"), intent.getStringExtra("stage"))
    }

    private fun offerInvite(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.dataString?.let(inviteLinks::offerUrl)
    }
}
