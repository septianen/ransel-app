package app.ransel.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.ransel.shared.dto.NotificationSettingsDto
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.*

/** Account → Notifications (designs `PengaturanNotifikasi`, `PengaturanNotifikasiMati`). */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Back from the phone's settings: the permission may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }
    NotificationSettingsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onSet = viewModel::set,
        onOpenSystemSettings = viewModel::openSystemSettings,
        modifier = modifier.statusBarsPadding(),
    )
}

@Composable
fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSet: (NotificationKind, Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(scheme.surface)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back)) }
            Text(stringResource(Res.string.notification_settings_title), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        }
        val settings = state.settings
        when {
            state.loading && settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.failure != null && settings == null ->
                if (state.failure.isConnection) ConnectionErrorState(onRetry = onRetry, errorCode = state.failure.code, onBack = onBack)
                else ServerErrorState(onRetry = onRetry, errorCode = state.failure.code, onBack = onBack)
            settings != null -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.permissionOff) PermissionOffBanner(onOpenSystemSettings)
                Column {
                    Text(
                        stringResource(Res.string.notification_settings_section),
                        Modifier.padding(top = 8.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                    )
                    SettingRow(Symbols.groupAdd, Res.string.notification_settings_trip_activity, Res.string.notification_settings_trip_activity_hint, settings.tripActivity) { onSet(NotificationKind.TRIP_ACTIVITY, it) }
                    HorizontalDivider(color = scheme.outlineVariant)
                    SettingRow(Symbols.payments, Res.string.notification_settings_expenses, Res.string.notification_settings_expenses_hint, settings.expenses) { onSet(NotificationKind.EXPENSES, it) }
                    HorizontalDivider(color = scheme.outlineVariant)
                    SettingRow(Symbols.schedule, Res.string.notification_settings_reminders, Res.string.notification_settings_reminders_hint, settings.reminders) { onSet(NotificationKind.REMINDERS, it) }
                }
                if (state.saveFailed) Text(stringResource(Res.string.notification_settings_error), style = MaterialTheme.typography.bodySmall, color = scheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Symbol(Symbols.info, size = 16.dp, tint = scheme.onSurfaceVariant)
                    Text(stringResource(Res.string.notification_settings_footnote), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PermissionOffBanner(onOpenSystemSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.tertiaryContainer).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(Symbols.volumeOff, size = 28.dp, filled = true, tint = scheme.onTertiaryContainer)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.notification_settings_off_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onTertiaryContainer)
                Text(stringResource(Res.string.notification_settings_off_body), style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer)
            }
        }
        OutlinedButton(onClick = onOpenSystemSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Text(stringResource(Res.string.notification_settings_off_action), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingRow(
    symbol: String,
    title: org.jetbrains.compose.resources.StringResource,
    hint: org.jetbrains.compose.resources.StringResource,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(IrisShape.md).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            Symbol(symbol, size = 22.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
        )
    }
}

@Preview
@Composable
private fun NotificationSettingsPreview() = IrisPreview {
    Box(Modifier.height(520.dp)) {
        NotificationSettingsContent(
            NotificationSettingsUiState(loading = false, settings = NotificationSettingsDto(reminders = false), permissionOff = true),
            onBack = {}, onRetry = {}, onSet = { _, _ -> }, onOpenSystemSettings = {},
        )
    }
}
