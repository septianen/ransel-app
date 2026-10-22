package app.ransel.admin.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.cancel
import app.ransel.admin.generated.resources.close
import app.ransel.admin.generated.resources.create_user_body
import app.ransel.admin.generated.resources.create_user_email
import app.ransel.admin.generated.resources.create_user_email_invalid
import app.ransel.admin.generated.resources.create_user_email_taken
import app.ransel.admin.generated.resources.create_user_name
import app.ransel.admin.generated.resources.create_user_name_required
import app.ransel.admin.generated.resources.create_user_save
import app.ransel.admin.generated.resources.create_user_title
import app.ransel.admin.generated.resources.error_code
import app.ransel.admin.ui.ErrorLine
import app.ransel.admin.ui.failureText
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.isValidEmail
import app.ransel.shared.dto.AdminUserDto
import app.ransel.shared.dto.CreateUserRequest
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Field problems, from the form itself or from the server's 422 / 409. */
private enum class FieldProblem { NAME_REQUIRED, EMAIL_INVALID, EMAIL_TAKEN }

/**
 * "Tambah pengguna": name + email; the server creates the account and emails an invite to set the password
 * (docs/specs/admin-spec.md). Shown only while email is set up (`AdminMeDto.passwordResetEmailEnabled`).
 */
@Composable
fun CreateUserDialog(
    api: AdminApi,
    onAccessLost: suspend (ApiFailure) -> Unit,
    onCreated: (AdminUserDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var nameProblem by remember { mutableStateOf<FieldProblem?>(null) }
    var emailProblem by remember { mutableStateOf<FieldProblem?>(null) }
    var error by remember { mutableStateOf<ApiFailure?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun save() {
        nameProblem = if (name.isBlank()) FieldProblem.NAME_REQUIRED else null
        emailProblem = if (!isValidEmail(email.trim())) FieldProblem.EMAIL_INVALID else null
        if (nameProblem != null || emailProblem != null) return
        busy = true
        error = null
        scope.launch {
            when (val result = api.createUser(CreateUserRequest(email = email.trim(), displayName = name.trim()))) {
                is ApiResult.Success -> onCreated(result.value)
                is ApiResult.Failure -> {
                    val failure = result.error
                    when {
                        failure.code == ErrorCode.EMAIL_ALREADY_REGISTERED -> emailProblem = FieldProblem.EMAIL_TAKEN
                        failure.code == ErrorCode.VALIDATION_FAILED && failure.details != null -> {
                            if (failure.details["email"] != null) emailProblem = FieldProblem.EMAIL_INVALID
                            if (failure.details["displayName"] != null) nameProblem = FieldProblem.NAME_REQUIRED
                        }
                        else -> {
                            onAccessLost(failure)
                            error = failure
                        }
                    }
                }
            }
            busy = false
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = IrisShape.xl,
            color = scheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.create_user_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(Res.string.create_user_body), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, enabled = !busy) {
                        Symbol(Symbols.close, size = 22.dp, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.close))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80); nameProblem = null },
                        label = { Text(stringResource(Res.string.create_user_name)) },
                        singleLine = true,
                        isError = nameProblem != null,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (nameProblem != null) ErrorLine(stringResource(Res.string.create_user_name_required))
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(254); emailProblem = null },
                        label = { Text(stringResource(Res.string.create_user_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = emailProblem != null,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when (emailProblem) {
                        FieldProblem.EMAIL_TAKEN -> ErrorLine(stringResource(Res.string.create_user_email_taken))
                        FieldProblem.EMAIL_INVALID -> ErrorLine(stringResource(Res.string.create_user_email_invalid))
                        else -> Unit
                    }
                }

                error?.let { failure ->
                    ErrorLine(failureText(failure) + if (failure.isConnection) "" else " · " + stringResource(Res.string.error_code, failure.code))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Button(onClick = ::save, enabled = !busy, modifier = Modifier.heightIn(min = 44.dp)) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                            Box(Modifier.size(8.dp))
                        }
                        Text(stringResource(Res.string.create_user_save))
                    }
                }
            }
        }
    }
}
