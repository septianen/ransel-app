package app.ransel.admin.feature.vouchers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.feature.users.Pager
import app.ransel.admin.generated.resources.*
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.ConfirmDialog
import app.ransel.admin.ui.ErrorLine
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.Pill
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.shared.dto.AdminVoucherDto
import app.ransel.shared.model.VoucherStatus
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Promo vouchers. No design board yet: built from the Users patterns, to be reviewed by the owner. */
@Composable
fun VouchersScreen(api: AdminApi, snackbar: SnackbarHostState, onAccessLost: suspend (ApiFailure) -> Unit) {
    val presenter = rememberPresenter { VouchersPresenter(api, it, onAccessLost) }
    val state by presenter.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val copy: (String) -> Unit = { code ->
        clipboard.setText(AnnotatedString(code))
        scope.launch { snackbar.showSnackbar(getString(Res.string.vouchers_copied, code)) }
    }
    LaunchedEffect(state.revoked) {
        state.revoked?.let { code ->
            presenter.revokedShown()
            snackbar.showSnackbar(getString(Res.string.vouchers_revoked, code))
        }
    }

    VouchersContent(state, onAdd = presenter::openForm, onCopy = copy, onRevoke = presenter::askRevoke, onPrevious = presenter::previousPage, onNext = presenter::nextPage, onRetry = presenter::retry)

    state.form?.let { VoucherFormDialog(it, presenter::editForm, presenter::create, presenter::closeForm) }
    state.created?.let { CreatedDialog(it, copy, presenter::closeCreated) }
    state.revoking?.let { voucher ->
        ConfirmDialog(
            title = stringResource(Res.string.vouchers_revoke_title, voucher.code),
            body = stringResource(Res.string.vouchers_revoke_body),
            confirmLabel = stringResource(Res.string.vouchers_revoke),
            destructive = true,
            busy = state.revokeBusy,
            error = state.revokeError?.let { failureText(it) },
            onConfirm = presenter::revoke,
            onDismiss = presenter::cancelRevoke,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VouchersContent(
    state: VouchersUiState,
    onAdd: () -> Unit,
    onCopy: (String) -> Unit,
    onRevoke: (AdminVoucherDto) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f).widthIn(min = 240.dp)) {
                Text(stringResource(Res.string.vouchers_title), style = MaterialTheme.typography.displaySmall)
                Text(stringResource(Res.string.vouchers_subtitle), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Button(onClick = onAdd, modifier = Modifier.heightIn(min = 44.dp)) {
                Symbol(Symbols.add, size = 20.dp, tint = LocalContentColor.current)
                Text(stringResource(Res.string.vouchers_add), Modifier.padding(start = 8.dp))
            }
        }
        val result = state.result
        when {
            state.error != null && result == null -> ErrorPanel(state.error, onRetry)
            result == null -> LoadingPanel()
            result.items.isEmpty() -> AdminCard(Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.vouchers_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            else -> AdminCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                Column {
                    result.items.forEachIndexed { index, voucher ->
                        if (index > 0) HorizontalDivider(color = scheme.outlineVariant)
                        VoucherRow(voucher, onCopy, onRevoke)
                    }
                    Pager(result, state.loading, onPrevious, onNext)
                }
            }
        }
    }
}

@Composable
private fun VoucherRow(voucher: AdminVoucherDto, onCopy: (String) -> Unit, onRevoke: (AdminVoucherDto) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(voucher.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                VoucherStatusPill(voucher.status)
            }
            Text(
                stringResource(Res.string.vouchers_summary, format.number(voucher.credits), format.number(voucher.usedCount), format.number(voucher.maxUses)),
                style = MaterialTheme.typography.bodyMedium,
            )
            voucher.expiresAt?.let {
                Text(stringResource(Res.string.vouchers_valid_until, format.date(it)), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            voucher.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
        IconButton(onClick = { onCopy(voucher.code) }, modifier = Modifier.size(44.dp)) {
            Symbol(Symbols.contentCopy, size = 20.dp, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.vouchers_copy))
        }
        if (voucher.status != VoucherStatus.REVOKED) {
            TextButton(onClick = { onRevoke(voucher) }, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(Res.string.vouchers_revoke), color = scheme.error)
            }
        }
    }
}

@Composable
private fun VoucherStatusPill(status: VoucherStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        VoucherStatus.ACTIVE -> Pill(stringResource(Res.string.vouchers_status_active), scheme.primaryContainer, scheme.onPrimaryContainer)
        VoucherStatus.USED_UP -> Pill(stringResource(Res.string.vouchers_status_used_up), scheme.surfaceContainer, scheme.onSurfaceVariant)
        VoucherStatus.EXPIRED -> Pill(stringResource(Res.string.vouchers_status_expired), scheme.surfaceContainer, scheme.onSurfaceVariant)
        VoucherStatus.REVOKED -> Pill(stringResource(Res.string.vouchers_status_revoked), scheme.errorContainer, scheme.onErrorContainer)
    }
}

@Composable
private fun DialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = IrisShape.xl,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, error: String?, enabled: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { text -> onChange(text.filter(Char::isDigit).take(6)) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = error != null,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { ErrorLine(it) }
    }
}

@Composable
private fun VoucherFormDialog(form: VoucherForm, onEdit: ((VoucherForm) -> VoucherForm) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DialogFrame(onDismiss = { if (!form.busy) onDismiss() }) {
        Text(stringResource(Res.string.vouchers_add), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        NumberField(
            stringResource(Res.string.voucher_form_credits), form.credits,
            stringResource(Res.string.voucher_form_credits_error).takeIf { form.showErrors && !form.creditsValid }, !form.busy,
        ) { v -> onEdit { it.copy(credits = v) } }
        NumberField(
            stringResource(Res.string.voucher_form_uses), form.maxUses,
            stringResource(Res.string.voucher_form_uses_error).takeIf { form.showErrors && !form.maxUsesValid }, !form.busy,
        ) { v -> onEdit { it.copy(maxUses = v) } }
        NumberField(
            stringResource(Res.string.voucher_form_days), form.validDays,
            stringResource(Res.string.voucher_form_days_error).takeIf { form.showErrors && !form.validDaysValid }, !form.busy,
        ) { v -> onEdit { it.copy(validDays = v) } }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> onEdit { it.copy(note = v.take(200)) } },
                label = { Text(stringResource(Res.string.voucher_form_note)) },
                singleLine = true,
                enabled = !form.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(Res.string.voucher_form_note_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        form.error?.let { failure ->
            ErrorLine(failureText(failure) + if (failure.isConnection) "" else " · " + stringResource(Res.string.error_code, failure.code))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss, enabled = !form.busy, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(Res.string.cancel)) }
            Button(onClick = onSave, enabled = !form.busy, modifier = Modifier.heightIn(min = 44.dp)) {
                if (form.busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                Text(stringResource(Res.string.voucher_form_save))
            }
        }
    }
}

@Composable
private fun CreatedDialog(voucher: AdminVoucherDto, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    val format = LocalAdminFormat.current
    DialogFrame(onDismiss = onDismiss) {
        Text(stringResource(Res.string.voucher_created_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(voucher.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.vouchers_summary, format.number(voucher.credits), format.number(voucher.usedCount), format.number(voucher.maxUses)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.voucher_created_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(Res.string.close)) }
            Button(onClick = { onCopy(voucher.code) }, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(Res.string.vouchers_copy)) }
        }
    }
}
