package app.ransel.feature.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.check_email_open_app
import ransel.composeapp.generated.resources.check_email_resend
import ransel.composeapp.generated.resources.check_email_resend_in
import ransel.composeapp.generated.resources.check_email_tip_spam
import ransel.composeapp.generated.resources.email_verified_body
import ransel.composeapp.generated.resources.email_verified_credits
import ransel.composeapp.generated.resources.email_verified_sign_in
import ransel.composeapp.generated.resources.email_verified_start
import ransel.composeapp.generated.resources.email_verified_title
import ransel.composeapp.generated.resources.invalid_invite_home
import ransel.composeapp.generated.resources.state_retry
import ransel.composeapp.generated.resources.verify_email_body
import ransel.composeapp.generated.resources.verify_email_credits_body
import ransel.composeapp.generated.resources.verify_email_credits_title
import ransel.composeapp.generated.resources.verify_email_later
import ransel.composeapp.generated.resources.verify_email_tip_now
import ransel.composeapp.generated.resources.verify_email_title
import ransel.composeapp.generated.resources.verify_link_invalid_body
import ransel.composeapp.generated.resources.verify_link_invalid_title
import ransel.composeapp.generated.resources.verify_link_new
import ransel.composeapp.generated.resources.verify_link_sent
import ransel.composeapp.generated.resources.verify_link_sign_in

private const val RESEND_SECONDS = 45

/** "Satu langkah lagi" right after sign-up (design `VerifikasiEmail`). [onLater] closes it; the app is usable already. */
@Composable
fun VerifyEmailScreen(
    email: String,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerifyEmailViewModel = koinViewModel(parameters = { parametersOf(email) }),
) {
    val state by viewModel.state.collectAsState()
    var secondsLeft by remember { mutableIntStateOf(RESEND_SECONDS) }
    LaunchedEffect(viewModel) { viewModel.resent.collect { secondsLeft = RESEND_SECONDS } }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
    }
    val uriHandler = LocalUriHandler.current
    VerifyEmailContent(
        state = state,
        secondsLeft = secondsLeft,
        onOpenEmailApp = { runCatching { uriHandler.openUri("mailto:") } },
        onResend = viewModel::resend,
        onLater = onLater,
        modifier = modifier,
    )
}

@Composable
fun VerifyEmailContent(
    state: CheckEmailUiState,
    secondsLeft: Int,
    onOpenEmailApp: () -> Unit,
    onResend: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().background(scheme.surface).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.verify_email_later), style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(
            Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IconTile(Symbols.markEmailRead, scheme.primaryContainer, scheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.verify_email_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(Res.string.verify_email_body, state.email), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
            CreditsCard(stringResource(Res.string.verify_email_credits_title), stringResource(Res.string.verify_email_credits_body))
            Column(
                Modifier.fillMaxWidth().background(scheme.surfaceVariant, IrisShape.lg).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TipLine(Symbols.inbox, stringResource(Res.string.check_email_tip_spam))
                TipLine(Symbols.luggage, stringResource(Res.string.verify_email_tip_now))
            }
            AuthProblemText(state.problem)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AuthPrimaryButton(stringResource(Res.string.check_email_open_app), onOpenEmailApp)
                ResendButton(secondsLeft, state.resending, onResend)
            }
        }
    }
}

/**
 * A verification link was opened (designs `EmailTerverifikasi`, `VerifikasiTidakBerlaku`). [signedIn] decides what
 * the buttons do: a guest (the link opened on a device without the account) is sent to sign in.
 */
@Composable
fun EmailVerifiedScreen(
    token: String,
    signedIn: Boolean,
    onExplore: () -> Unit,
    onHome: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmailVerifiedViewModel = koinViewModel(parameters = { parametersOf(token) }),
) {
    val state by viewModel.state.collectAsState()
    EmailVerifiedContent(
        state = state,
        signedIn = signedIn,
        onExplore = onExplore,
        onHome = onHome,
        onSignIn = onSignIn,
        onResend = viewModel::resend,
        onRetry = viewModel::verify,
        modifier = modifier,
    )
}

@Composable
fun EmailVerifiedContent(
    state: EmailVerifiedUiState,
    signedIn: Boolean,
    onExplore: () -> Unit,
    onHome: () -> Unit,
    onSignIn: () -> Unit,
    onResend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val surface = modifier.fillMaxSize().background(scheme.surface)
    when (state) {
        EmailVerifiedUiState.Loading -> Box(surface, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is EmailVerifiedUiState.Failed -> FullScreenState(
            title = stringResource(Res.string.email_verified_title),
            body = problemText(state.problem),
            modifier = surface,
            primaryLabel = stringResource(Res.string.state_retry),
            onPrimary = onRetry,
        )
        is EmailVerifiedUiState.Verified -> CenteredState(
            symbol = Symbols.checkCircle,
            tileColor = scheme.primaryContainer,
            symbolColor = scheme.primary,
            title = stringResource(Res.string.email_verified_title),
            body = stringResource(Res.string.email_verified_body, state.email),
            modifier = surface,
        ) {
            if (state.creditsGranted > 0) {
                CreditsCard(stringResource(Res.string.email_verified_credits, state.creditsGranted), null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (signedIn) {
                    AuthPrimaryButton(stringResource(Res.string.email_verified_start), onExplore)
                    HomeButton(onHome)
                } else {
                    AuthPrimaryButton(stringResource(Res.string.email_verified_sign_in), onSignIn)
                }
            }
        }
        is EmailVerifiedUiState.Invalid -> CenteredState(
            symbol = Symbols.error,
            tileColor = scheme.errorContainer,
            symbolColor = scheme.error,
            title = stringResource(Res.string.verify_link_invalid_title),
            body = stringResource(Res.string.verify_link_invalid_body),
            errorCode = "VERIFY_TOKEN_INVALID",
            modifier = surface,
        ) {
            if (state.resent) {
                Text(stringResource(Res.string.verify_link_sent), style = MaterialTheme.typography.bodyMedium, color = scheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            AuthProblemText(state.problem)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (signedIn) {
                    if (!state.resent) AuthPrimaryButton(stringResource(Res.string.verify_link_new), onResend, loading = state.resending)
                    HomeButton(onHome)
                } else {
                    AuthPrimaryButton(stringResource(Res.string.verify_link_sign_in), onSignIn)
                }
            }
        }
    }
}

@Composable
private fun CenteredState(
    symbol: String,
    tileColor: Color,
    symbolColor: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    errorCode: String? = null,
    actions: @Composable () -> Unit,
) {
    Box(modifier.statusBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            IconTile(symbol, tileColor, symbolColor, big = true)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (errorCode != null) {
                    Text(errorCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
        }
    }
}

@Composable
private fun IconTile(symbol: String, background: Color, tint: Color, big: Boolean = false) {
    Box(
        Modifier.size(if (big) 96.dp else 72.dp).background(background, if (big) IrisShape.xl else IrisShape.lg),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(symbol, size = if (big) 56.dp else 40.dp, filled = true, tint = tint)
    }
}

/** Purple card with the Explore sparkle (designs `VerifikasiEmail`, `EmailTerverifikasi`). */
@Composable
private fun CreditsCard(title: String, body: String?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(scheme.tertiaryContainer, IrisShape.lg).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(IrisTheme.accents.purple, IrisShape.md), contentAlignment = Alignment.Center) {
            Symbol(Symbols.autoAwesome, size = 22.dp, filled = true, tint = IrisTheme.accents.onAccent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun TipLine(symbol: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Symbol(symbol, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResendButton(secondsLeft: Int, resending: Boolean, onResend: () -> Unit) {
    val countdown = "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}"
    TextButton(onClick = onResend, enabled = secondsLeft == 0 && !resending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(
            if (secondsLeft > 0) stringResource(Res.string.check_email_resend_in, countdown) else stringResource(Res.string.check_email_resend),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun HomeButton(onHome: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onHome),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(Res.string.invalid_invite_home), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Preview
@Composable
private fun VerifyEmailPreview() = IrisPreview {
    VerifyEmailContent(CheckEmailUiState("septian@contoh.id"), secondsLeft = 45, onOpenEmailApp = {}, onResend = {}, onLater = {})
}

@Preview
@Composable
private fun EmailVerifiedPreview() = IrisPreview {
    EmailVerifiedContent(EmailVerifiedUiState.Verified("septian@contoh.id", 30), signedIn = true, onExplore = {}, onHome = {}, onSignIn = {}, onResend = {}, onRetry = {})
}

@Preview
@Composable
private fun VerifyLinkInvalidPreview() = IrisPreview {
    EmailVerifiedContent(EmailVerifiedUiState.Invalid(), signedIn = true, onExplore = {}, onHome = {}, onSignIn = {}, onResend = {}, onRetry = {})
}
