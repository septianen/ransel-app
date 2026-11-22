package app.ransel.feature.inspiration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.format.formatMoney
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_ai_estimate_note
import ransel.composeapp.generated.resources.explore_activity_region
import ransel.composeapp.generated.resources.explore_cost_label
import ransel.composeapp.generated.resources.explore_create_trip
import ransel.composeapp.generated.resources.explore_create_trip_note
import ransel.composeapp.generated.resources.explore_detail_title
import ransel.composeapp.generated.resources.explore_save

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun CardDetailSheet(
    card: InspirationCardDto,
    onDismiss: () -> Unit,
    onCreateTrip: (InspirationCardDto) -> Unit,
    onSave: (InspirationCardDto) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = IrisShape.xl) {
        CardDetailContent(card, onCreateTrip, onSave)
    }
}

@Composable
fun CardDetailContent(
    card: InspirationCardDto,
    onCreateTrip: (InspirationCardDto) -> Unit = {},
    onSave: (InspirationCardDto) -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.explore_activity_region, card.activity, card.region), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.explore_detail_title), style = MaterialTheme.typography.titleSmall)
        card.requirements.forEach { requirement ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Symbol(Symbols.check, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(requirement, style = MaterialTheme.typography.bodyMedium)
            }
        }
        card.costRange?.let { cost ->
            Text(stringResource(Res.string.explore_cost_label), style = MaterialTheme.typography.titleSmall)
            Text("${formatMoney(cost.minMinor, cost.currency)} – ${formatMoney(cost.maxMinor, cost.currency)}", style = MaterialTheme.typography.bodyLarge)
            EstimateNote()
        }
        card.visaNote?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
            EstimateNote()
        }
        card.bestSeason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        card.safetyLine?.let {
            Text(
                it,
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Button(onClick = { onCreateTrip(card) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(Res.string.explore_create_trip))
        }
        Text(stringResource(Res.string.explore_create_trip_note), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onSave(card) }, modifier = Modifier.fillMaxWidth()) {
            Symbol(Symbols.bookmark, size = 18.dp)
            Text(stringResource(Res.string.explore_save), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable private fun EstimateNote() {
    Text(
        stringResource(Res.string.explore_ai_estimate_note),
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer, IrisShape.md).padding(10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

private val skillPreview = InspirationCardDto(
    id = "c1", activity = "Freediving pemula", region = "Nusa Penida", why = "Sesuai minatmu",
    requirements = listOf("Level: pemula", "Buddy: belum ada"), safetyLine = "Ransel bukan operator. Pastikan lisensi dan buddy sebelum berangkat.",
)
@Preview @Composable private fun SkillCardSheetPreview() = IrisPreview { CardDetailContent(skillPreview) }
@Preview @Composable private fun AbroadCardSheetPreview() = IrisPreview { CardDetailContent(skillPreview.copy(visaNote = "Visa wisata diperlukan", costRange = null)) }
