package app.ransel.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.shared.domain.PasswordCheck
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.auth_error_connection
import ransel.composeapp.generated.resources.auth_error_email_taken
import ransel.composeapp.generated.resources.auth_error_generic
import ransel.composeapp.generated.resources.auth_error_invalid_credentials
import ransel.composeapp.generated.resources.auth_error_rate_limited
import ransel.composeapp.generated.resources.field_password
import ransel.composeapp.generated.resources.password_hide
import ransel.composeapp.generated.resources.password_rule_digit
import ransel.composeapp.generated.resources.password_rule_length
import ransel.composeapp.generated.resources.password_rule_letter
import ransel.composeapp.generated.resources.password_show
import ransel.composeapp.generated.resources.reset_token_invalid_body

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
/** Shared frame of the auth screens: optional back, 26/32 title, muted subtitle, scrolling body, footer line. */
@Composable
fun AuthScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (onBack != null) {
                Box(
                    Modifier.padding(top = 8.dp).size(48.dp).clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
                }
            } else {
                Box(Modifier.size(56.dp))
            }
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
        // Hide the footer while the keyboard is up so it does not squeeze the form.
        if (footer != null && !WindowInsets.isImeVisible) Box(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 28.dp)) { footer() }
    }
}

/** "Belum punya akun? **Daftar**" line. */
@Composable
fun AuthFooter(prefix: String, action: String, onClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(prefix, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            action,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp),
        )
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.field_password),
    error: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    IrisTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        leadingSymbol = Symbols.lock,
        error = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            Box(
                Modifier.size(40.dp).clickable(role = Role.Button) { visible = !visible },
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    if (visible) Symbols.visibilityOff else Symbols.visibility,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = stringResource(if (visible) Res.string.password_hide else Res.string.password_show),
                )
            }
        },
    )
}

/** Live checklist: 8 characters, a number, a letter. */
@Composable
fun PasswordRules(check: PasswordCheck, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RuleRow(check.minLength, stringResource(Res.string.password_rule_length))
        RuleRow(check.hasDigit, stringResource(Res.string.password_rule_digit))
        RuleRow(check.hasLetter, stringResource(Res.string.password_rule_letter))
    }
}

@Composable
private fun RuleRow(met: Boolean, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Symbol(
            if (met) Symbols.checkCircle else Symbols.radioButtonUnchecked,
            size = 18.dp,
            filled = met,
            tint = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (met) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AuthPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun AuthSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, symbol: String? = null) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        if (symbol != null) {
            Symbol(symbol, size = 20.dp)
            Box(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun AuthProblemText(problem: AuthProblem?, modifier: Modifier = Modifier) {
    if (problem == null) return
    val text = when (problem) {
        AuthProblem.INVALID_CREDENTIALS -> stringResource(Res.string.auth_error_invalid_credentials)
        AuthProblem.EMAIL_TAKEN -> stringResource(Res.string.auth_error_email_taken)
        AuthProblem.RATE_LIMITED -> stringResource(Res.string.auth_error_rate_limited)
        AuthProblem.CONNECTION -> stringResource(Res.string.auth_error_connection)
        AuthProblem.RESET_TOKEN_INVALID -> stringResource(Res.string.reset_token_invalid_body)
        AuthProblem.GENERIC -> stringResource(Res.string.auth_error_generic)
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Symbol(Symbols.error, size = 18.dp, filled = true, tint = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start)
    }
}
