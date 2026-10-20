package app.ransel.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.data.repository.AppLanguage
import app.ransel.data.repository.AppPreferences
import app.ransel.feature.currency.CurrencySheet
import app.ransel.platform.supportsInAppLanguage
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.feature.currency.RadioMark
import app.ransel.ui.symbols.Symbol
import app.ransel.feature.credits.rememberCredits
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.ThemeMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.account_about
import ransel.composeapp.generated.resources.account_confirm_reorder
import ransel.composeapp.generated.resources.account_confirm_reorder_hint
import ransel.composeapp.generated.resources.account_credits
import ransel.composeapp.generated.resources.account_default_currency
import ransel.composeapp.generated.resources.account_language
import ransel.composeapp.generated.resources.account_section_other
import ransel.composeapp.generated.resources.account_section_preferences
import ransel.composeapp.generated.resources.account_theme
import ransel.composeapp.generated.resources.account_title
import ransel.composeapp.generated.resources.currency_sheet_default_hint
import ransel.composeapp.generated.resources.currency_sheet_default_title
import ransel.composeapp.generated.resources.language_english
import ransel.composeapp.generated.resources.language_indonesian
import ransel.composeapp.generated.resources.language_sheet_hint
import ransel.composeapp.generated.resources.language_sheet_title
import ransel.composeapp.generated.resources.language_system
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import app.ransel.data.auth.SessionState
import app.ransel.platform.requiresAccount
import app.ransel.ui.components.ConfirmDialog
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ransel.composeapp.generated.resources.account_section_account
import ransel.composeapp.generated.resources.account_sign_out
import ransel.composeapp.generated.resources.account_sign_out_body
import ransel.composeapp.generated.resources.account_sign_out_pending
import ransel.composeapp.generated.resources.account_sign_out_title
import ransel.composeapp.generated.resources.account_signed_out_body
import ransel.composeapp.generated.resources.account_sync_last
import ransel.composeapp.generated.resources.account_sync_never
import ransel.composeapp.generated.resources.account_sync_now
import ransel.composeapp.generated.resources.account_sync_offline
import ransel.composeapp.generated.resources.account_sync_pending
import ransel.composeapp.generated.resources.account_sync_running
import ransel.composeapp.generated.resources.account_sync_synced
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.sign_in_action
import ransel.composeapp.generated.resources.sign_up_action
import ransel.composeapp.generated.resources.theme_dark
import ransel.composeapp.generated.resources.theme_light
import ransel.composeapp.generated.resources.theme_sheet_hint
import ransel.composeapp.generated.resources.theme_sheet_system_note
import ransel.composeapp.generated.resources.theme_sheet_title
import ransel.composeapp.generated.resources.theme_system

private enum class AccountSheet { CURRENCY, THEME, LANGUAGE }

private const val APP_VERSION = "0.1.0"

/** Account tab without sign-in (Phase 1): preferences and app info. Sync rows arrive with Phase 3. */
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    debugContent: (@Composable () -> Unit)? = null,
    onOpenCredits: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSignUp: () -> Unit = {},
    viewModel: AccountViewModel = koinViewModel(),
) {
    val prefs by viewModel.state.collectAsState()
    var sheet by rememberSaveable { mutableStateOf<AccountSheet?>(null) }
    val session by viewModel.sessionState.collectAsState()
    val pendingCount by viewModel.sync.pendingCount.collectAsState()
    val lastSyncAt by viewModel.sync.lastSyncAt.collectAsState()
    val offline by viewModel.sync.offline.collectAsState()
    val syncing by viewModel.sync.syncing.collectAsState()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(Res.string.account_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section(stringResource(Res.string.account_section_account)) {
                val user = (session as? SessionState.SignedIn)?.user
                if (user != null) {
                    ProfileRow(user.displayName, user.email)
                    if (!requiresAccount) {
                        val lastTime = lastSyncAt?.let { formatClock(it) }
                        val subtitle = when {
                            syncing -> stringResource(Res.string.account_sync_running)
                            offline -> stringResource(Res.string.account_sync_offline)
                            pendingCount > 0 -> stringResource(Res.string.account_sync_pending, pendingCount)
                            lastTime != null -> stringResource(Res.string.account_sync_last, lastTime)
                            else -> stringResource(Res.string.account_sync_never)
                        }
                        val title = if (pendingCount == 0 && !offline && !syncing) {
                            stringResource(Res.string.account_sync_synced)
                        } else {
                            stringResource(Res.string.account_sync_now)
                        }
                        SettingRow(
                            if (offline) Symbols.cloudOff else if (pendingCount > 0) Symbols.cloudUpload else Symbols.cloudDone,
                            title,
                            subtitle,
                            onClick = if (syncing) null else ({ viewModel.sync.syncNow() }),
                        )
                    }
                    val credits by rememberCredits()
                    SettingRow(Symbols.autoAwesome, stringResource(Res.string.account_credits), credits?.available?.toString(), onClick = onOpenCredits)
                    SettingRow(Symbols.logout, stringResource(Res.string.account_sign_out), null, onClick = { confirmSignOut = true })
                } else if (!requiresAccount) {
                    Text(
                        stringResource(Res.string.account_signed_out_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onSignIn, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(Res.string.sign_in_action))
                        }
                        OutlinedButton(onClick = onSignUp, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(Res.string.sign_up_action))
                        }
                    }
                }
            }
            Section(stringResource(Res.string.account_section_preferences)) {
                SettingRow(
                    Symbols.payments, stringResource(Res.string.account_default_currency), prefs.defaultCurrency,
                    onClick = { sheet = AccountSheet.CURRENCY },
                )
                SettingRow(
                    Symbols.darkMode, stringResource(Res.string.account_theme), stringResource(prefs.themeMode.label),
                    onClick = { sheet = AccountSheet.THEME },
                )
                if (supportsInAppLanguage) {
                    SettingRow(
                        Symbols.translate, stringResource(Res.string.account_language), stringResource(prefs.language.label),
                        onClick = { sheet = AccountSheet.LANGUAGE },
                    )
                }
                SwitchRow(
                    Symbols.swipeVertical,
                    stringResource(Res.string.account_confirm_reorder),
                    stringResource(Res.string.account_confirm_reorder_hint),
                    checked = !prefs.skipReorderConfirmation,
                    onCheckedChange = { viewModel.setConfirmReorder(it) },
                )
            }
            Section(stringResource(Res.string.account_section_other)) {
                SettingRow(Symbols.info, stringResource(Res.string.account_about), "v$APP_VERSION", onClick = null)
            }
            debugContent?.invoke()
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(Res.string.account_sign_out_title),
            body = buildString {
                append(stringResource(Res.string.account_sign_out_body))
                if (pendingCount > 0) append(' ').append(stringResource(Res.string.account_sign_out_pending, pendingCount))
            },
            confirmLabel = stringResource(Res.string.account_sign_out),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
            destructive = true,
            symbol = Symbols.logout,
        )
    }

    when (sheet) {
        AccountSheet.CURRENCY -> CurrencySheet(
            title = stringResource(Res.string.currency_sheet_default_title),
            subtitle = stringResource(Res.string.currency_sheet_default_hint),
            selected = prefs.defaultCurrency,
            onSelect = {
                viewModel.setCurrency(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )
        AccountSheet.THEME -> ThemeSheet(
            selected = prefs.themeMode,
            onSelect = viewModel::setTheme,
            onDismiss = { sheet = null },
        )
        AccountSheet.LANGUAGE -> LanguageSheet(
            selected = prefs.language,
            onSelect = viewModel::setLanguage,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

private val ThemeMode.label: StringResource
    get() = when (this) {
        ThemeMode.SYSTEM -> Res.string.theme_system
        ThemeMode.LIGHT -> Res.string.theme_light
        ThemeMode.DARK -> Res.string.theme_dark
    }

private val AppLanguage.label: StringResource
    get() = when (this) {
        AppLanguage.SYSTEM -> Res.string.language_system
        AppLanguage.INDONESIAN -> Res.string.language_indonesian
        AppLanguage.ENGLISH -> Res.string.language_english
    }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

@Composable
private fun SettingRow(symbol: String, title: String, value: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LeadingTile(symbol)
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Symbol(Symbols.chevronRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(symbol: String, title: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LeadingTile(symbol)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
private fun ProfileRow(name: String, email: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "14.32" in the device's time zone (the app writes times as HH.mm, like the design). */
private fun formatClock(instant: kotlin.time.Instant): String {
    val t = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.hour.toString().padStart(2, '0')}.${t.minute.toString().padStart(2, '0')}"
}

@Composable
private fun LeadingTile(symbol: String) {
    Box(
        Modifier.size(40.dp).clip(IrisShape.md).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) { Symbol(symbol, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun ThemeSheet(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Text(stringResource(Res.string.theme_sheet_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.theme_sheet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(IrisShape.lg)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            IrisShape.lg,
                        )
                        .selectable(isSelected, role = Role.RadioButton) { onSelect(mode) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemePreview(mode)
                    Row(
                        Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioMark(isSelected)
                        Text(
                            stringResource(mode.label),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Symbol(Symbols.info, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(Res.string.theme_sheet_system_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Miniature of a screen in the chosen theme (light, dark, or split for "follow system"). */
@Composable
private fun ThemePreview(mode: ThemeMode) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(IrisShape.md)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, IrisShape.md),
    ) {
        if (mode != ThemeMode.DARK) PreviewHalf(light = true, modifier = Modifier.weight(1f))
        if (mode != ThemeMode.LIGHT) PreviewHalf(light = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PreviewHalf(light: Boolean, modifier: Modifier) {
    val colors = if (light) app.ransel.ui.theme.IrisLightColors else app.ransel.ui.theme.IrisDarkColors
    Column(
        modifier.background(colors.surface).heightIn(min = 96.dp).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = 36.dp, height = 8.dp).clip(IrisShape.xs).background(colors.onSurface))
        Box(Modifier.fillMaxWidth().heightIn(min = 22.dp).clip(IrisShape.xs).background(colors.primaryContainer))
        Box(Modifier.size(width = 28.dp, height = 6.dp).clip(IrisShape.xs).background(colors.outlineVariant))
    }
}

@Composable
private fun LanguageSheet(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Text(stringResource(Res.string.language_sheet_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.language_sheet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.padding(top = 8.dp).selectableGroup()) {
            AppLanguage.entries.forEachIndexed { index, language ->
                val isSelected = language == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(isSelected, role = Role.RadioButton) { onSelect(language) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(language.label),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    RadioMark(isSelected)
                }
                if (index < AppLanguage.entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
