package app.ransel.feature.itinerary.travel

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.domain.TravelLeg
import app.ransel.shared.domain.TravelWarning
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.ui.components.dashedBorder
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.travel_a11y_capsule
import ransel.composeapp.generated.resources.travel_a11y_warning
import ransel.composeapp.generated.resources.travel_add_location
import ransel.composeapp.generated.resources.travel_gap_warning
import ransel.composeapp.generated.resources.travel_overlap_warning

/** What the capsule between two consecutive activities shows (spec "Perjalanan antar tempat V1", section 1). */
data class TravelCapsuleModel(
    val from: ItineraryItemDto,
    val to: ItineraryItemDto,
    val leg: TravelLeg,
    val warning: TravelWarning?,
)

/** The warning sentence of a leg: "Jeda cuma 15 mnt. Jam tidak diubah." or, when the times touch, "Jam bentrok ...". */
@Composable
fun travelWarningText(warning: TravelWarning): String =
    if (warning.overlaps) {
        stringResource(Res.string.travel_overlap_warning)
    } else {
        stringResource(Res.string.travel_gap_warning, travelGapText(warning.gapMinutes))
    }

/**
 * The capsule between two activities: mode icon, "±time", distance and a chevron; a tight gap turns it into the warning
 * variant (second line with the warning sentence). The touch area is 48 dp, the capsule itself 32 dp (the plain one).
 */
@Composable
fun TravelCapsule(model: TravelCapsuleModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val warning = model.warning
    val text = capsuleText(model.leg, warned = warning != null)
    val description = stringResource(Res.string.travel_a11y_capsule, model.from.title, model.to.title, text) +
        (warning?.let { " " + stringResource(Res.string.travel_a11y_warning, travelWarningText(it)) }.orEmpty())
    Box(
        modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick, role = Role.Button)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (warning == null) {
            Row(
                Modifier
                    .heightIn(min = 32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                legSymbol(model.leg.kind)?.let { Symbol(it, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Symbol(Symbols.chevronRight, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val colors = IrisTheme.warning
            Column(
                Modifier
                    .clip(IrisShape.lg)
                    .background(colors.warningContainer)
                    .border(BorderStroke(1.5.dp, colors.warning), IrisShape.lg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    legSymbol(model.leg.kind)?.let { Symbol(it, size = 16.dp, tint = colors.onWarningContainer) }
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onWarningContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Symbol(Symbols.chevronRight, size = 16.dp, tint = colors.onWarningContainer)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Symbol(Symbols.warning, size = 16.dp, tint = colors.onWarningContainer)
                    Text(
                        travelWarningText(warning),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onWarningContainer,
                    )
                }
            }
        }
    }
}

/** "Tambah lokasi untuk lihat perjalanan": the capsule of two activities where one has no place yet (spec 1d). Editors only. */
@Composable
fun AddLocationPrompt(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = stringResource(Res.string.travel_add_location)
    Box(
        modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick, role = Role.Button)
            .clearAndSetSemantics {
                contentDescription = text
                role = Role.Button
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .heightIn(min = 36.dp)
                .dashedBorder(MaterialTheme.colorScheme.outline, radius = 18.dp, width = 1.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Symbol(Symbols.locationOn, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
            Text(
                text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview
@Composable
private fun TravelCapsulePreview() = IrisPreview {
    val a = previewItem("a", "Kuliner")
    val b = previewItem("b", "Pantai")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TravelCapsule(TravelCapsuleModel(a, b, TravelLeg(0.75, app.ransel.shared.domain.LegKind.WALK, 10, 10), null), {})
        TravelCapsule(TravelCapsuleModel(a, b, TravelLeg(5.2, app.ransel.shared.domain.LegKind.MOTORCYCLE, 15, 15), null), {})
        TravelCapsule(TravelCapsuleModel(a, b, TravelLeg(150.0, app.ransel.shared.domain.LegKind.FAR, null, 180), TravelWarning(60, 180)), {})
        TravelCapsule(TravelCapsuleModel(a, b, TravelLeg(5.2, app.ransel.shared.domain.LegKind.MOTORCYCLE, 15, 15), TravelWarning(0, 15)), {})
        AddLocationPrompt({})
    }
}

internal fun previewItem(id: String, title: String) = ItineraryItemDto(
    id = id, tripId = "t", title = title, category = app.ransel.shared.model.Category.ATTRACTION,
    createdAt = kotlin.time.Instant.fromEpochSeconds(0), updatedAt = kotlin.time.Instant.fromEpochSeconds(0),
)
