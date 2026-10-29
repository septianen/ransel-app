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
import app.ransel.data.sync.DroppedChanges
import app.ransel.data.sync.DroppedKind
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_ok
import ransel.composeapp.generated.resources.back_online_snackbar
import ransel.composeapp.generated.resources.offline_banner_body
import ransel.composeapp.generated.resources.offline_banner_title
import ransel.composeapp.generated.resources.sync_dropped_activities
import ransel.composeapp.generated.resources.sync_dropped_and
import ransel.composeapp.generated.resources.sync_dropped_expenses
import ransel.composeapp.generated.resources.sync_dropped_in_trip_no_access
import ransel.composeapp.generated.resources.sync_dropped_in_trip_refused
import ransel.composeapp.generated.resources.sync_dropped_in_trips_no_access
import ransel.composeapp.generated.resources.sync_dropped_in_trips_refused
import ransel.composeapp.generated.resources.sync_dropped_packing
import ransel.composeapp.generated.resources.sync_dropped_trips
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

/** How long the "tidak tersimpan" snackbar stays without "OK": longer than the others, it reports lost changes. */
private const val DROPPED_SNACKBAR_MILLIS = 10_000L

/**
 * "2 pengeluaran dan 1 aktivitas di “Bali 4 hari” tidak tersimpan karena kamu tidak punya akses edit lagi.": changes the
 * server refused, already deleted from this device (owner, 29 Oct 2026). [onShown] after "OK" or the timeout.
 */
@Composable
fun DroppedChangesSnackbar(dropped: DroppedChanges?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(dropped) {
        if (dropped != null) {
            delay(DROPPED_SNACKBAR_MILLIS)
            onShown()
        }
    }
    if (dropped == null) return
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        UndoSnackbar(droppedChangesMessage(dropped), actionLabel = stringResource(Res.string.action_ok), onAction = onShown)
    }
}

@Composable
internal fun droppedChangesMessage(dropped: DroppedChanges): String {
    val parts = listOf(
        DroppedKind.EXPENSE to Res.plurals.sync_dropped_expenses,
        DroppedKind.ACTIVITY to Res.plurals.sync_dropped_activities,
        DroppedKind.PACKING to Res.plurals.sync_dropped_packing,
        DroppedKind.TRIP to Res.plurals.sync_dropped_trips,
    ).mapNotNull { (kind, res) -> dropped.counts[kind]?.takeIf { it > 0 }?.let { pluralStringResource(res, it, it) } }
    val and = stringResource(Res.string.sync_dropped_and)
    val what = if (parts.size < 2) parts.firstOrNull().orEmpty() else parts.dropLast(1).joinToString(", ") + " $and " + parts.last()
    val title = dropped.tripTitle
    return when {
        title != null && dropped.noAccess -> stringResource(Res.string.sync_dropped_in_trip_no_access, what, title)
        title != null -> stringResource(Res.string.sync_dropped_in_trip_refused, what, title)
        dropped.noAccess -> stringResource(Res.string.sync_dropped_in_trips_no_access, what, dropped.tripIds.size)
        else -> stringResource(Res.string.sync_dropped_in_trips_refused, what, dropped.tripIds.size)
    }
}

@Preview
@Composable
private fun DroppedChangesSnackbarPreview() = IrisPreview {
    DroppedChangesSnackbar(
        DroppedChanges(mapOf(DroppedKind.EXPENSE to 2, DroppedKind.ACTIVITY to 1), setOf("t1"), tripTitle = "Bali 4 hari"),
        onShown = {},
    )
}

@Preview
@Composable
private fun DroppedChangesSnackbarRefusedPreview() = IrisPreview {
    DroppedChangesSnackbar(DroppedChanges(mapOf(DroppedKind.PACKING to 3), setOf("t1", "t2"), noAccess = false), onShown = {})
}
