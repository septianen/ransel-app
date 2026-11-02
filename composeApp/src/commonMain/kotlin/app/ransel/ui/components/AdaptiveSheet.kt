package app.ransel.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.navigation.SheetBackHandler
import app.ransel.ui.adaptive.rememberIsExpanded
import app.ransel.ui.theme.IrisShape

/**
 * Sheet that adapts to the window: a bottom sheet on phones (top corners 24 dp, 20 dp padding, scrim
 * 40%), and a centered dialog card (max 480 dp wide) on wide web windows, where a full-width bottom
 * sheet would stretch across the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // back closes the sheet first, the screen stays (Android back, the browser's back button)
    SheetBackHandler(onDismiss)
    if (rememberIsExpanded()) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(
                    // A bounded height is required: sheet contents may scroll, and a dialog gives no height limit itself.
                    modifier = modifier.widthIn(max = 480.dp).heightIn(max = 640.dp).fillMaxWidth(),
                    shape = IrisShape.xl,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 8.dp,
                ) {
                    // No scrolling here: sheets that can be long (currency list, expense form) scroll
                    // themselves, and nested vertical scrolling is not allowed.
                    Column(Modifier.padding(20.dp), content = content)
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = modifier,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = IrisShape.xl.copy(
                bottomStart = androidx.compose.foundation.shape.ZeroCornerSize,
                bottomEnd = androidx.compose.foundation.shape.ZeroCornerSize,
            ),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
                    .navigationBarsPadding(),
                content = content,
            )
        }
    }
}
