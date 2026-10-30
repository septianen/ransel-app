package app.ransel.feature.polls

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.model.PollKind
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.poll_kind_sheet_title

/** The templates of a poll. Each kind decides the option fields and what the winner does. */
@Composable
fun PollKindSheet(onPick: (PollKind) -> Unit, onDismiss: () -> Unit) {
    AdaptiveSheet(onDismiss = onDismiss) { PollKindContent(onPick) }
}

@Composable
private fun PollKindContent(onPick: (PollKind) -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.poll_kind_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        PollKind.entries.forEach { kind ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(IrisShape.md)
                    .clickable(role = Role.Button) { onPick(kind) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(IrisShape.md).background(kind.tileColor()), contentAlignment = Alignment.Center) {
                    Symbol(kind.symbol, tint = IrisTheme.accents.onAccent)
                }
                Column(Modifier.weight(1f)) {
                    Text(kind.label(), style = MaterialTheme.typography.titleSmall)
                    Text(kind.hint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview
@Composable
private fun PollKindPreview() = IrisPreview { Box(Modifier.padding(20.dp)) { PollKindContent {} } }
