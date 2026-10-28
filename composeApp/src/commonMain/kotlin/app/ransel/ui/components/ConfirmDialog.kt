package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Confirmation dialog (delete trip, reorder, remove member, leave trip).
 * [destructive] = `error` confirm button and, when [symbol] is set, the icon sits in an
 * `error-container` circle. [extra] holds dialog-specific content under the body
 * (list of what is deleted, "Don't ask again" checkbox, ...).
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    symbol: String? = null,
    /** Non-null = the confirm action waits for something: a [LoadingButton] that cannot be pressed while true, and the dialog cannot be closed. */
    confirmLoading: Boolean? = null,
    /** False = the confirm button is disabled (e.g. nothing picked yet in [extra]). */
    confirmEnabled: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val busy = confirmLoading == true
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = modifier,
        shape = IrisShape.xl,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = symbol?.let {
            {
                val container = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                val content = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(container),
                    contentAlignment = Alignment.Center,
                ) { Symbol(it, tint = content, filled = true) }
            }
        },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                extra?.invoke(this)
            }
        },
        confirmButton = {
            if (confirmLoading != null) {
                LoadingButton(
                    onClick = onConfirm,
                    loading = confirmLoading,
                    enabled = confirmEnabled,
                    colors = if (destructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) { Text(confirmLabel, style = MaterialTheme.typography.labelLarge) }
            } else {
                TextButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    colors = if (destructive) {
                        ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    },
                ) { Text(confirmLabel, style = MaterialTheme.typography.labelLarge) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(
                    dismissLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

// Dialogs open their own window, so the preview shows the destructive variant's content directly.
@Preview
@Composable
private fun ConfirmDialogPreview() = IrisPreview {
    ConfirmDialog(
        title = "Hapus trip ini?",
        body = "Semua aktivitas, barang, dan catatan pengeluaran ikut terhapus.",
        confirmLabel = "Ya, hapus",
        dismissLabel = "Batal",
        onConfirm = {},
        onDismiss = {},
        destructive = true,
        symbol = Symbols.delete,
    )
}
