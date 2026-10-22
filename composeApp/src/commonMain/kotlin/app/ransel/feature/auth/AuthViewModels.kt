package app.ransel.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.AuthRepository
import app.ransel.data.remote.ApiResult
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.PasswordCheck
import app.ransel.shared.domain.checkPassword
import app.ransel.shared.domain.isValidEmail
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What can be wrong on an auth form; the screen maps it to copy (the app switches on codes, not messages). */
enum class AuthProblem { INVALID_CREDENTIALS, ACCOUNT_DISABLED, EMAIL_TAKEN, RATE_LIMITED, CONNECTION, RESET_TOKEN_INVALID, REGISTRATION_CLOSED, GENERIC }

private fun problemOf(code: String): AuthProblem = when (code) {
    ErrorCode.INVALID_CREDENTIALS -> AuthProblem.INVALID_CREDENTIALS
    ErrorCode.ACCOUNT_DISABLED -> AuthProblem.ACCOUNT_DISABLED
    ErrorCode.EMAIL_ALREADY_REGISTERED -> AuthProblem.EMAIL_TAKEN
    ErrorCode.RATE_LIMITED -> AuthProblem.RATE_LIMITED
    ErrorCode.NETWORK_TIMEOUT, ErrorCode.NETWORK_UNAVAILABLE -> AuthProblem.CONNECTION
    ErrorCode.RESET_TOKEN_INVALID -> AuthProblem.RESET_TOKEN_INVALID
    else -> AuthProblem.GENERIC
}

/** The server answers 403 FORBIDDEN on register while sign-up is closed (`REGISTRATION_ENABLED=false`). */
private fun signUpProblemOf(code: String): AuthProblem =
    if (code == ErrorCode.FORBIDDEN) AuthProblem.REGISTRATION_CLOSED else problemOf(code)

// ── sign in ─────────────────────────────────────────────────────────────────

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val problem: AuthProblem? = null,
    val loading: Boolean = false,
)

class SignInViewModel(private val auth: AuthRepository) : ViewModel() {
    // Arrived here because an admin deactivated the account while it was signed in: say so once.
    private val _state = MutableStateFlow(
        SignInUiState(problem = if (auth.takeAccountDisabled()) AuthProblem.ACCOUNT_DISABLED else null),
    )
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun setEmail(value: String) = _state.update { it.copy(email = value, emailError = false, problem = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, passwordError = false, problem = null) }

    /** On success the session flips to signed-in and the app swaps the screen; nothing to navigate here. */
    fun submit() {
        val current = _state.value
        if (current.loading) return
        val emailError = !isValidEmail(current.email)
        val passwordError = current.password.isEmpty()
        if (emailError || passwordError) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(loading = true, problem = null) }
        viewModelScope.launch {
            when (val result = auth.signIn(current.email, current.password)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, problem = problemOf(result.error.code)) }
            }
        }
    }
}

// ── sign up ─────────────────────────────────────────────────────────────────

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val acceptedTerms: Boolean = false,
    val nameError: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val termsError: Boolean = false,
    val problem: AuthProblem? = null,
    val loading: Boolean = false,
) {
    val passwordCheck: PasswordCheck get() = checkPassword(password)
}

class SignUpViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    fun setName(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun setEmail(value: String) = _state.update { it.copy(email = value, emailError = false, problem = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, passwordError = false) }
    fun setAcceptedTerms(value: Boolean) = _state.update { it.copy(acceptedTerms = value, termsError = false) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val check = current.passwordCheck
        val nameError = current.name.isBlank()
        val emailError = !isValidEmail(current.email)
        val passwordError = !check.isValid
        val termsError = !current.acceptedTerms
        if (nameError || emailError || passwordError || termsError) {
            _state.update {
                it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError, termsError = termsError)
            }
            return
        }
        _state.update { it.copy(loading = true, problem = null) }
        viewModelScope.launch {
            when (val result = auth.signUp(current.name, current.email, current.password)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, problem = signUpProblemOf(result.error.code)) }
            }
        }
    }
}

// ── forgot password → check email ───────────────────────────────────────────

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: Boolean = false,
    val problem: AuthProblem? = null,
    val loading: Boolean = false,
)

class ForgotPasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    /** Emits the email once the link was requested; the screen navigates to "check your email". */
    private val _sent = Channel<String>(Channel.BUFFERED)
    val sent = _sent.receiveAsFlow()

    fun setEmail(value: String) = _state.update { it.copy(email = value, emailError = false, problem = null) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        if (!isValidEmail(current.email)) {
            _state.update { it.copy(emailError = true) }
            return
        }
        _state.update { it.copy(loading = true, problem = null) }
        viewModelScope.launch {
            when (val result = auth.forgotPassword(current.email)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false) }
                    _sent.send(current.email.trim())
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, problem = problemOf(result.error.code)) }
            }
        }
    }
}

data class CheckEmailUiState(val email: String, val resending: Boolean = false, val problem: AuthProblem? = null)

class CheckEmailViewModel(email: String, private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(CheckEmailUiState(email))
    val state: StateFlow<CheckEmailUiState> = _state.asStateFlow()

    private val _resent = Channel<Unit>(Channel.BUFFERED)
    /** Fires after each successful resend so the screen restarts its countdown. */
    val resent = _resent.receiveAsFlow()

    fun resend() {
        if (_state.value.resending) return
        _state.update { it.copy(resending = true, problem = null) }
        viewModelScope.launch {
            when (val result = auth.forgotPassword(_state.value.email)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(resending = false) }
                    _resent.send(Unit)
                }
                is ApiResult.Failure -> _state.update { it.copy(resending = false, problem = problemOf(result.error.code)) }
            }
        }
    }
}

// ── new password ────────────────────────────────────────────────────────────

data class NewPasswordUiState(
    val password: String = "",
    val repeat: String = "",
    val passwordError: Boolean = false,
    val mismatch: Boolean = false,
    val problem: AuthProblem? = null,
    val loading: Boolean = false,
    val done: Boolean = false,
) {
    val passwordCheck: PasswordCheck get() = checkPassword(password)
}

class NewPasswordViewModel(private val token: String, private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(NewPasswordUiState())
    val state: StateFlow<NewPasswordUiState> = _state.asStateFlow()

    fun setPassword(value: String) = _state.update { it.copy(password = value, passwordError = false, mismatch = false) }
    fun setRepeat(value: String) = _state.update { it.copy(repeat = value, mismatch = false) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val check = current.passwordCheck
        val passwordError = !check.isValid
        val mismatch = current.password != current.repeat
        if (passwordError || mismatch) {
            _state.update { it.copy(passwordError = passwordError, mismatch = mismatch) }
            return
        }
        _state.update { it.copy(loading = true, problem = null) }
        viewModelScope.launch {
            when (val result = auth.resetPassword(token, current.password)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, done = true) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, problem = problemOf(result.error.code)) }
            }
        }
    }
}
