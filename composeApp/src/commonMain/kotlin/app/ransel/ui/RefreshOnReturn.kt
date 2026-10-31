package app.ransel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Server data shows what the server said when the screen opened, and a screen left on the back stack keeps its
 * ViewModel. So a screen that reads from the API calls this: [onRefresh] runs whenever the screen shows again (back
 * from a screen above it, or the app back in front), but not when it first opens, since the ViewModel loads then.
 * [onRefresh] must keep what is shown while it loads (no spinner) and keep it when the refresh fails.
 */
@Composable
fun RefreshOnReturn(onRefresh: () -> Unit) {
    // the first ON_RESUME is the one an observer gets when it starts watching an already resumed screen
    val seen = remember { BooleanArray(1) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (seen[0]) onRefresh() else seen[0] = true
    }
}
