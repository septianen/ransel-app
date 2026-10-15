package app.ransel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.ransel.data.invite.InviteLinks
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val inviteLinks: InviteLinks by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) offerInvite(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        offerInvite(intent)
    }

    private fun offerInvite(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.dataString?.let(inviteLinks::offerUrl)
    }
}
