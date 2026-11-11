package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.content.stringResource
import app.ransel.navigation.SheetBackHandler
import app.ransel.ui.RefreshOnReturn
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.RanselMark
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.update_action
import ransel.composeapp.generated.resources.update_action_required
import ransel.composeapp.generated.resources.update_body_optional
import ransel.composeapp.generated.resources.update_body_required
import ransel.composeapp.generated.resources.update_current_version
import ransel.composeapp.generated.resources.update_later
import ransel.composeapp.generated.resources.update_opening_store
import ransel.composeapp.generated.resources.update_store_error
import ransel.composeapp.generated.resources.update_title_optional
import ransel.composeapp.generated.resources.update_title_required

/**
 * Logo green, in both themes. Material `primary` is lighter in dark (`#5ed051`); the mark on the update screens
 * stays the icon green. This is the one place a raw colour is allowed.
 */
private val RanselMarkGreen = Color(0xFF008808)

/** The backpack on the icon-green tile. [RanselLogoTile] follows the theme primary, so the update screens do not use it. */
@Composable
private fun UpdateMark(size: Dp) {
    Box(
        Modifier.size(size).clip(IrisShape.md).background(RanselMarkGreen),
        contentAlignment = Alignment.Center,
    ) {
        RanselMark(size = size * 0.8f, tint = Color.White, contentDescription = "RANSEL")
    }
}

/**
 * Full-screen gate when this install is below the minimum. It is not a dialog: Back is consumed, there is no
 * dismiss, and the screen stays up when the person comes back from the store still outdated. [onReturned] runs
 * on a later resume (not the one that first shows this screen).
 */
@Composable
fun ForceUpdateScreen(
    versionName: String,
    currentVersion: String,
    message: String?,
    opening: Boolean,
    storeError: Boolean,
    onUpdate: () -> Unit,
    onReturned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RefreshOnReturn(onReturned)
    SheetBackHandler { }
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(scheme.surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            UpdateMark(88.dp)
            Spacer(Modifier.size(24.dp))
            Text(
                stringResource(Res.string.update_title_required),
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                message?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.update_body_required, versionName),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(Res.string.update_current_version, currentVersion),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(8.dp)
                .background(scheme.surface)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (storeError) StoreError()
            Button(
                onClick = onUpdate,
                enabled = !opening,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                    disabledContainerColor = scheme.primary,
                    disabledContentColor = scheme.onPrimary,
                ),
            ) {
                if (opening) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = scheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(if (opening) Res.string.update_opening_store else Res.string.update_action_required),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/**
 * "Update tersedia". Nanti, the scrim and Back all call [onLater] (one snooze for this latest). Update does not
 * snooze: the dialog stays, and [onReturned] clears the spinner when the person comes back from the store.
 */
@Composable
fun SoftUpdateDialog(
    versionName: String,
    currentVersion: String,
    message: String?,
    opening: Boolean,
    storeError: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onReturned: () -> Unit,
) {
    RefreshOnReturn(onReturned)
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .shadow(8.dp, IrisShape.xl)
                .clip(IrisShape.xl)
                .background(scheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UpdateMark(40.dp)
            Text(
                stringResource(Res.string.update_title_optional),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
            )
            Text(
                message?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.update_body_optional, versionName, currentVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            if (storeError) StoreError()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        stringResource(Res.string.update_later),
                        color = scheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                LoadingButton(
                    onClick = onUpdate,
                    loading = opening,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                ) {
                    Text(stringResource(Res.string.update_action), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun StoreError() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Symbol(Symbols.error, size = 18.dp, tint = MaterialTheme.colorScheme.error, filled = true)
        Text(
            stringResource(Res.string.update_store_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Preview
@Composable
private fun ForceUpdateScreenPreview() = IrisPreview {
    ForceUpdateScreen("1.2.0", "1.0.0", null, opening = false, storeError = false, onUpdate = {}, onReturned = {})
}

@Preview
@Composable
private fun SoftUpdateDialogPreview() = IrisPreview {
    SoftUpdateDialog("1.2.0", "1.0.0", null, opening = false, storeError = false, onUpdate = {}, onLater = {}, onReturned = {})
}
