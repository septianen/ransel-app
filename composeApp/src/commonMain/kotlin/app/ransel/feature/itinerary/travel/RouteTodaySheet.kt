package app.ransel.feature.itinerary.travel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.route_today_open

/** The chip in the day's title row: "Buka di Google Maps" (touch area 48 dp, the chip itself 32). */
@Composable
fun RouteTodayChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Symbol(Symbols.route, size = 16.dp, tint = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(Res.string.route_today_open),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
