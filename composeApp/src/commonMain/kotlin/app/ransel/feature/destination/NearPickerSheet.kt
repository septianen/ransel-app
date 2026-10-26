package app.ransel.feature.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.platform.LocationFix
import app.ransel.platform.rememberLocationProvider
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.NEAR_DESTINATION_KM
import app.ransel.shared.domain.withinKm
import app.ransel.shared.dto.TripPlaceDto
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.destination_denied_title
import ransel.composeapp.generated.resources.destination_location_failed
import ransel.composeapp.generated.resources.near_current
import ransel.composeapp.generated.resources.near_current_far
import ransel.composeapp.generated.resources.near_current_hint
import ransel.composeapp.generated.resources.near_last_activity
import ransel.composeapp.generated.resources.near_note
import ransel.composeapp.generated.resources.near_other
import ransel.composeapp.generated.resources.near_other_hint
import ransel.composeapp.generated.resources.near_title

/** The last activity of the poll's day, offered as the centre ("Aktivitas terakhir"). */
data class LastActivity(val dayNumber: Int, val title: String, val area: String, val time: String?)

/**
 * Design "PilihLokasiDekat": where Jelajah should search around. The current location is offered only while the trip
 * is in progress, and used only when the fix is within 50 km of the destination; the coordinates are used once.
 */
@Composable
fun NearPickerSheet(
    subtitle: String,
    destinationName: String,
    tripOngoing: Boolean,
    tripPlace: TripPlaceDto?,
    lastActivity: LastActivity?,
    onArea: (String) -> Unit,
    onDismiss: () -> Unit,
    api: RanselApi = koinInject(),
) {
    val scheme = MaterialTheme.colorScheme
    val location = rememberLocationProvider()
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var searchOther by remember { mutableStateOf(false) }
    val far = stringResource(Res.string.near_current_far, destinationName)
    val denied = stringResource(Res.string.destination_denied_title)
    val failed = stringResource(Res.string.destination_location_failed)

    AdaptiveSheet(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.near_title), style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            if (tripOngoing) {
                Option(Symbols.locationOn, stringResource(Res.string.near_current), problem ?: stringResource(Res.string.near_current_hint), accent = true, busy = locating) {
                    if (locating) return@Option
                    locating = true
                    problem = null
                    scope.launch {
                        when (val fix = location.request()) {
                            is LocationFix.Failed -> problem = if (fix.code == ErrorCode.LOCATION_DENIED) denied else failed
                            is LocationFix.Found -> {
                                val near = tripPlace == null || withinKm(fix.lat, fix.lng, tripPlace.lat, tripPlace.lng, NEAR_DESTINATION_KM)
                                if (!near) {
                                    problem = far
                                } else {
                                    val area = (api.reverseGeocode(fix.lat, fix.lng) as? ApiResult.Success)?.value?.areas?.firstOrNull()
                                    if (area != null) onArea(listOfNotNull(area.name, area.secondary).joinToString(", ")) else problem = failed
                                }
                            }
                        }
                        locating = false
                    }
                }
            }
            if (lastActivity != null) {
                Option(
                    Symbols.eventNote,
                    stringResource(Res.string.near_last_activity, lastActivity.dayNumber),
                    listOfNotNull(lastActivity.title, lastActivity.time).joinToString(" · "),
                ) { onArea(lastActivity.area) }
            }
            Option(Symbols.search, stringResource(Res.string.near_other), stringResource(Res.string.near_other_hint)) { searchOther = true }
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.info, size = 18.dp, tint = scheme.onSurfaceVariant)
                Text(stringResource(Res.string.near_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
    if (searchOther) {
        DestinationPicker(
            onPicked = {
                searchOther = false
                onArea(it.name)
            },
            onDismiss = { searchOther = false },
        )
    }
}

@Composable
private fun Option(symbol: String, title: String, subtitle: String, accent: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(IrisShape.lg)
            .background(if (accent) scheme.primaryContainer else scheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp)) else Symbol(symbol, tint = if (accent) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (accent) scheme.onPrimaryContainer else scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (accent) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
        }
        Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
    }
}
