package app.ransel.admin.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminSession
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.LocalEnvironments
import app.ransel.admin.feature.shell.EnvironmentPicker
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.app_name
import app.ransel.admin.generated.resources.app_subtitle
import app.ransel.admin.generated.resources.error_code
import app.ransel.admin.generated.resources.sign_in_body
import app.ransel.admin.generated.resources.sign_in_email
import app.ransel.admin.generated.resources.sign_in_error_credentials
import app.ransel.admin.generated.resources.sign_in_error_disabled
import app.ransel.admin.generated.resources.sign_in_error_fields
import app.ransel.admin.generated.resources.sign_in_error_rate_limited
import app.ransel.admin.generated.resources.sign_in_hide_password
import app.ransel.admin.generated.resources.sign_in_password
import app.ransel.admin.generated.resources.sign_in_show_password
import app.ransel.admin.generated.resources.sign_in_submit
import app.ransel.admin.generated.resources.sign_in_title
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.ErrorLine
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.isValidEmail
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val missingFields: Boolean = false,
    val error: ApiFailure? = null,
)

/** Signs in with a normal RANSEL account; [AdminSession] then decides Ready or "Bukan akun admin". */
class SignInPresenter(private val session: AdminSession, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun setEmail(value: String) = _state.update { it.copy(email = value, missingFields = false, error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, missingFields = false, error = null) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        if (!isValidEmail(current.email.trim()) || current.password.isEmpty()) {
            _state.update { it.copy(missingFields = true) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            val error = session.signIn(current.email, current.password)
            _state.update { it.copy(loading = false, error = error) }
        }
    }
}

@Composable
fun SignInScreen(session: AdminSession) {
    val presenter = rememberPresenter { SignInPresenter(session, it) }
    val state by presenter.state.collectAsState()
    SignInContent(state, presenter::setEmail, presenter::setPassword, presenter::submit)
}

@Composable
fun SignInContent(
    state: SignInUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var showPassword by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(scheme.surfaceContainer).verticalScroll(rememberScrollState()).padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AdminCard(Modifier.padding(top = 72.dp).widthIn(max = 440.dp).fillMaxWidth(), padding = 28.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandMark()
                Text(stringResource(Res.string.sign_in_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(Res.string.sign_in_body), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                EnvironmentPicker(LocalEnvironments.current)
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmail,
                    label = { Text(stringResource(Res.string.sign_in_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPassword,
                    label = { Text(stringResource(Res.string.sign_in_password)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Symbol(
                                if (showPassword) Symbols.visibilityOff else Symbols.visibility,
                                size = 20.dp,
                                contentDescription = stringResource(if (showPassword) Res.string.sign_in_hide_password else Res.string.sign_in_show_password),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state.missingFields -> ErrorLine(stringResource(Res.string.sign_in_error_fields))
                    state.error != null -> ErrorLine(signInErrorText(state.error))
                }
                Button(onClick = onSubmit, enabled = !state.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    else Text(stringResource(Res.string.sign_in_submit))
                }
            }
        }
    }
}

@Composable
private fun signInErrorText(error: ApiFailure): String = when (error.code) {
    ErrorCode.INVALID_CREDENTIALS -> stringResource(Res.string.sign_in_error_credentials)
    ErrorCode.ACCOUNT_DISABLED -> stringResource(Res.string.sign_in_error_disabled)
    ErrorCode.RATE_LIMITED -> stringResource(Res.string.sign_in_error_rate_limited)
    else -> failureText(error) + if (error.isConnection) "" else " · " + stringResource(Res.string.error_code, error.code)
}

/** The Karung mark from the sidebar: purple tile with the app name. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(scheme.tertiary, IrisShape.sm), contentAlignment = Alignment.Center) {
            Symbol(Symbols.luggage, size = 22.dp, tint = scheme.onTertiary)
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(Res.string.app_subtitle), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}
