package app.ransel.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.content.stringResource
import app.ransel.data.auth.AuthRepository
import app.ransel.data.remote.ApiResult
import app.ransel.feature.auth.PasswordField
import app.ransel.feature.auth.PasswordRules
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.checkPassword
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.auth_error_connection
import ransel.composeapp.generated.resources.auth_error_generic
import ransel.composeapp.generated.resources.auth_error_rate_limited
import ransel.composeapp.generated.resources.change_password_action
import ransel.composeapp.generated.resources.change_password_confirm
import ransel.composeapp.generated.resources.change_password_confirm_empty
import ransel.composeapp.generated.resources.change_password_current
import ransel.composeapp.generated.resources.change_password_current_empty
import ransel.composeapp.generated.resources.change_password_lead
import ransel.composeapp.generated.resources.change_password_mismatch
import ransel.composeapp.generated.resources.change_password_new
import ransel.composeapp.generated.resources.change_password_new_empty
import ransel.composeapp.generated.resources.change_password_placeholder
import ransel.composeapp.generated.resources.change_password_same
import ransel.composeapp.generated.resources.change_password_title
import ransel.composeapp.generated.resources.change_password_wrong
import ransel.composeapp.generated.resources.field_error_password
import androidx.compose.foundation.clickable

/**
 * What one password box can say after submit. [WEAK] is the register/reset rule (`checkPassword`, and the server's
 * 72-byte limit reported as TOO_LONG). The screen does not invent a different minimum length.
 */
enum class PasswordFieldError { EMPTY, MISMATCH, WEAK, SAME, WRONG }

/** Shown under the form. Success is not one of these: it leaves the screen. */
enum class ChangePasswordFormProblem { RATE_LIMITED, CONNECTION, GENERIC }

internal data class ChangePasswordErrors(
    val current: PasswordFieldError? = null,
    val newPassword: PasswordFieldError? = null,
    val confirm: PasswordFieldError? = null,
) {
    val any: Boolean get() = current != null || newPassword != null || confirm != null
}

/** Client checks before the request. Confirm never leaves the device. A weak password wins over "same as current". */
internal fun clientChangePasswordErrors(current: String, newPassword: String, confirm: String): ChangePasswordErrors {
    val weak = newPassword.isNotEmpty() && !checkPassword(newPassword).isValid
    val same = newPassword.isNotEmpty() && current.isNotEmpty() && newPassword == current && !weak
    val mismatch = newPassword.isNotEmpty() && confirm.isNotEmpty() && newPassword != confirm
    return ChangePasswordErrors(
        current = if (current.isEmpty()) PasswordFieldError.EMPTY else null,
        newPassword = when {
            newPassword.isEmpty() -> PasswordFieldError.EMPTY
            weak -> PasswordFieldError.WEAK
            same -> PasswordFieldError.SAME
            else -> null
        },
        confirm = when {
            confirm.isEmpty() -> PasswordFieldError.EMPTY
            mismatch -> PasswordFieldError.MISMATCH
            else -> null
        },
    )
}

/** Server answer that is not success. Field errors stay on the boxes; the rest is one line under the form. */
internal fun changePasswordFailure(code: String, details: Map<String, String>?): ChangePasswordServerError = when {
    code == ErrorCode.CURRENT_PASSWORD_WRONG -> ChangePasswordServerError.CurrentWrong
    code == ErrorCode.VALIDATION_FAILED && details?.get("newPassword") == "SAME_AS_CURRENT" -> ChangePasswordServerError.Same
    code == ErrorCode.VALIDATION_FAILED && details?.get("newPassword") in WEAK_DETAILS -> ChangePasswordServerError.Weak
    code == ErrorCode.RATE_LIMITED -> ChangePasswordServerError.Form(ChangePasswordFormProblem.RATE_LIMITED)
    code == ErrorCode.NETWORK_TIMEOUT || code == ErrorCode.NETWORK_UNAVAILABLE ->
        ChangePasswordServerError.Form(ChangePasswordFormProblem.CONNECTION)
    else -> ChangePasswordServerError.Form(ChangePasswordFormProblem.GENERIC)
}

private val WEAK_DETAILS = setOf("WEAK_PASSWORD", "TOO_LONG")

internal sealed interface ChangePasswordServerError {
    data object CurrentWrong : ChangePasswordServerError
    data object Weak : ChangePasswordServerError
    data object Same : ChangePasswordServerError
    data class Form(val problem: ChangePasswordFormProblem) : ChangePasswordServerError
}

data class ChangePasswordUiState(
    val current: String = "",
    val newPassword: String = "",
    val confirm: String = "",
    val currentError: PasswordFieldError? = null,
    val newError: PasswordFieldError? = null,
    val confirmError: PasswordFieldError? = null,
    val formProblem: ChangePasswordFormProblem? = null,
    val loading: Boolean = false,
)

class ChangePasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    private val _changed = Channel<Unit>(Channel.BUFFERED)
    /** The password was replaced and this session is still the one that changed it. */
    val changed = _changed.receiveAsFlow()

    fun setCurrent(value: String) = _state.update { it.copy(current = value, currentError = null, formProblem = null) }
    fun setNew(value: String) = _state.update { it.copy(newPassword = value, newError = null, formProblem = null) }
    fun setConfirm(value: String) = _state.update { it.copy(confirm = value, confirmError = null, formProblem = null) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val errors = clientChangePasswordErrors(current.current, current.newPassword, current.confirm)
        if (errors.any) {
            _state.update {
                it.copy(
                    currentError = errors.current,
                    newError = errors.newPassword,
                    confirmError = errors.confirm,
                    formProblem = null,
                )
            }
            return
        }
        _state.update { it.copy(loading = true, currentError = null, newError = null, confirmError = null, formProblem = null) }
        viewModelScope.launch {
            when (val result = auth.changePassword(current.current, current.newPassword)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false) }
                    _changed.send(Unit)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false).withServer(changePasswordFailure(result.error.code, result.error.details))
                }
            }
        }
    }
}

private fun ChangePasswordUiState.withServer(error: ChangePasswordServerError): ChangePasswordUiState = when (error) {
    ChangePasswordServerError.CurrentWrong -> copy(currentError = PasswordFieldError.WRONG)
    ChangePasswordServerError.Weak -> copy(newError = PasswordFieldError.WEAK)
    ChangePasswordServerError.Same -> copy(newError = PasswordFieldError.SAME)
    is ChangePasswordServerError.Form -> copy(formProblem = error.problem)
}

/**
 * Akun → Ubah password. Full screen (three fields plus a sticky button do not fit a sheet).
 * Built from the IRIS handoff: top bar, label-above fields, pill button. No forgot-password link and no illustration.
 */
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    viewModel: ChangePasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.changed.collect { onChanged() } }
    ChangePasswordContent(
        state = state,
        onBack = onBack,
        onCurrent = viewModel::setCurrent,
        onNew = viewModel::setNew,
        onConfirm = viewModel::setConfirm,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun ChangePasswordContent(
    state: ChangePasswordUiState,
    onBack: () -> Unit,
    onCurrent: (String) -> Unit,
    onNew: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val placeholder = stringResource(Res.string.change_password_placeholder)
    Column(
        modifier
            .fillMaxSize()
            .background(scheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Text(
                stringResource(Res.string.change_password_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(Res.string.change_password_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            PasswordField(
                value = state.current,
                onValueChange = onCurrent,
                label = stringResource(Res.string.change_password_current),
                placeholder = placeholder,
                leadingSymbol = null,
                error = currentErrorText(state.currentError),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(
                    value = state.newPassword,
                    onValueChange = onNew,
                    label = stringResource(Res.string.change_password_new),
                    placeholder = placeholder,
                    leadingSymbol = null,
                    error = newErrorText(state.newError),
                )
                PasswordRules(checkPassword(state.newPassword))
            }
            PasswordField(
                value = state.confirm,
                onValueChange = onConfirm,
                label = stringResource(Res.string.change_password_confirm),
                placeholder = placeholder,
                leadingSymbol = null,
                error = confirmErrorText(state.confirmError),
            )
            state.formProblem?.let { problem ->
                Text(problem.text(), style = MaterialTheme.typography.bodyMedium, color = scheme.error)
            }
        }
        Surface(color = scheme.surface, shadowElevation = 8.dp) {
            Column {
                HorizontalDivider(color = scheme.outlineVariant)
                LoadingButton(
                    onClick = onSubmit,
                    loading = state.loading,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp).height(48.dp),
                ) {
                    Text(stringResource(Res.string.change_password_action), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Snackbar on Akun after a successful change. This device is still signed in. */
@Composable
fun PasswordChangedSnackbar(message: String, modifier: Modifier = Modifier) {
    Snackbar(
        modifier = modifier,
        shape = IrisShape.sm,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol(
                Symbols.checkCircle,
                size = 20.dp,
                filled = true,
                tint = MaterialTheme.colorScheme.inversePrimary,
            )
            Text(message, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PasswordFieldError?.text(): String? = when (this) {
    null, PasswordFieldError.EMPTY -> null
    PasswordFieldError.MISMATCH -> stringResource(Res.string.change_password_mismatch)
    PasswordFieldError.WEAK -> stringResource(Res.string.field_error_password)
    PasswordFieldError.SAME -> stringResource(Res.string.change_password_same)
    PasswordFieldError.WRONG -> stringResource(Res.string.change_password_wrong)
}

@Composable
private fun ChangePasswordFormProblem.text(): String = when (this) {
    ChangePasswordFormProblem.RATE_LIMITED -> stringResource(Res.string.auth_error_rate_limited)
    ChangePasswordFormProblem.CONNECTION -> stringResource(Res.string.auth_error_connection)
    ChangePasswordFormProblem.GENERIC -> stringResource(Res.string.auth_error_generic)
}

/** Empty copy differs per field. Every other error uses the same sentence on whichever box it sits. */
@Composable
private fun PasswordFieldError?.textFor(empty: String): String? = when (this) {
    PasswordFieldError.EMPTY -> empty
    else -> text()
}

@Composable
private fun currentErrorText(error: PasswordFieldError?) =
    error.textFor(stringResource(Res.string.change_password_current_empty))

@Composable
private fun newErrorText(error: PasswordFieldError?) =
    error.textFor(stringResource(Res.string.change_password_new_empty))

@Composable
private fun confirmErrorText(error: PasswordFieldError?) =
    error.textFor(stringResource(Res.string.change_password_confirm_empty))

@Preview
@Composable
private fun ChangePasswordPreview() = IrisPreview {
    ChangePasswordContent(
        state = ChangePasswordUiState(),
        onBack = {},
        onCurrent = {},
        onNew = {},
        onConfirm = {},
        onSubmit = {},
    )
}

@Preview
@Composable
private fun ChangePasswordErrorPreview() = IrisPreview {
    ChangePasswordContent(
        state = ChangePasswordUiState(
            current = "secret",
            newPassword = "secret",
            confirm = "other",
            currentError = PasswordFieldError.WRONG,
            newError = PasswordFieldError.SAME,
            confirmError = PasswordFieldError.MISMATCH,
        ),
        onBack = {},
        onCurrent = {},
        onNew = {},
        onConfirm = {},
        onSubmit = {},
    )
}
