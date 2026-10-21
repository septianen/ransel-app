package app.ransel.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Karung's screens keep their state in small presenter classes (one `StateFlow<UiState>` each, like the ViewModels in
 * composeApp) that run on a [CoroutineScope] tied to the screen: it is cancelled when the screen leaves or [keys] change.
 */
@Composable
fun <T : Any> rememberPresenter(vararg keys: Any?, create: (CoroutineScope) -> T): T {
    val holder = remember(*keys) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope to create(scope)
    }
    DisposableEffect(holder) { onDispose { holder.first.cancel() } }
    return holder.second
}
