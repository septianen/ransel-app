package app.ransel.feature.packing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.ransel.shared.dto.TripDto
import app.ransel.ui.shell.PlaceholderScreen

// Stub until its task (F1.4 / F1.5 / F1.6) is built.
@Composable
fun PackingTab(
    trip: TripDto,
    
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(title = trip.title, body = "PackingTab", modifier = modifier)
}
