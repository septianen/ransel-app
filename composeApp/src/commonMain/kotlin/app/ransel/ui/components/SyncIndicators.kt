package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.back_online_snackbar
import ransel.composeapp.generated.resources.offline_banner_body
import ransel.composeapp.generated.resources.offline_banner_title
import ransel.composeapp.generated.resources.sync_pending_description

/** Ids of rows whose latest change has not reached the server (empty on the web). Provided at the app root. */
val LocalPendingIds = compositionLocalOf<Set<String>> { emptySet() }

/** Small cloud-upload glyph shown next to rows that are still waiting to be sent. */
@Composable
fun PendingMarker(id: String, modifier: Modifier = Modifier) {
    if (id !in LocalPendingIds.current) return
    Symbol(
        Symbols.cloudUpload,
        modifier = modifier,
        size = 16.dp,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        contentDescription = stringResource(Res.string.sync_pending_description),
    )
}

/** "Kamu sedang offline" strip under the trip tabs while changes can only be saved locally. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(Symbols.cloudOff, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(stringResource(Res.string.offline_banner_title), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(Res.string.offline_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Brief confirmation after the connection came back and pending changes went out. */
@Composable
fun BackOnlineToast(count: Int?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(count) {
        if (count != null) {
            delay(3_000)
            onShown()
        }
    }
    if (count == null) return
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(Symbols.cloudDone, size = 20.dp, filled = true, tint = MaterialTheme.colorScheme.inverseOnSurface)
            Text(
                stringResource(Res.string.back_online_snackbar, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}
