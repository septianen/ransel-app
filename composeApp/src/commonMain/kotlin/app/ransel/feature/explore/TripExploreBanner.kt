package app.ransel.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.trips.TripPhase
import app.ransel.shared.model.Role as TripRole
import app.ransel.ui.components.SessionPendingDot
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.trip_explore_banner
import ransel.composeapp.generated.resources.trip_explore_banner_no_destination
import ransel.composeapp.generated.resources.trip_explore_banner_resume

/** What the Overview's small "Cari tempat" banner says (plan explore-trip-one-tap v4). */
enum class TripExploreBanner {
    /** Opens the prefilled "Cari tempat" sheet. */
    SEARCH,
    /** An unfinished swipe session: straight back into it, no sheet, no charge. */
    RESUME,
    /** The trip has no destination: opens Edit trip (the OWNER's). */
    NO_DESTINATION,
}

/**
 * Which banner the Overview shows; null = none. Signed-in OWNER and EDITOR on a trip that has not ended (viewers and ended
 * trips do not see it). Without a destination only the OWNER gets "Atur tujuan dulu" (Edit trip is the owner's).
 */
fun tripExploreBanner(signedIn: Boolean, role: TripRole?, phase: TripPhase, destination: String, pending: Boolean): TripExploreBanner? = when {
    !signedIn || !app.ransel.feature.group.canSearchPlacesFromSheet(role, phase) -> null
    destination.isBlank() -> if (role == TripRole.OWNER) TripExploreBanner.NO_DESTINATION else null
    pending -> TripExploreBanner.RESUME
    else -> TripExploreBanner.SEARCH
}

/** One compact row under the countdown, the same on group and solo trips: AI tile, one line, chevron. No price here. */
@Composable
fun TripExploreBannerRow(banner: TripExploreBanner, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(IrisShape.lg)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // secondary stays for AI-origin elements (same tile as the + sheet's row)
        Box(Modifier.size(40.dp).clip(IrisShape.md).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Symbol(Symbols.autoAwesome, size = 20.dp, tint = scheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    when (banner) {
                        TripExploreBanner.SEARCH -> Res.string.trip_explore_banner
                        TripExploreBanner.RESUME -> Res.string.trip_explore_banner_resume
                        TripExploreBanner.NO_DESTINATION -> Res.string.trip_explore_banner_no_destination
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
        }
        if (banner == TripExploreBanner.RESUME) SessionPendingDot()
        Symbol(Symbols.chevronRight, size = 20.dp, tint = scheme.onSurfaceVariant)
    }
}

@Preview
@Composable
private fun TripExploreBannerPreview() = IrisPreview {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TripExploreBanner.entries.forEach { TripExploreBannerRow(it, onClick = {}) }
    }
}
