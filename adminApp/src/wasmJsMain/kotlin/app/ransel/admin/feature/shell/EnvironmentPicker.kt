package app.ransel.admin.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.ApiEnvironment
import app.ransel.admin.data.EnvironmentChoice
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.env_development
import app.ransel.admin.generated.resources.env_mismatch
import app.ransel.admin.generated.resources.env_picker_label
import app.ransel.admin.generated.resources.env_production
import app.ransel.admin.generated.resources.env_staging
import app.ransel.admin.generated.resources.env_strip
import app.ransel.admin.generated.resources.env_switch_body
import app.ransel.admin.generated.resources.env_switch_confirm
import app.ransel.admin.generated.resources.env_switch_menu
import app.ransel.admin.generated.resources.env_switch_title
import app.ransel.admin.ui.ConfirmDialog
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun environmentLabel(id: String): String = when (id.lowercase()) {
    ApiEnvironment.PRODUCTION -> stringResource(Res.string.env_production)
    ApiEnvironment.STAGING -> stringResource(Res.string.env_staging)
    ApiEnvironment.DEVELOPMENT -> stringResource(Res.string.env_development)
    else -> id
}

private fun ApiEnvironment.host() = origin.substringAfter("://").trimEnd('/')

/** Sign-in screen: which server to sign in to. Picking one reloads Karung on that server (no session yet). */
@Composable
fun EnvironmentPicker(choice: EnvironmentChoice, modifier: Modifier = Modifier) {
    if (!choice.canSwitch) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.env_picker_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choice.all.forEachIndexed { index, environment ->
                SegmentedButton(
                    selected = environment == choice.current,
                    onClick = { if (environment != choice.current) choice.switchTo(environment) },
                    shape = SegmentedButtonDefaults.itemShape(index, choice.all.size),
                ) { Text(environmentLabel(environment.id)) }
            }
        }
    }
}

/**
 * The environment badge (from `GET /admin/me`, so it shows what the server says), clickable when Karung knows more than
 * one server: a menu of the others, then a confirmation, because switching signs out ([onSwitch] signs out and reloads).
 */
@Composable
fun EnvironmentMenuBadge(serverEnvironment: String, choice: EnvironmentChoice, onSwitch: (ApiEnvironment) -> Unit) {
    if (!choice.canSwitch) {
        EnvironmentBadge(serverEnvironment)
        return
    }
    var open by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<ApiEnvironment?>(null) }
    var busy by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .clickable(role = Role.DropdownList, onClickLabel = stringResource(Res.string.env_switch_menu)) { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EnvironmentBadge(serverEnvironment)
            Symbol(Symbols.arrowDropDown, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choice.all.forEach { environment ->
                DropdownMenuItem(
                    text = { Text(environmentLabel(environment.id)) },
                    leadingIcon = {
                        if (environment == choice.current) Symbol(Symbols.check, size = 20.dp)
                    },
                    onClick = {
                        open = false
                        if (environment != choice.current) target = environment
                    },
                )
            }
        }
    }
    target?.let { next ->
        val to = environmentLabel(next.id)
        ConfirmDialog(
            title = stringResource(Res.string.env_switch_title, to),
            body = stringResource(Res.string.env_switch_body, environmentLabel(choice.current.id), to),
            confirmLabel = stringResource(Res.string.env_switch_confirm),
            destructive = false,
            busy = busy,
            error = null,
            onConfirm = {
                busy = true
                onSwitch(next)
            },
            onDismiss = { target = null },
        )
    }
}

/**
 * Amber strip over the page while Karung talks to a server that is not production, or red when the server reports a
 * different environment than the one picked ([serverEnvironment] is null before sign-in).
 */
@Composable
fun EnvironmentStrip(current: ApiEnvironment, serverEnvironment: String?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val mismatch = serverEnvironment != null && !serverEnvironment.equals(current.id, ignoreCase = true)
    if (current.isProduction && !mismatch) return
    val (container, content) = if (mismatch) {
        scheme.errorContainer to scheme.onErrorContainer
    } else {
        IrisTheme.accents.yellow.copy(alpha = 0.22f) to scheme.onSurface
    }
    Row(
        modifier.fillMaxWidth().background(container).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Symbol(if (mismatch) Symbols.error else Symbols.info, size = 18.dp, tint = content)
        Text(
            if (mismatch) {
                stringResource(Res.string.env_mismatch, environmentLabel(serverEnvironment!!), environmentLabel(current.id))
            } else {
                stringResource(Res.string.env_strip, environmentLabel(current.id).uppercase(), current.host())
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}
