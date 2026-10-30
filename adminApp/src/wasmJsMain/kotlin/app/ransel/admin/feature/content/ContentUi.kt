package app.ransel.admin.feature.content

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.content_publish
import app.ransel.admin.generated.resources.content_publish_body
import app.ransel.admin.generated.resources.content_publish_none
import app.ransel.admin.generated.resources.content_publish_title
import app.ransel.admin.generated.resources.content_status_default
import app.ransel.admin.generated.resources.content_status_draft
import app.ransel.admin.generated.resources.content_status_published
import app.ransel.admin.ui.ConfirmDialog
import app.ransel.admin.ui.Pill
import app.ransel.shared.model.ContentStatus
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource

/** "Terbit", "Draf" or "Bawaan" (design rows of KontenTeks / KontenError). */
@Composable
fun ContentStatusPill(status: ContentStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        ContentStatus.PUBLISHED -> Pill(stringResource(Res.string.content_status_published), scheme.primaryContainer, scheme.onPrimaryContainer)
        ContentStatus.DRAFT -> Pill(stringResource(Res.string.content_status_draft), IrisTheme.accents.yellow.copy(alpha = 0.22f), scheme.onSurface)
        ContentStatus.DEFAULT -> Pill(stringResource(Res.string.content_status_default), scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
    }
}

/** "Terbitkan N draf": every string and error draft goes live together, after a confirmation. */
@Composable
fun PublishButton(drafts: Int, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = drafts > 0 && !busy, modifier = Modifier.heightIn(min = 44.dp)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            Symbol(Symbols.cloudUpload, size = 20.dp, tint = LocalContentColor.current)
            Text(
                if (drafts > 0) stringResource(Res.string.content_publish, drafts) else stringResource(Res.string.content_publish_none),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun PublishDialog(drafts: Int, busy: Boolean, error: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.content_publish_title, drafts),
        body = stringResource(Res.string.content_publish_body),
        confirmLabel = stringResource(Res.string.content_publish, drafts),
        destructive = false,
        busy = busy,
        error = error,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
