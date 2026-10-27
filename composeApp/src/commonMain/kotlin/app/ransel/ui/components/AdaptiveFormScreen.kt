package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.navigation.SheetBackHandler
import app.ransel.ui.adaptive.rememberIsExpanded
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.form_discard_body
import ransel.composeapp.generated.resources.form_discard_confirm
import ransel.composeapp.generated.resources.form_discard_keep
import ransel.composeapp.generated.resources.form_discard_title

/**
 * A form that opens over the current screen: full screen on phones (back, title, a scrolling body and a footer that
 * stays at the bottom), a centred dialog card on wide web windows. Used for forms that can get long, where a bottom
 * sheet would close while scrolling (expense form, receipt result; owner, 27 Oct 2026).
 *
 * Back, the close button and a tap outside the card all ask [onDismiss]; with [confirmDiscard] they first ask
 * "Buang perubahan?", so typed input is not lost by accident.
 */
@Composable
fun AdaptiveFormScreen(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    confirmDiscard: Boolean = false,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    val requestClose = { if (confirmDiscard) asking = true else onDismiss() }
    SheetBackHandler(requestClose)
    val expanded = rememberIsExpanded()
    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (expanded) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = modifier.widthIn(max = 520.dp).heightIn(max = 720.dp).fillMaxWidth(),
                    shape = IrisShape.xl,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 8.dp,
                ) {
                    AdaptiveFormContent(title, subtitle, requestClose, card = true, footer = footer, content = content)
                }
            }
        } else {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                AdaptiveFormContent(title, subtitle, requestClose, card = false, footer = footer, content = content)
            }
        }
    }
    if (asking) {
        ConfirmDialog(
            title = stringResource(Res.string.form_discard_title),
            body = stringResource(Res.string.form_discard_body),
            confirmLabel = stringResource(Res.string.form_discard_confirm),
            dismissLabel = stringResource(Res.string.form_discard_keep),
            onConfirm = {
                asking = false
                onDismiss()
            },
            onDismiss = { asking = false },
            destructive = true,
        )
    }
}

/** The layout inside [AdaptiveFormScreen]: phone = back arrow on the left; [card] (wide web) = close on the right. */
@Composable
internal fun AdaptiveFormContent(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    card: Boolean,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(if (card) Modifier else Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val close = @Composable {
                Box(
                    Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(
                        if (card) Symbols.close else Symbols.arrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        tint = scheme.onSurface,
                    )
                }
            }
            if (!card) close()
            Column(Modifier.weight(1f).padding(horizontal = if (card) 16.dp else 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (card) close()
        }
        HorizontalDivider(color = scheme.outlineVariant)
        Column(
            Modifier
                .weight(1f, fill = !card)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        HorizontalDivider(color = scheme.outlineVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .background(scheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .then(if (card) Modifier else Modifier.navigationBarsPadding()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = footer,
        )
    }
}

@Preview
@Composable
private fun AdaptiveFormContentPreview() = IrisPreview {
    Box(Modifier.height(420.dp)) {
        AdaptiveFormContent(
            title = "Catat pengeluaran",
            subtitle = "Bali 4 hari · Hari 2",
            onClose = {},
            card = false,
            footer = { Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Simpan") } },
        ) {
            repeat(8) { Text("Field ${it + 1}", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
