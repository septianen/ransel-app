package app.ransel.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.cancel
import app.ransel.admin.generated.resources.error_code
import app.ransel.admin.generated.resources.error_connection
import app.ransel.admin.generated.resources.error_generic
import app.ransel.admin.generated.resources.error_not_found
import app.ransel.admin.generated.resources.retry
import app.ransel.shared.api.ErrorCode
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.irisCard
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** White IRIS card with the standard padding. */
@Composable
fun AdminCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.irisCard(IrisShape.lg).padding(padding)) { content() }
}

/** Small rounded label: status, ledger kind, environment. */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        modifier = modifier.background(container, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Initial in a coloured circle; [seed] picks the colour so one user keeps the same one. */
@Composable
fun Avatar(name: String, seed: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val pairs = listOf(
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.surfaceContainerHighest to scheme.onSurface,
    )
    val (bg, fg) = pairs[(seed.hashCode() and Int.MAX_VALUE) % pairs.size]
    Box(modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = fg,
            style = if (size >= 56.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun failureText(error: ApiFailure): String = when {
    error.isConnection -> stringResource(Res.string.error_connection)
    error.code == ErrorCode.NOT_FOUND -> stringResource(Res.string.error_not_found)
    else -> stringResource(Res.string.error_generic)
}

/** Same text as [failureText], for snackbars shown from a coroutine. */
suspend fun failureMessage(error: ApiFailure): String = when {
    error.isConnection -> getString(Res.string.error_connection)
    error.code == ErrorCode.NOT_FOUND -> getString(Res.string.error_not_found)
    else -> getString(Res.string.error_generic) + " · " + getString(Res.string.error_code, error.code)
}

/** A load failed: message, the code small (as in the apps), and a retry button. */
@Composable
fun ErrorPanel(error: ApiFailure, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(Symbols.cloudOff, size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(failureText(error), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (!error.isConnection) {
            Text(
                stringResource(Res.string.error_code, error.code),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 44.dp)) {
            Text(stringResource(Res.string.retry))
        }
    }
}

@Composable
fun LoadingPanel(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Inline error line under a field or above the dialog buttons. */
@Composable
fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Symbol(Symbols.error, size = 18.dp, filled = true, tint = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** Every admin action asks first (admin-spec "Other user actions"). */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    busy: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                if (error != null) ErrorLine(error)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                else Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
