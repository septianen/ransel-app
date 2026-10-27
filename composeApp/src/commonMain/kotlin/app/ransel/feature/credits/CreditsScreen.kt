package app.ransel.feature.credits

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.ransel.data.remote.ApiFailure
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.CreditCostsDto
import app.ransel.shared.dto.CreditLedgerEntryDto
import app.ransel.shared.dto.CreditPackageDto
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.CreditLedgerKind
import app.ransel.shared.model.CreditSource
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.*

/**
 * Explore credits: balance, the free daily allowance, redeem a voucher, the packs, and the history.
 * No design board exists yet; built from the Account / Members patterns (ask the owner before polishing the layout).
 */
@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreditsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    CreditsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onCodeChange = viewModel::onCodeChange,
        onRedeem = viewModel::redeem,
        modifier = modifier.statusBarsPadding(),
    )
}

@Composable
fun CreditsContent(
    state: CreditsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopBar(onBack)
        val credits = state.credits
        when {
            state.loading && credits == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.failure != null && credits == null -> LoadFailure(state.failure, onRetry, onBack)
            credits != null -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BalanceCard(credits)
                VoucherSection(state, onCodeChange, onRedeem)
                state.packages?.takeIf { it.packages.isNotEmpty() }?.let { PackagesSection(it.packages, it.purchasable, credits.costs) }
                HistorySection(state.ledger)
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = SemanticsRole.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back)) }
        Text(stringResource(Res.string.credits_title), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun LoadFailure(failure: ApiFailure, onRetry: () -> Unit, onBack: () -> Unit) {
    if (failure.isConnection) ConnectionErrorState(onRetry = onRetry, errorCode = failure.code, onBack = onBack)
    else ServerErrorState(onRetry = onRetry, errorCode = failure.code, onBack = onBack)
}

@Composable
private fun BalanceCard(credits: CreditsDto) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.xl).background(scheme.tertiaryContainer).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Symbol(Symbols.autoAwesome, filled = true, tint = scheme.onTertiaryContainer)
            Text(stringResource(Res.string.credits_balance_label), style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(credits.balance.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = scheme.onTertiaryContainer)
            Text(stringResource(Res.string.credits_unit), Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer)
        }
        Text(
            stringResource(Res.string.credits_free_today, credits.dailyFreeLeft, credits.dailyFreeLimit),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onTertiaryContainer,
        )
        Text(
            stringResource(Res.string.credits_costs, credits.costs.search, credits.costs.nextBatch) +
                if (credits.costs.receipt > 0) " · " + stringResource(Res.string.credits_cost_receipt, credits.costs.receipt) else "",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun VoucherSection(state: CreditsUiState, onCodeChange: (String) -> Unit, onRedeem: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.credits_voucher_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IrisTextField(
            label = stringResource(Res.string.credits_voucher_label),
            value = state.code,
            onValueChange = onCodeChange,
            placeholder = stringResource(Res.string.credits_voucher_placeholder),
            leadingSymbol = Symbols.sell,
            error = state.redeemError?.let { voucherError(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        state.redeemed?.let {
            Text(stringResource(Res.string.credits_voucher_success, it), style = MaterialTheme.typography.bodyMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        }
        LoadingButton(
            onClick = onRedeem,
            loading = state.redeeming,
            enabled = state.code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.credits_voucher_apply), style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun voucherError(code: String): String = stringResource(
    when (code) {
        ErrorCode.VOUCHER_INVALID -> Res.string.credits_voucher_invalid
        ErrorCode.VOUCHER_EXPIRED -> Res.string.credits_voucher_expired
        ErrorCode.VOUCHER_USED -> Res.string.credits_voucher_used
        ErrorCode.NETWORK_TIMEOUT, ErrorCode.NETWORK_UNAVAILABLE -> Res.string.credits_error_connection
        ErrorCode.RATE_LIMITED -> Res.string.credits_voucher_rate_limited
        else -> Res.string.credits_error_generic
    },
)

@Composable
private fun PackagesSection(packages: List<CreditPackageDto>, purchasable: Boolean, costs: CreditCostsDto) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.credits_packages_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        packages.forEach { pack ->
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.credits_package_credits, pack.credits), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (costs.search > 0) {
                        // Price of one new search in this pack.
                        val perSearch = pack.priceMinor * costs.search / pack.credits
                        Text(
                            stringResource(Res.string.credits_package_per_search, formatMoney(perSearch, pack.currency)),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Text(formatMoney(pack.priceMinor, pack.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (!purchasable) {
            Text(stringResource(Res.string.credits_packages_soon), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistorySection(ledger: List<CreditLedgerEntryDto>) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.credits_history_title), Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (ledger.isEmpty()) {
            Text(stringResource(Res.string.credits_history_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        ledger.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = scheme.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(entryTitle(entry), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        dates.dayMonth(entry.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (entry.delta > 0) "+${entry.delta}" else entry.delta.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.delta > 0) scheme.primary else scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun entryTitle(entry: CreditLedgerEntryDto): String {
    val base = stringResource(
        when (entry.kind) {
            CreditLedgerKind.SIGNUP_GRANT -> Res.string.credits_kind_signup
            CreditLedgerKind.VOUCHER -> Res.string.credits_kind_voucher
            CreditLedgerKind.TOPUP -> Res.string.credits_kind_topup
            CreditLedgerKind.REFUND -> Res.string.credits_kind_refund
            CreditLedgerKind.ADJUSTMENT -> Res.string.credits_kind_adjustment
            CreditLedgerKind.SPEND -> when (entry.action) {
                CreditAction.NEXT_BATCH -> Res.string.credits_action_next_batch
                CreditAction.PLACE_DETAIL -> Res.string.credits_action_place_detail
                CreditAction.RECEIPT -> Res.string.credits_action_receipt
                CreditAction.SEARCH, null -> Res.string.credits_action_search
            }
        },
    )
    return if (entry.source == CreditSource.FREE_DAILY) "$base · ${stringResource(Res.string.credits_source_free)}" else base
}
