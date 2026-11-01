package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape

/**
 * "A newer version is out". [onLater] = null makes it a required update: no dismiss button, and neither a tap outside
 * nor the Back button closes it, so the only way on is [onUpdate] (the store).
 */
@Composable
fun AppUpdateDialog(
    title: String,
    body: String,
    updateLabel: String,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    laterLabel: String? = null,
    onLater: (() -> Unit)? = null,
) {
    val dismissible = onLater != null
    AlertDialog(
        onDismissRequest = { onLater?.invoke() },
        modifier = modifier,
        shape = IrisShape.xl,
        containerColor = MaterialTheme.colorScheme.surface,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
        icon = {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Symbol(Symbols.refresh, tint = MaterialTheme.colorScheme.primary, filled = true) }
        },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(
                onClick = onUpdate,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text(updateLabel, style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = if (dismissible && laterLabel != null) {
            {
                TextButton(onClick = { onLater?.invoke() }) {
                    Text(laterLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        } else null,
    )
}
