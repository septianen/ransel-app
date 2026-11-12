package app.ransel.feature.itinerary

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.place_field_clear
import ransel.composeapp.generated.resources.place_field_hint
import ransel.composeapp.generated.resources.place_field_no_point

/**
 * The "Tempat" field of the activity form: a button that opens the place picker (spec "Field Tempat di form aktivitas").
 * Three states: A empty; B filled from a suggestion or this trip's places (green pin = it has a point on the map, name
 * and short address, X clears the name, the id and the point together); C free text (grey pin, the caption "Tanpa titik
 * di peta"). The pin colour never says it alone: C always has the caption.
 */
@Composable
fun PlaceField(
    label: String,
    name: String,
    secondary: String?,
    hasPoint: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val filled = name.isNotEmpty()
    val twoLines = filled && hasPoint && !secondary.isNullOrBlank()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (twoLines) 60.dp else 48.dp)
                .clip(IrisShape.md)
                .border(1.dp, scheme.outline, IrisShape.md)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 12.dp, end = if (filled) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Symbol(Symbols.locationOn, size = 22.dp, tint = if (filled && hasPoint) scheme.primary else scheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (!filled) {
                    Text(stringResource(Res.string.place_field_hint), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (twoLines) Text(secondary.orEmpty(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (filled) {
                // 32 dp button inside a 48 dp touch area
                Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClear), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        Symbol(Symbols.close, size = 18.dp, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.place_field_clear))
                    }
                }
            } else {
                Symbol(Symbols.search, size = 22.dp, tint = scheme.onSurfaceVariant)
            }
        }
        if (filled && !hasPoint) {
            Text(stringResource(Res.string.place_field_no_point), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
private fun PlaceFieldPreview() = IrisPreview {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PlaceField("Tempat", "", null, false, {}, {})
        PlaceField("Tempat", "Pantai Kuta", "Kuta, Kabupaten Badung", true, {}, {})
        PlaceField("Tempat", "Rumah Budi", null, false, {}, {})
    }
}
