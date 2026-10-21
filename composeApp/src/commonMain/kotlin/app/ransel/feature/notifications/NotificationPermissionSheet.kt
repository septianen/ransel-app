package app.ransel.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.*

/** Explains the notification permission before the system dialog (design `IzinNotifikasi`). */
@Composable
fun NotificationPermissionSheet(onAllow: () -> Unit, onLater: () -> Unit) {
    AdaptiveSheet(onDismiss = onLater) {
        PermissionSheetContent(onAllow, onLater)
    }
}

@Composable
private fun PermissionSheetContent(onAllow: () -> Unit, onLater: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            Symbol(Symbols.notificationsActive, size = 28.dp, filled = true, tint = scheme.onPrimaryContainer)
        }
        Text(stringResource(Res.string.notification_permission_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.notification_permission_body), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.notification_permission_allow), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.notification_permission_later), fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.padding(bottom = 8.dp))
    }
}

@Preview
@Composable
private fun PermissionSheetPreview() = IrisPreview { PermissionSheetContent({}, {}) }
