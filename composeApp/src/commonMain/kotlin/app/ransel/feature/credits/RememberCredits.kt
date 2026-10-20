package app.ransel.feature.credits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import app.ransel.data.credit.CreditsStore
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.CreditsDto
import org.koin.compose.koinInject

/** The signed-in user's Explore credits: the last known value at once, refreshed from the server on entering the screen. */
@Composable
fun rememberCredits(): State<CreditsDto?> {
    val store: CreditsStore = koinInject()
    val api: RanselApi = koinInject()
    LaunchedEffect(Unit) { store.refresh(api) }
    return store.state.collectAsState()
}
