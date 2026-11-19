package app.ransel.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.data.auth.SessionState
import app.ransel.feature.credits.rememberCredits
import app.ransel.feature.currency.CurrencySheet
import app.ransel.platform.installedAppVersion
import app.ransel.platform.requiresAccount
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.tour.TourController
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.account_about
import ransel.composeapp.generated.resources.account_confirm_reorder
import ransel.composeapp.generated.resources.account_confirm_reorder_hint
import ransel.composeapp.generated.resources.account_default_currency
import ransel.composeapp.generated.resources.account_language
import ransel.composeapp.generated.resources.account_notifications
import ransel.composeapp.generated.resources.account_payment
import ransel.composeapp.generated.resources.account_privacy
import ransel.composeapp.generated.resources.account_profile_security
import ransel.composeapp.generated.resources.account_section_info
import ransel.composeapp.generated.resources.account_section_account
import ransel.composeapp.generated.resources.account_section_preferences
import ransel.composeapp.generated.resources.account_sign_out
import ransel.composeapp.generated.resources.account_sign_out_body
import ransel.composeapp.generated.resources.account_sign_out_pending
import ransel.composeapp.generated.resources.account_sign_out_title
import ransel.composeapp.generated.resources.account_signed_out_body
import ransel.composeapp.generated.resources.account_theme
import ransel.composeapp.generated.resources.account_title
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.change_password_done
import ransel.composeapp.generated.resources.currency_sheet_default_hint
import ransel.composeapp.generated.resources.currency_sheet_default_title
import ransel.composeapp.generated.resources.sign_in_action
import ransel.composeapp.generated.resources.sign_up_action
import ransel.composeapp.generated.resources.tour_replay
import ransel.composeapp.generated.resources.tour_replay_hint

private enum class AccountSheet { CURRENCY, THEME, LANGUAGE }

@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    debugContent: (@Composable () -> Unit)? = null,
    onOpenProfileSecurity: () -> Unit = {},
    onOpenPayment: () -> Unit = {},
    onOpenCredits: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSignUp: () -> Unit = {},
    onReplayTour: () -> Unit = {},
    passwordChanged: Boolean = false,
    onPasswordChangedShown: () -> Unit = {},
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
    val snackbar = remember { SnackbarHostState() }
    val tour: TourController = koinInject()
    val tourScope = rememberCoroutineScope()
    val changedText = stringResource(Res.string.change_password_done)
    LaunchedEffect(passwordChanged) {
        if (!passwordChanged) return@LaunchedEffect
        snackbar.showSnackbar(changedText, duration = SnackbarDuration.Long)
        onPasswordChangedShown()
    }
    val user = (session as? SessionState.SignedIn)?.user
    val credits by rememberCredits()
    val sync = if (user != null && !requiresAccount) {
        syncTileSnapshot(syncing = syncing, offline = offline, lastSyncAt = lastSyncAt)
    } else {
        null
    }

    Box(modifier) {
        AccountContent(
            name = user?.displayName,
            email = user?.email,
            avatarUrl = user?.avatarUrl,
            credits = user?.let { credits?.available ?: 0 },
            sync = sync,
            currency = prefs.defaultCurrency,
            themeLabel = stringResource(prefs.themeMode.label),
            languageLabel = stringResource(prefs.language.label),
            confirmReorder = !prefs.skipReorderConfirmation,
            signedOut = user == null && !requiresAccount,
            appVersion = installedAppVersion,
            onOpenProfileSecurity = onOpenProfileSecurity,
            onOpenPayment = onOpenPayment,
            onOpenCredits = onOpenCredits,
            onSyncNow = { viewModel.sync.syncNow() },
            onOpenNotifications = onOpenNotifications,
            onOpenCurrency = { sheet = AccountSheet.CURRENCY },
            onOpenLanguage = { sheet = AccountSheet.LANGUAGE },
            onOpenTheme = { sheet = AccountSheet.THEME },
            onConfirmReorderChange = viewModel::setConfirmReorder,
            onReplayTour = { tourScope.launch { tour.replay(); onReplayTour() } },
            onSignOut = { confirmSignOut = true },
            onSignIn = onSignIn,
            onSignUp = onSignUp,
            debugContent = debugContent,
        )

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
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) { data -> PasswordChangedSnackbar(data.visuals.message) }
    }
}

@Composable
internal fun AccountContent(
    name: String?,
    email: String?,
    avatarUrl: String?,
    credits: Int?,
    sync: SyncTileSnapshot?,
    currency: String,
    themeLabel: String,
    languageLabel: String,
    confirmReorder: Boolean,
    signedOut: Boolean,
    appVersion: String?,
    onOpenProfileSecurity: () -> Unit,
    onOpenPayment: () -> Unit,
    onOpenCredits: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenTheme: () -> Unit,
    onConfirmReorderChange: (Boolean) -> Unit,
    onReplayTour: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    debugContent: (@Composable () -> Unit)? = null,
) {
    val signedIn = name != null
    val links = LocalUriHandler.current
    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(Res.string.account_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            if (signedIn) {
                ProfileCard(name.orEmpty(), email.orEmpty(), avatarUrl, onOpenProfileSecurity)
                StatusTilesRow(
                    credits = credits,
                    sync = sync,
                    onCredits = onOpenCredits,
                    onSync = onSyncNow,
                )
                AccountGroupLabel(stringResource(Res.string.account_section_account))
                AccountGroupCard {
                    AccountSettingRow(Symbols.lock, stringResource(Res.string.account_profile_security), onClick = onOpenProfileSecurity)
                    AccountSettingRow(Symbols.creditCard, stringResource(Res.string.account_payment), onClick = onOpenPayment, showDivider = true)
                }
            } else if (signedOut) {
                Text(
                    stringResource(Res.string.account_signed_out_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSignIn, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.sign_in_action))
                    }
                    OutlinedButton(onClick = onSignUp, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.sign_up_action))
                    }
                }
            }
            AccountGroupLabel(stringResource(Res.string.account_section_preferences))
            AccountGroupCard {
                if (signedIn) {
                    AccountSettingRow(Symbols.notifications, stringResource(Res.string.account_notifications), onClick = onOpenNotifications)
                }
                AccountSettingRow(
                    Symbols.payments,
                    stringResource(Res.string.account_default_currency),
                    value = currency,
                    onClick = onOpenCurrency,
                    showDivider = signedIn,
                )
                AccountSettingRow(
                    Symbols.translate,
                    stringResource(Res.string.account_language),
                    value = languageLabel,
                    onClick = onOpenLanguage,
                    showDivider = true,
                )
                AccountSettingRow(
                    Symbols.darkMode,
                    stringResource(Res.string.account_theme),
                    value = themeLabel,
                    onClick = onOpenTheme,
                    showDivider = true,
                )
                AccountSwitchRow(
                    Symbols.swipeVertical,
                    stringResource(Res.string.account_confirm_reorder),
                    stringResource(Res.string.account_confirm_reorder_hint),
                    checked = confirmReorder,
                    onCheckedChange = onConfirmReorderChange,
                    showDivider = true,
                )
            }
            AccountGroupLabel(stringResource(Res.string.account_section_info))
            AccountGroupCard {
                AccountSettingRow(
                    Symbols.help,
                    stringResource(Res.string.tour_replay),
                    subtitle = stringResource(Res.string.tour_replay_hint),
                    onClick = onReplayTour,
                )
                AccountSettingRow(
                    Symbols.lock,
                    stringResource(Res.string.account_privacy),
                    onClick = { runCatching { links.openUri(PRIVACY_POLICY_URL) } },
                    trailing = AccountRowTrailing.OPEN_IN_NEW,
                    showDivider = true,
                )
                AccountSettingRow(
                    Symbols.info,
                    stringResource(Res.string.account_about),
                    value = appVersion?.let { "v$it" },
                    onClick = null,
                    showDivider = true,
                )
            }
            if (signedIn) {
                Column(Modifier.padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SignOutButton(onSignOut)
                }
            }
            debugContent?.invoke()
        }
    }
}

@Preview
@Composable
private fun AccountSyncingPreview() = IrisPreview {
    AccountContent(
        name = "Dahyun",
        email = "test@gmail.com",
        avatarUrl = null,
        credits = 20,
        sync = SyncTileSnapshot(SyncTileKind.SYNCING, "20:11"),
        currency = "IDR",
        themeLabel = "Light",
        languageLabel = "Indonesian",
        confirmReorder = true,
        signedOut = false,
        appVersion = "1.0.1",
        onOpenProfileSecurity = {},
        onOpenPayment = {},
        onOpenCredits = {},
        onSyncNow = {},
        onOpenNotifications = {},
        onOpenCurrency = {},
        onOpenLanguage = {},
        onOpenTheme = {},
        onConfirmReorderChange = {},
        onReplayTour = {},
        onSignOut = {},
        onSignIn = {},
        onSignUp = {},
    )
}

@Preview
@Composable
private fun AccountSignedInPreview() = IrisPreview {
    AccountContent(
        name = "Dahyun",
        email = "test@gmail.com",
        avatarUrl = null,
        credits = 20,
        sync = SyncTileSnapshot(SyncTileKind.IDLE, "20:11"),
        currency = "IDR",
        themeLabel = "Light",
        languageLabel = "Indonesian",
        confirmReorder = true,
        signedOut = false,
        appVersion = "1.0.1",
        onOpenProfileSecurity = {},
        onOpenPayment = {},
        onOpenCredits = {},
        onSyncNow = {},
        onOpenNotifications = {},
        onOpenCurrency = {},
        onOpenLanguage = {},
        onOpenTheme = {},
        onConfirmReorderChange = {},
        onReplayTour = {},
        onSignOut = {},
        onSignIn = {},
        onSignUp = {},
    )
}
