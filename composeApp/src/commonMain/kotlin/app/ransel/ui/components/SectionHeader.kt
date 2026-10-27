package app.ransel.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The one style for section titles inside a screen (IRIS `headline-small`: "Judul section"; owner, 27 Oct 2026), e.g.
 * "Perlu tindakanmu", "Transaksi", a day of the itinerary. Optional quiet [trailing] text ("3 anggota") or an action
 * link on the right. Not for app bar or sheet titles, nor for the collapsible category headers of a list.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onAction)
                    .padding(start = 12.dp)
                    .padding(vertical = 14.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SectionHeaderPreview() = IrisPreview {
    SectionHeader(title = "Belum dijadwalkan", actionLabel = "Lihat semua", onAction = {})
}
