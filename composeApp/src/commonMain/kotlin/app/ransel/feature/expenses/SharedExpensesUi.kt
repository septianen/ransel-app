package app.ransel.feature.expenses

import app.ransel.ui.components.SectionHeader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.feature.currency.currencySymbol
import app.ransel.feature.tripform.ThousandsVisualTransformation
import app.ransel.shared.domain.currencyDecimals
import app.ransel.shared.dto.MemberBalanceDto
import app.ransel.shared.dto.TransferDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.LoadingOutlinedButton
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.sanitizeAmountInput
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.shared_balance_collect_short
import ransel.composeapp.generated.resources.shared_balance_even_short
import ransel.composeapp.generated.resources.shared_balance_owe_short
import ransel.composeapp.generated.resources.shared_per_person
import ransel.composeapp.generated.resources.shared_people
import ransel.composeapp.generated.resources.shared_balances_empty
import ransel.composeapp.generated.resources.shared_balances_title
import ransel.composeapp.generated.resources.shared_balances_total
import ransel.composeapp.generated.resources.shared_mark_settled
import ransel.composeapp.generated.resources.shared_paid_by
import ransel.composeapp.generated.resources.shared_paid_by_hint
import ransel.composeapp.generated.resources.shared_payers_custom_hint
import ransel.composeapp.generated.resources.shared_payers_title
import ransel.composeapp.generated.resources.shared_pays_to
import ransel.composeapp.generated.resources.shared_settle_hint
import ransel.composeapp.generated.resources.shared_settle_title
import ransel.composeapp.generated.resources.shared_settled
import ransel.composeapp.generated.resources.shared_share_not_in
import ransel.composeapp.generated.resources.shared_split_custom
import ransel.composeapp.generated.resources.shared_split_custom_hint
import ransel.composeapp.generated.resources.shared_split_equal
import ransel.composeapp.generated.resources.shared_split_over
import ransel.composeapp.generated.resources.shared_split_short
import ransel.composeapp.generated.resources.shared_split_title
import ransel.composeapp.generated.resources.shared_tab_balances
import ransel.composeapp.generated.resources.shared_tab_summary
import ransel.composeapp.generated.resources.shared_you
import ransel.composeapp.generated.resources.shared_you_even
import kotlin.math.abs

private fun List<TripMemberDto>.nameOf(memberId: String, myMemberId: String?, you: String): String {
    val name = firstOrNull { it.memberId == memberId }?.displayName ?: "?"
    return if (memberId == myMemberId) "$name ($you)" else name
}

// ── Summary / Balances switch ───────────────────────────────────────────────

enum class SharedTab { SUMMARY, BALANCES }

/** The switch at the top of the shared Expenses tab ("Ringkasan | Saldo"). */
@Composable
fun SharedTabSwitch(selected: SharedTab, onSelect: (SharedTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SharedTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val scheme = MaterialTheme.colorScheme
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) scheme.primaryContainer else scheme.surface)
                    .border(1.dp, if (isSelected) scheme.primary else scheme.outlineVariant, CircleShape)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isSelected) Symbol(Symbols.check, size = 18.dp, tint = scheme.onPrimaryContainer)
                Text(
                    stringResource(
                        when (tab) {
                            SharedTab.SUMMARY -> Res.string.shared_tab_summary
                            SharedTab.BALANCES -> Res.string.shared_tab_balances
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
                )
            }
        }
    }
}

/** Card colours for my balance on the balances tab: green when money comes back, red when I owe, neutral when even. */
@Composable
internal fun balanceColors(balanceMinor: Long): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val scheme = MaterialTheme.colorScheme
    return when {
        balanceMinor > 0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        balanceMinor < 0 -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurface
    }
}

// ── Balances tab ────────────────────────────────────────────────────────────

@Composable
fun BalancesContent(
    state: BalancesUiState,
    members: List<TripMemberDto>,
    myMemberId: String?,
    onRetry: () -> Unit,
    onSettle: (TransferDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.balances
    val failure = state.failure
    val you = stringResource(Res.string.shared_you)
    when {
        data == null && state.loading -> Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        data == null && failure != null ->
            if (failure.isConnection) ConnectionErrorState(onRetry = onRetry, errorCode = failure.code, modifier = modifier)
            else ServerErrorState(onRetry = onRetry, errorCode = failure.code, modifier = modifier)
        data == null -> Unit
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionHeader(stringResource(Res.string.shared_balances_title))
                    Text(
                        stringResource(Res.string.shared_balances_total, formatMoney(data.members.sumOf { it.paidMinor }, data.baseCurrency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                data.members.forEach { b ->
                    BalanceRow(b, members, myMemberId, you, data.baseCurrency)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(Res.string.shared_settle_title))
                Text(
                    stringResource(Res.string.shared_settle_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (data.transfers.isEmpty() && state.settledNow.isEmpty()) {
                    Text(stringResource(Res.string.shared_balances_empty), style = MaterialTheme.typography.bodyLarge)
                }
                data.transfers.forEach { t ->
                    TransferRow(t, members, myMemberId, you, data.baseCurrency, settled = false, enabled = !state.settling) { onSettle(t) }
                }
                state.settledNow.forEach { t ->
                    TransferRow(t, members, myMemberId, you, data.baseCurrency, settled = true, enabled = false) {}
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(
    b: MemberBalanceDto,
    members: List<TripMemberDto>,
    myMemberId: String?,
    you: String,
    currency: String,
) {
    val scheme = MaterialTheme.colorScheme
    val name = members.nameOf(b.memberId, myMemberId, you)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MemberAvatar(b.displayName, b.memberId, size = 40.dp)
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            val amount = when {
                b.balanceMinor > 0 -> "+" + formatMoney(b.balanceMinor, currency)
                b.balanceMinor < 0 -> "−" + formatMoney(-b.balanceMinor, currency)
                else -> formatMoney(0, currency)
            }
            Text(
                amount,
                style = MaterialTheme.typography.titleSmall,
                color = if (b.balanceMinor > 0) scheme.primary else if (b.balanceMinor < 0) scheme.error else scheme.onSurface,
            )
            Text(
                stringResource(
                    when {
                        b.balanceMinor > 0 -> Res.string.shared_balance_collect_short
                        b.balanceMinor < 0 -> Res.string.shared_balance_owe_short
                        else -> Res.string.shared_balance_even_short
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransferRow(
    t: TransferDto,
    members: List<TripMemberDto>,
    myMemberId: String?,
    you: String,
    currency: String,
    settled: Boolean,
    enabled: Boolean,
    onSettle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val from = members.firstOrNull { it.memberId == t.fromMemberId }
    val to = members.firstOrNull { it.memberId == t.toMemberId }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MemberAvatar(from?.displayName ?: "?", t.fromMemberId, size = 32.dp)
            Symbol(Symbols.arrowForward, size = 18.dp, tint = scheme.onSurfaceVariant)
            MemberAvatar(to?.displayName ?: "?", t.toMemberId, size = 32.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    Res.string.shared_pays_to,
                    members.nameOf(t.fromMemberId, myMemberId, you),
                    members.nameOf(t.toMemberId, myMemberId, you),
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            Text(formatMoney(t.amountMinor, currency), style = MaterialTheme.typography.titleSmall)
        }
        if (settled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Symbol(Symbols.checkCircle, size = 20.dp, filled = true, tint = scheme.primary)
                Text(stringResource(Res.string.shared_settled), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
        } else {
            LoadingOutlinedButton(onClick = onSettle, loading = !enabled) {
                Text(stringResource(Res.string.shared_mark_settled), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ── Paid by / split section of the expense sheet ────────────────────────────

@Composable
fun SplitSection(
    split: SplitState,
    amountMinor: Long?,
    currency: String,
    myMemberId: String?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val you = stringResource(Res.string.shared_you)
    val shares = split.shares(amountMinor, currency)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.shared_paid_by), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(Res.string.shared_paid_by_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                split.members.forEach { m ->
                    val selected = m.memberId in split.payers
                    Row(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .background(if (selected) scheme.primaryContainer else scheme.surface)
                            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant), CircleShape)
                            .clickable(role = Role.Checkbox) { split.togglePayer(m.memberId) }
                            .padding(start = 8.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MemberAvatar(m.displayName, m.memberId, size = 32.dp)
                        Text(
                            split.members.nameOf(m.memberId, myMemberId, you),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                            maxLines = 1,
                        )
                        if (selected) Symbol(Symbols.check, size = 18.dp, tint = scheme.onPrimaryContainer)
                    }
                }
            }
            if (split.payers.size > 1) PayerAmounts(split, amountMinor, currency, myMemberId)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.shared_split_title), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SplitModeChip(stringResource(Res.string.shared_split_equal), selected = !split.custom) { split.useEqual() }
                SplitModeChip(stringResource(Res.string.shared_split_custom), selected = split.custom) {
                    split.useCustom(amountMinor, currency)
                }
            }
            split.members.forEach { m ->
                val ticked = m.memberId in split.participants
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(IrisShape.md)
                            .clickable(role = Role.Checkbox) { split.toggle(m.memberId) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(IrisShape.xs)
                                .background(if (ticked) scheme.primary else scheme.surface)
                                .border(2.dp, if (ticked) scheme.primary else scheme.outline, IrisShape.xs),
                            contentAlignment = Alignment.Center,
                        ) { if (ticked) Symbol(Symbols.check, size = 18.dp, tint = scheme.onPrimary) }
                        MemberAvatar(m.displayName, m.memberId, size = 32.dp)
                        Text(
                            split.members.nameOf(m.memberId, myMemberId, you),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    when {
                        !ticked -> Text(
                            stringResource(Res.string.shared_share_not_in),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        split.custom -> OutlinedTextField(
                            value = split.shareTexts[m.memberId].orEmpty(),
                            onValueChange = { split.shareTexts[m.memberId] = sanitizeAmountInput(it, currencyDecimals(currency)) },
                            modifier = Modifier.width(150.dp),
                            singleLine = true,
                            prefix = { Text(currencySymbol(currency)) },
                            visualTransformation = ThousandsVisualTransformation,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (currencyDecimals(currency) == 0) KeyboardType.Number else KeyboardType.Decimal,
                            ),
                            shape = IrisShape.md,
                        )
                        else -> Text(
                            formatMoney(shares[m.memberId] ?: 0, currency),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            SplitSummary(split, amountMinor, currency, shares)
        }
    }
}

/** Two or more people paid: how much each paid (equal, or "Atur nominal"), with the same check as the split. */
@Composable
private fun PayerAmounts(split: SplitState, amountMinor: Long?, currency: String, myMemberId: String?) {
    val scheme = MaterialTheme.colorScheme
    val you = stringResource(Res.string.shared_you)
    val amounts = split.payerAmounts(amountMinor, currency)
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.shared_payers_title), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitModeChip(stringResource(Res.string.shared_split_equal), selected = !split.payersCustom) { split.usePayersEqual() }
            SplitModeChip(stringResource(Res.string.shared_split_custom), selected = split.payersCustom) {
                split.usePayersCustom(amountMinor, currency)
            }
        }
        split.payers.forEach { id ->
            val m = split.members.firstOrNull { it.memberId == id } ?: return@forEach
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MemberAvatar(m.displayName, m.memberId, size = 32.dp)
                Text(
                    split.members.nameOf(m.memberId, myMemberId, you),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (split.payersCustom) {
                    OutlinedTextField(
                        value = split.payerTexts[id].orEmpty(),
                        onValueChange = { split.payerTexts[id] = sanitizeAmountInput(it, currencyDecimals(currency)) },
                        modifier = Modifier.width(150.dp),
                        singleLine = true,
                        prefix = { Text(currencySymbol(currency)) },
                        visualTransformation = ThousandsVisualTransformation,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (currencyDecimals(currency) == 0) KeyboardType.Number else KeyboardType.Decimal,
                        ),
                        shape = IrisShape.md,
                    )
                } else {
                    Text(formatMoney(amounts[id] ?: 0, currency), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        val diff = if (amountMinor != null && amountMinor > 0) split.payerDifference(amountMinor, currency) else 0L
        if (diff != 0L) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        if (diff > 0) Res.string.shared_split_short else Res.string.shared_split_over,
                        formatMoney(abs(diff), currency),
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = scheme.error,
                )
                Text(
                    stringResource(Res.string.shared_payers_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SplitModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun SplitSummary(split: SplitState, amountMinor: Long?, currency: String, shares: Map<String, Long>) {
    val scheme = MaterialTheme.colorScheme
    if (amountMinor == null || amountMinor <= 0) return
    val diff = split.difference(amountMinor, currency)
    if (diff == 0L) {
        val people = split.participants.size
        val text = if (split.custom || people == 0) {
            pluralStringResource(Res.plurals.shared_people, people, people)
        } else {
            val each = shares.values.maxOrNull() ?: 0
            pluralStringResource(Res.plurals.shared_people, people, people) + " · " +
                stringResource(Res.string.shared_per_person, formatMoney(each, currency))
        }
        Row(
            Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.primaryContainer).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(Symbols.checkCircle, size = 20.dp, filled = true, tint = scheme.onPrimaryContainer)
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
            Text(formatMoney(amountMinor, currency), style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer)
        }
    } else {
        Column(
            Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.errorContainer).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symbol(Symbols.error, size = 20.dp, filled = true, tint = scheme.error)
                Text(
                    stringResource(
                        if (diff > 0) Res.string.shared_split_short else Res.string.shared_split_over,
                        formatMoney(abs(diff), currency),
                    ),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = scheme.onErrorContainer,
                )
                Text(
                    formatMoney(amountMinor - diff, currency) + " / " + formatMoney(amountMinor, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onErrorContainer,
                )
            }
            Text(
                stringResource(Res.string.shared_split_custom_hint),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onErrorContainer,
            )
        }
    }
}
