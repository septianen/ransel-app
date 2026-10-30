package app.ransel.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.content.stringResource
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PreferencesRepository
import app.ransel.feature.expenses.ExpensesViewModel
import app.ransel.shared.domain.MAX_PAYMENT_METHODS
import app.ransel.shared.domain.validatePaymentMethod
import app.ransel.shared.dto.PaymentMethodDto
import app.ransel.shared.dto.PaymentMethodInput
import app.ransel.shared.model.PaymentMethodKind
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.paymethod_add
import ransel.composeapp.generated.resources.paymethod_bank
import ransel.composeapp.generated.resources.paymethod_delete
import ransel.composeapp.generated.resources.paymethod_ewallet
import ransel.composeapp.generated.resources.paymethod_holder
import ransel.composeapp.generated.resources.paymethod_include
import ransel.composeapp.generated.resources.paymethod_include_hint
import ransel.composeapp.generated.resources.paymethod_intro
import ransel.composeapp.generated.resources.paymethod_invalid
import ransel.composeapp.generated.resources.paymethod_number
import ransel.composeapp.generated.resources.paymethod_primary
import ransel.composeapp.generated.resources.paymethod_privacy
import ransel.composeapp.generated.resources.paymethod_provider
import ransel.composeapp.generated.resources.paymethod_save
import ransel.composeapp.generated.resources.paymethod_set_primary
import ransel.composeapp.generated.resources.paymethod_sheet_edit
import ransel.composeapp.generated.resources.paymethod_sheet_new
import ransel.composeapp.generated.resources.paymethod_title
import ransel.composeapp.generated.resources.payto_holder

data class PaymentMethodsUiState(
    val loading: Boolean = true,
    val methods: List<PaymentMethodDto> = emptyList(),
    val failure: ApiFailure? = null,
    val includeInReminders: Boolean = true,
    val saving: Boolean = false,
    /** ErrorCode of the last failed save, shown in the sheet. */
    val saveError: String? = null,
)

/** Account → Info pembayaran (server data, online only). */
class PaymentMethodsViewModel(
    private val api: RanselApi,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PaymentMethodsUiState())
    val state: StateFlow<PaymentMethodsUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            val saved = preferences.getString(ExpensesViewModel.PAY_INFO_IN_REMINDERS)
            _state.update { it.copy(includeInReminders = saved != "false") }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            when (val result = api.paymentMethods()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, methods = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = result.error) }
            }
        }
    }

    /** [id] null = a new method. Calls [onDone] after a successful save. */
    fun save(id: String?, input: PaymentMethodInput, onDone: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val result = if (id == null) api.addPaymentMethod(input) else api.updatePaymentMethod(id, input)
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false) }
                    onDone()
                    load()
                }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, saveError = result.error.code) }
            }
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (api.deletePaymentMethod(id) is ApiResult.Success) {
                onDone()
                load()
            }
        }
    }

    fun setIncludeInReminders(include: Boolean) {
        _state.update { it.copy(includeInReminders = include) }
        viewModelScope.launch { preferences.putString(ExpensesViewModel.PAY_INFO_IN_REMINDERS, include.toString()) }
    }
}

/** Design "InfoPembayaran". */
@Composable
fun PaymentMethodsScreen(onBack: () -> Unit, viewModel: PaymentMethodsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    PaymentMethodsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onAdd = { editing = EditTarget(null) },
        onEdit = { editing = EditTarget(it) },
        onIncludeChange = viewModel::setIncludeInReminders,
        modifier = Modifier.statusBarsPadding(),
    )
    editing?.let { target ->
        PaymentMethodSheet(
            existing = target.method,
            saving = state.saving,
            error = state.saveError,
            onSave = { viewModel.save(target.method?.id, it) { editing = null } },
            onDelete = target.method?.let { m -> { viewModel.delete(m.id) { editing = null } } },
            onDismiss = { editing = null },
        )
    }
}

private class EditTarget(val method: PaymentMethodDto?)

@Composable
fun PaymentMethodsContent(
    state: PaymentMethodsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PaymentMethodDto) -> Unit,
    onIncludeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back)) }
            Text(stringResource(Res.string.paymethod_title), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
        }
        val failure = state.failure
        when {
            state.loading && state.methods.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            failure != null && state.methods.isEmpty() ->
                if (failure.isConnection) ConnectionErrorState(onRetry = onRetry, errorCode = failure.code, onBack = onBack)
                else ServerErrorState(onRetry = onRetry, errorCode = failure.code, onBack = onBack)
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.paymethod_intro), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                state.methods.forEach { method ->
                    Row(
                        Modifier.fillMaxWidth().clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg)
                            .clickable(role = Role.Button) { onEdit(method) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(IrisShape.md).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                            Symbol(
                                if (method.kind == PaymentMethodKind.BANK) Symbols.accountBalance else Symbols.accountBalanceWallet,
                                filled = true, tint = scheme.onTertiaryContainer,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${method.provider} · ${method.accountNumber}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(Res.string.payto_holder) + " " + method.holderName +
                                    if (method.isPrimary) " · " + stringResource(Res.string.paymethod_primary) else "",
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                            )
                        }
                        Symbol(Symbols.edit, tint = scheme.onSurfaceVariant)
                    }
                }
                if (state.methods.size < MAX_PAYMENT_METHODS) {
                    OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Symbol(Symbols.add, size = 20.dp)
                        Text(stringResource(Res.string.paymethod_add), Modifier.padding(start = 8.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.paymethod_include), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(Res.string.paymethod_include_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Switch(checked = state.includeInReminders, onCheckedChange = onIncludeChange)
                }
                Row(
                    Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symbol(Symbols.lock, size = 18.dp, tint = scheme.onSurfaceVariant)
                    Text(stringResource(Res.string.paymethod_privacy), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Add or edit one bank account or e-wallet. */
@Composable
private fun PaymentMethodSheet(
    existing: PaymentMethodDto?,
    saving: Boolean,
    error: String?,
    onSave: (PaymentMethodInput) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(existing?.kind ?: PaymentMethodKind.BANK) }
    var provider by remember { mutableStateOf(existing?.provider.orEmpty()) }
    var number by remember { mutableStateOf(existing?.accountNumber.orEmpty()) }
    var holder by remember { mutableStateOf(existing?.holderName.orEmpty()) }
    var primary by remember { mutableStateOf(existing?.isPrimary ?: false) }
    var showErrors by remember { mutableStateOf(false) }
    val input = PaymentMethodInput(kind, provider, number, holder, primary)
    val problems = validatePaymentMethod(input)
    val invalid = stringResource(Res.string.paymethod_invalid)
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(if (existing == null) Res.string.paymethod_sheet_new else Res.string.paymethod_sheet_edit),
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip(stringResource(Res.string.paymethod_bank), kind == PaymentMethodKind.BANK) { kind = PaymentMethodKind.BANK }
                KindChip(stringResource(Res.string.paymethod_ewallet), kind == PaymentMethodKind.EWALLET) { kind = PaymentMethodKind.EWALLET }
            }
            IrisTextField(
                label = stringResource(Res.string.paymethod_provider), value = provider, onValueChange = { provider = it },
                error = if (showErrors && "provider" in problems) invalid else null,
            )
            IrisTextField(
                label = stringResource(Res.string.paymethod_number), value = number,
                onValueChange = { number = it.filter { c -> c.isDigit() || c == ' ' || c == '-' } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                error = if (showErrors && "accountNumber" in problems) invalid else null,
            )
            IrisTextField(
                label = stringResource(Res.string.paymethod_holder), value = holder, onValueChange = { holder = it },
                error = if (showErrors && "holderName" in problems) invalid else null,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.paymethod_set_primary), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = primary, onCheckedChange = { primary = it })
            }
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            LoadingButton(
                onClick = { if (problems.isEmpty()) onSave(input) else showErrors = true },
                loading = saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.paymethod_save)) }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.paymethod_delete), color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Symbol(Symbols.check, size = 18.dp, tint = scheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface)
    }
}

@Preview
@Composable
private fun PaymentMethodsContentPreview() = IrisPreview {
    val now = kotlin.time.Instant.parse("2026-10-26T08:00:00Z")
    PaymentMethodsContent(
        state = PaymentMethodsUiState(
            loading = false,
            methods = listOf(
                PaymentMethodDto("1", PaymentMethodKind.BANK, "BCA", "1234 5678 90", "Septian Nugraha", true, now, now),
                PaymentMethodDto("2", PaymentMethodKind.EWALLET, "GoPay", "0812 3456 7890", "Septian", false, now, now),
            ),
        ),
        onBack = {}, onRetry = {}, onAdd = {}, onEdit = {}, onIncludeChange = {},
    )
}
