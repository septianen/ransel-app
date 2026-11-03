package app.ransel.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.content.stringResource
import app.ransel.data.auth.AuthRepository
import app.ransel.data.credit.CreditsStore
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.feature.auth.PasswordField
import app.ransel.shared.dto.AccountDeletionPreviewDto
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.*

/** Stays on the production host: there is one policy, whichever API the build talks to. */
const val PRIVACY_POLICY_URL = "https://ransel.app/privacy.html"

enum class DeleteProblem { WRONG_PASSWORD, FAILED }

data class DeleteAccountUiState(
    /** Null until loaded, or when it could not be: the deletion still works, it only says less. */
    val preview: AccountDeletionPreviewDto? = null,
    val password: String = "",
    val deleting: Boolean = false,
    val problem: DeleteProblem? = null,
    val deleted: Boolean = false,
)

class DeleteAccountViewModel(
    private val api: RanselApi,
    private val auth: AuthRepository,
    private val credits: CreditsStore,
    private val notifications: NotificationsStore,
    private val voting: app.ransel.data.voting.VotingStore,
) : ViewModel() {
    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            (api.accountDeletionPreview() as? ApiResult.Success)?.let { r -> _state.update { it.copy(preview = r.value) } }
        }
    }

    fun setPassword(text: String) = _state.update { it.copy(password = text, problem = null) }

    fun delete() {
        val password = _state.value.password
        if (password.isEmpty() || _state.value.deleting) return
        _state.update { it.copy(deleting = true, problem = null) }
        viewModelScope.launch {
            when (val result = auth.deleteAccount(password)) {
                is ApiResult.Success -> {
                    credits.update(null)
                    notifications.clear()
                    voting.clear()
                    _state.update { it.copy(deleting = false, deleted = true) }
                }
                is ApiResult.Failure -> {
                    val wrong = result.error.details?.get("password") == "WRONG"
                    _state.update { it.copy(deleting = false, problem = if (wrong) DeleteProblem.WRONG_PASSWORD else DeleteProblem.FAILED) }
                }
            }
        }
    }
}

/** Akun → Hapus akun. No board yet, the owner reviews it: what goes, what stays, and the password. */
@Composable
fun DeleteAccountScreen(onBack: () -> Unit, viewModel: DeleteAccountViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    Column(Modifier.fillMaxSize().background(scheme.surface).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Text(stringResource(Res.string.delete_account_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.delete_account_lead), style = MaterialTheme.typography.titleMedium, color = scheme.error)
            Text(stringResource(Res.string.delete_account_gone), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.delete_account_kept), style = MaterialTheme.typography.bodyMedium)
            state.preview?.let { preview ->
                if (preview.credits > 0) Text(stringResource(Res.string.delete_account_credits, preview.credits), style = MaterialTheme.typography.bodyMedium)
                if (preview.tripsHandedOver.isNotEmpty()) Text(stringResource(Res.string.delete_account_handed, preview.tripsHandedOver.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                if (preview.tripsDeleted.isNotEmpty()) Text(stringResource(Res.string.delete_account_deleted, preview.tripsDeleted.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(Res.string.delete_account_password), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            PasswordField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                error = when (state.problem) {
                    DeleteProblem.WRONG_PASSWORD -> stringResource(Res.string.delete_account_wrong)
                    DeleteProblem.FAILED -> stringResource(Res.string.delete_account_failed)
                    null -> null
                },
            )
        }
        LoadingButton(
            onClick = viewModel::delete,
            loading = state.deleting,
            enabled = state.password.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.delete_account_action)) }
    }
}
