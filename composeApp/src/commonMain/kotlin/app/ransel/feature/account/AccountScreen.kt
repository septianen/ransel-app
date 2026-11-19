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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.account_app_version
import ransel.composeapp.generated.resources.account_default_currency
import ransel.composeapp.generated.resources.account_notifications
import ransel.composeapp.generated.resources.account_payment
import ransel.composeapp.generated.resources.account_profile_security
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

private enum class AccountSheet { CURRENCY, THEME }

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
            signedOut = user == null && !requiresAccount,
            appVersion = installedAppVersion,
            onOpenProfileSecurity = onOpenProfileSecurity,
            onOpenPayment = onOpenPayment,
            onOpenCredits = onOpenCredits,
            onSyncNow = { viewModel.sync.syncNow() },
            onOpenNotifications = onOpenNotifications,
            onOpenCurrency = { sheet = AccountSheet.CURRENCY },
            onOpenTheme = { sheet = AccountSheet.THEME },
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
    signedOut: Boolean,
    appVersion: String?,
    onOpenProfileSecurity: () -> Unit,
    onOpenPayment: () -> Unit,
    onOpenCredits: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenTheme: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    debugContent: (@Composable () -> Unit)? = null,
) {
    val signedIn = name != null
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
                    Symbols.darkMode,
                    stringResource(Res.string.account_theme),
                    value = themeLabel,
                    onClick = onOpenTheme,
                    showDivider = true,
                )
            }
            debugContent?.invoke()
            if (signedIn) {
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SignOutButton(onSignOut)
                    appVersion?.let {
                        Text(
                            stringResource(Res.string.account_app_version, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                appVersion?.let {
                    Text(
                        stringResource(Res.string.account_app_version, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
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
        signedOut = false,
        appVersion = "1.0.1",
        onOpenProfileSecurity = {},
        onOpenPayment = {},
        onOpenCredits = {},
        onSyncNow = {},
        onOpenNotifications = {},
        onOpenCurrency = {},
        onOpenTheme = {},
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
        signedOut = false,
        appVersion = "1.0.1",
        onOpenProfileSecurity = {},
        onOpenPayment = {},
        onOpenCredits = {},
        onSyncNow = {},
        onOpenNotifications = {},
        onOpenCurrency = {},
        onOpenTheme = {},
        onSignOut = {},
        onSignIn = {},
        onSignUp = {},
    )
}
