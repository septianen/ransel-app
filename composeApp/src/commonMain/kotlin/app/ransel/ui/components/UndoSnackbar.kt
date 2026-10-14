package app.ransel.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * IRIS snackbar: `inverse-surface`, radius 8, action in `inverse-primary`.
 * Use inside `SnackbarHost { data -> UndoSnackbar(data.visuals.message, data.visuals.actionLabel, ...) }`.
 * Without [actionLabel] it is a plain message ("Kembali online · 3 perubahan terkirim").
 */
@Composable
fun UndoSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Snackbar(
        modifier = modifier,
        shape = IrisShape.sm,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        action = if (actionLabel != null && onAction != null) {
            {
                TextButton(onClick = onAction) {
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                }
            }
        } else {
            null
        },
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview
@Composable
private fun UndoSnackbarPreview() = IrisPreview {
    UndoSnackbar(message = "Jadwal Hari 1 dirapatkan", actionLabel = "Urungkan", onAction = {})
}
