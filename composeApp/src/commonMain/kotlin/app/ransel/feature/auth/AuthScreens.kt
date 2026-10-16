package app.ransel.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.capybara_tired_phone
import ransel.composeapp.generated.resources.check_email_body
import ransel.composeapp.generated.resources.check_email_change
import ransel.composeapp.generated.resources.check_email_open_app
import ransel.composeapp.generated.resources.check_email_resend
import ransel.composeapp.generated.resources.check_email_resend_in
import ransel.composeapp.generated.resources.check_email_tip_address
import ransel.composeapp.generated.resources.check_email_tip_spam
import ransel.composeapp.generated.resources.check_email_title
import ransel.composeapp.generated.resources.check_email_wrong
import ransel.composeapp.generated.resources.field_email
import ransel.composeapp.generated.resources.field_error_email
import ransel.composeapp.generated.resources.field_error_name
import ransel.composeapp.generated.resources.field_error_password
import ransel.composeapp.generated.resources.field_error_terms
import ransel.composeapp.generated.resources.field_name
import ransel.composeapp.generated.resources.forgot_password_link
import ransel.composeapp.generated.resources.forgot_send
import ransel.composeapp.generated.resources.forgot_subtitle
import ransel.composeapp.generated.resources.forgot_title
import ransel.composeapp.generated.resources.have_account
import ransel.composeapp.generated.resources.new_password_done_body
import ransel.composeapp.generated.resources.new_password_done_title
import ransel.composeapp.generated.resources.new_password_field
import ransel.composeapp.generated.resources.new_password_mismatch
import ransel.composeapp.generated.resources.new_password_repeat
import ransel.composeapp.generated.resources.new_password_save
import ransel.composeapp.generated.resources.new_password_subtitle
import ransel.composeapp.generated.resources.new_password_title
import ransel.composeapp.generated.resources.no_account_yet
import ransel.composeapp.generated.resources.or_divider
import ransel.composeapp.generated.resources.remember_password
import ransel.composeapp.generated.resources.reset_token_invalid_body
import ransel.composeapp.generated.resources.reset_token_invalid_title
import ransel.composeapp.generated.resources.session_expired_body
import ransel.composeapp.generated.resources.session_expired_offline
import ransel.composeapp.generated.resources.session_expired_pending
import ransel.composeapp.generated.resources.session_expired_sign_in
import ransel.composeapp.generated.resources.session_expired_title
import ransel.composeapp.generated.resources.sign_in_action
import ransel.composeapp.generated.resources.sign_in_subtitle
import ransel.composeapp.generated.resources.sign_in_title
import ransel.composeapp.generated.resources.sign_up_action
import ransel.composeapp.generated.resources.sign_up_link
import ransel.composeapp.generated.resources.sign_up_subtitle
import ransel.composeapp.generated.resources.sign_up_title
import ransel.composeapp.generated.resources.terms_agree
import ransel.composeapp.generated.resources.use_without_account

@Composable
fun SignInScreen(
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    onContinueWithoutAccount: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    AuthScaffold(
        title = stringResource(Res.string.sign_in_title),
        subtitle = stringResource(Res.string.sign_in_subtitle),
        modifier = modifier,
        onBack = onBack,
        footer = { AuthFooter(stringResource(Res.string.no_account_yet), stringResource(Res.string.sign_up_link), onSignUp) },
    ) {
        IrisTextField(
            label = stringResource(Res.string.field_email),
            value = state.email,
            onValueChange = viewModel::setEmail,
            leadingSymbol = Symbols.mail,
            error = if (state.emailError) stringResource(Res.string.field_error_email) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            error = if (state.passwordError) stringResource(Res.string.field_error_password) else null,
        )
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(Res.string.forgot_password_link), style = MaterialTheme.typography.labelLarge)
        }
        AuthProblemText(state.problem)
        AuthPrimaryButton(stringResource(Res.string.sign_in_action), viewModel::submit, loading = state.loading)
        if (onContinueWithoutAccount != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text(stringResource(Res.string.or_divider), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }
            AuthSecondaryButton(stringResource(Res.string.use_without_account), onContinueWithoutAccount, symbol = Symbols.noAccounts)
        }
    }
}

@Composable
fun SignUpScreen(
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    AuthScaffold(
        title = stringResource(Res.string.sign_up_title),
        subtitle = stringResource(Res.string.sign_up_subtitle),
        modifier = modifier,
        onBack = onBack,
        footer = { AuthFooter(stringResource(Res.string.have_account), stringResource(Res.string.sign_in_action), onSignIn) },
    ) {
        IrisTextField(
            label = stringResource(Res.string.field_name),
            value = state.name,
            onValueChange = viewModel::setName,
            leadingSymbol = Symbols.person,
            error = if (state.nameError) stringResource(Res.string.field_error_name) else null,
        )
        IrisTextField(
            label = stringResource(Res.string.field_email),
            value = state.email,
            onValueChange = viewModel::setEmail,
            leadingSymbol = Symbols.mail,
            error = if (state.emailError) stringResource(Res.string.field_error_email) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            error = if (state.passwordError) stringResource(Res.string.field_error_password) else null,
        )
        PasswordRules(state.passwordCheck)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.acceptedTerms, onCheckedChange = viewModel::setAcceptedTerms)
            Text(stringResource(Res.string.terms_agree), style = MaterialTheme.typography.bodyMedium)
        }
        if (state.termsError) {
            Text(stringResource(Res.string.field_error_terms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        AuthProblemText(state.problem)
        AuthPrimaryButton(stringResource(Res.string.sign_up_action), viewModel::submit, loading = state.loading)
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onSent: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.sent.collect(onSent) }
    AuthScaffold(
        title = stringResource(Res.string.forgot_title),
        subtitle = stringResource(Res.string.forgot_subtitle),
        modifier = modifier,
        onBack = onBack,
        footer = { AuthFooter(stringResource(Res.string.remember_password), stringResource(Res.string.sign_in_action), onBack) },
    ) {
        IrisTextField(
            label = stringResource(Res.string.field_email),
            value = state.email,
            onValueChange = viewModel::setEmail,
            leadingSymbol = Symbols.mail,
            error = if (state.emailError) stringResource(Res.string.field_error_email) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        AuthProblemText(state.problem)
        AuthPrimaryButton(stringResource(Res.string.forgot_send), viewModel::submit, loading = state.loading)
    }
}

private const val RESEND_SECONDS = 45

@Composable
fun CheckEmailScreen(
    email: String,
    onChangeEmail: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckEmailViewModel = koinViewModel(parameters = { parametersOf(email) }),
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
    AuthScaffold(
        title = stringResource(Res.string.check_email_title),
        subtitle = stringResource(Res.string.check_email_body, state.email),
        modifier = modifier,
        onBack = onChangeEmail,
        footer = { AuthFooter(stringResource(Res.string.check_email_wrong), stringResource(Res.string.check_email_change), onChangeEmail) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tip(Symbols.inbox, stringResource(Res.string.check_email_tip_spam))
            Tip(Symbols.alternateEmail, stringResource(Res.string.check_email_tip_address))
        }
        AuthSecondaryButton(stringResource(Res.string.check_email_open_app), onClick = { runCatching { uriHandler.openUri("mailto:") } }, symbol = Symbols.openInNew)
        AuthProblemText(state.problem)
        val countdown = "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}"
        TextButton(
            onClick = viewModel::resend,
            enabled = secondsLeft == 0 && !state.resending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (secondsLeft > 0) stringResource(Res.string.check_email_resend_in, countdown)
                else stringResource(Res.string.check_email_resend),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun Tip(symbol: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Symbol(symbol, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NewPasswordScreen(
    token: String,
    onDone: () -> Unit,
    onRequestNewLink: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewPasswordViewModel = koinViewModel(parameters = { parametersOf(token) }),
) {
    val state by viewModel.state.collectAsState()
    when {
        state.done -> FullScreenState(
            title = stringResource(Res.string.new_password_done_title),
            body = stringResource(Res.string.new_password_done_body),
            modifier = modifier.background(MaterialTheme.colorScheme.surface),
            illustration = painterResource(Res.drawable.capybara_tired_phone),
            primaryLabel = stringResource(Res.string.sign_in_action),
            onPrimary = onDone,
        )
        state.problem == AuthProblem.RESET_TOKEN_INVALID -> FullScreenState(
            title = stringResource(Res.string.reset_token_invalid_title),
            body = stringResource(Res.string.reset_token_invalid_body),
            modifier = modifier.background(MaterialTheme.colorScheme.surface),
            illustration = painterResource(Res.drawable.capybara_tired_phone),
            primaryLabel = stringResource(Res.string.forgot_send),
            onPrimary = onRequestNewLink,
        )
        else -> AuthScaffold(
            title = stringResource(Res.string.new_password_title),
            subtitle = stringResource(Res.string.new_password_subtitle),
            modifier = modifier,
        ) {
            PasswordField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = stringResource(Res.string.new_password_field),
                error = if (state.passwordError) stringResource(Res.string.field_error_password) else null,
            )
            PasswordRules(state.passwordCheck)
            PasswordField(
                value = state.repeat,
                onValueChange = viewModel::setRepeat,
                label = stringResource(Res.string.new_password_repeat),
                error = if (state.mismatch) stringResource(Res.string.new_password_mismatch) else null,
            )
            AuthProblemText(state.problem)
            AuthPrimaryButton(stringResource(Res.string.new_password_save), viewModel::submit, loading = state.loading)
        }
    }
}

/** Shown when the refresh token no longer works. [pendingChanges] is the number of unsent local edits. */
@Composable
fun SessionExpiredScreen(
    email: String,
    pendingChanges: Int,
    onSignIn: () -> Unit,
    onUseOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = buildString {
        append(stringResource(Res.string.session_expired_body))
        if (pendingChanges > 0) append(' ').append(stringResource(Res.string.session_expired_pending, pendingChanges))
    }
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.weight(1f)) {
            FullScreenState(
                title = stringResource(Res.string.session_expired_title),
                body = body,
                illustration = painterResource(Res.drawable.capybara_tired_phone),
                primaryLabel = stringResource(Res.string.session_expired_sign_in),
                onPrimary = onSignIn,
                secondaryLabel = stringResource(Res.string.session_expired_offline),
                onSecondary = onUseOffline,
            )
        }
        Text(
            email,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
    }
}
