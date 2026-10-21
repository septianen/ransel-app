package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Round 56 dp primary FAB with only an icon; [label] is read by screen readers. */
@Composable
fun IconFab(symbol: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f))
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(symbol, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Preview
@Composable
private fun IconFabPreview() = IrisPreview {
    IconFab(Symbols.add, label = "Tambah barang", onClick = {})
}
