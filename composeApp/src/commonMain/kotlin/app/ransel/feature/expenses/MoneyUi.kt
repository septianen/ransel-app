package app.ransel.feature.expenses

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ransel.feature.currency.currencySymbol
import app.ransel.feature.tripform.ThousandsVisualTransformation
import app.ransel.platform.rememberShareText
import app.ransel.shared.domain.currencyDecimals
import app.ransel.shared.domain.whatsappLink
import app.ransel.shared.dto.MemberBalanceDto
import app.ransel.shared.dto.PaymentMethodDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.TransferDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.PaymentMethodKind
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.LoadingOutlinedButton
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.format.amountToInput
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.parseAmountMinor
import app.ransel.ui.format.sanitizeAmountInput
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.itinerary_close
import ransel.composeapp.generated.resources.money_claim_confirm
import ransel.composeapp.generated.resources.money_claim_from
import ransel.composeapp.generated.resources.money_claim_reject
import ransel.composeapp.generated.resources.money_claim_waiting
import ransel.composeapp.generated.resources.money_claims_title
import ransel.composeapp.generated.resources.money_get_back
import ransel.composeapp.generated.resources.money_get_back_line
import ransel.composeapp.generated.resources.money_guest_via_whatsapp
import ransel.composeapp.generated.resources.money_mark_settled
import ransel.composeapp.generated.resources.money_owe
import ransel.composeapp.generated.resources.money_pay
import ransel.composeapp.generated.resources.money_pays_you
import ransel.composeapp.generated.resources.money_remind
import ransel.composeapp.generated.resources.money_you_pay
import ransel.composeapp.generated.resources.money_your_pay_info
import ransel.composeapp.generated.resources.money_your_pay_info_empty
import ransel.composeapp.generated.resources.money_your_pay_info_line
import ransel.composeapp.generated.resources.payto_claim
import ransel.composeapp.generated.resources.payto_copy
import ransel.composeapp.generated.resources.payto_holder
import ransel.composeapp.generated.resources.payto_no_info
import ransel.composeapp.generated.resources.payto_note
import ransel.composeapp.generated.resources.payto_share_line
import ransel.composeapp.generated.resources.payto_title
import ransel.composeapp.generated.resources.remind_copy
import ransel.composeapp.generated.resources.remind_include
import ransel.composeapp.generated.resources.remind_include_hint
import ransel.composeapp.generated.resources.remind_message
import ransel.composeapp.generated.resources.remind_message_pay_info
import ransel.composeapp.generated.resources.remind_send
import ransel.composeapp.generated.resources.remind_subtitle
import ransel.composeapp.generated.resources.remind_title
import ransel.composeapp.generated.resources.shared_balances_empty
import ransel.composeapp.generated.resources.shared_pays_to
import ransel.composeapp.generated.resources.shared_settle_title
import ransel.composeapp.generated.resources.shared_settled
import ransel.composeapp.generated.resources.shared_you
import ransel.composeapp.generated.resources.shared_you_even

private fun List<TripMemberDto>.find(memberId: String) = firstOrNull { it.memberId == memberId }

private fun List<TripMemberDto>.nameOf(memberId: String) = find(memberId)?.displayName ?: "?"

/** "BCA •••• 7890": the provider and the last four digits only. */
fun PaymentMethodDto.masked(): String {
    val digits = accountNumber.filter { it.isDigit() }
    return "$provider •••• ${digits.takeLast(4)}"
}

/** "BCA 1234567890 a.n. Septian" lines for a reminder message. */
private fun List<PaymentMethodDto>.asMessageLines(holderWord: String): String =
    joinToString(" / ") { "${it.provider} ${it.accountNumber} $holderWord ${it.holderName}" }

// ── Saldo ───────────────────────────────────────────────────────────────────

/**
 * Saldo tab (designs "SaldoTagih", "BayarKe"): my position, my payment info, transfers with the right action per row
 * (to me: Ingatkan + Tandai lunas, from me: Bayar, between others: Tandai lunas) and claims that wait for a receiver.
 */
@Composable
fun SaldoContent(
    balances: BalancesUiState,
    money: MoneyUiState,
    members: List<TripMemberDto>,
    myMemberId: String?,
    onRetry: () -> Unit,
    onSettle: (TransferDto) -> Unit,
    onRemind: (TransferDto) -> Unit,
    onPay: (TransferDto) -> Unit,
    onConfirm: (SettlementDto) -> Unit,
    onReject: (SettlementDto) -> Unit,
    onOpenPayInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = balances.balances
    val failure = balances.failure
    when {
        data == null && balances.loading -> Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        data == null && failure != null ->
            if (failure.isConnection) ConnectionErrorState(onRetry = onRetry, errorCode = failure.code, modifier = modifier)
            else ServerErrorState(onRetry = onRetry, errorCode = failure.code, modifier = modifier)
        data == null -> Unit
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val currency = data.baseCurrency
            val mine = data.members.firstOrNull { it.memberId == myMemberId }
            if (mine != null) PositionHeadline(mine, data.transfers, balances.settledNow, currency)
            if (mine != null) MyPayInfoCard(money.myMethods, money.includePayInfo, onOpenPayInfo)

            val toConfirm = money.claimsTo(myMemberId)
            val waiting = money.claimsFrom(myMemberId)
            if (toConfirm.isNotEmpty() || waiting.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.money_claims_title), style = MaterialTheme.typography.titleMedium)
                    toConfirm.forEach { claim ->
                        ClaimRow(claim, members, currency, waiting = false, busy = money.busy, onConfirm = { onConfirm(claim) }, onReject = { onReject(claim) })
                    }
                    waiting.forEach { claim -> ClaimRow(claim, members, currency, waiting = true, busy = false, onConfirm = {}, onReject = {}) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.shared_settle_title), style = MaterialTheme.typography.titleMedium)
                // a transfer already claimed by its payer shows under the claims, not here
                val claimed = money.settlements.filter { it.status == app.ransel.shared.model.SettlementStatus.CLAIMED_PAID }
                    .map { it.fromMemberId to it.toMemberId }.toSet()
                val open = data.transfers.filter { (it.fromMemberId to it.toMemberId) !in claimed }
                if (open.isEmpty() && balances.settledNow.isEmpty()) {
                    Text(stringResource(Res.string.shared_balances_empty), style = MaterialTheme.typography.bodyLarge)
                }
                open.forEach { t ->
                    SettleRow(
                        t, members, myMemberId, currency, busy = balances.settling,
                        onSettle = { onSettle(t) }, onRemind = { onRemind(t) }, onPay = { onPay(t) },
                    )
                }
                balances.settledNow.forEach { t -> SettledRow(t, members, myMemberId, currency) }
            }
        }
    }
}

@Composable
private fun PositionHeadline(mine: MemberBalanceDto, transfers: List<TransferDto>, settledNow: List<TransferDto>, currency: String) {
    val scheme = MaterialTheme.colorScheme
    val (container, _) = balanceColors(mine.balanceMinor)
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(container).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when {
            mine.balanceMinor > 0 -> {
                Text(stringResource(Res.string.money_get_back), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
                Text(formatMoney(mine.balanceMinor, currency), style = MaterialTheme.typography.headlineMedium, color = scheme.primary)
                val waiting = transfers.count { it.toMemberId == mine.memberId }
                val settled = settledNow.filter { it.toMemberId == mine.memberId }.sumOf { it.amountMinor }
                Text(
                    stringResource(Res.string.money_get_back_line, formatMoney(settled, currency), waiting),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
            mine.balanceMinor < 0 -> {
                Text(stringResource(Res.string.money_owe), style = MaterialTheme.typography.labelMedium, color = scheme.onErrorContainer)
                Text(formatMoney(-mine.balanceMinor, currency), style = MaterialTheme.typography.headlineMedium, color = scheme.error)
            }
            else -> Text(stringResource(Res.string.shared_you_even), style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
        }
    }
}

@Composable
private fun MyPayInfoCard(methods: List<PaymentMethodDto>?, includeInReminders: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(scheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(IrisShape.md).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Symbol(Symbols.accountBalance, size = 22.dp, filled = true, tint = scheme.onTertiaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.money_your_pay_info), style = MaterialTheme.typography.titleSmall)
            Text(
                if (methods.isNullOrEmpty()) stringResource(Res.string.money_your_pay_info_empty)
                else methods.joinToString(" · ") { if (it.kind == PaymentMethodKind.BANK) it.masked() else it.provider } +
                    if (includeInReminders) stringResource(Res.string.money_your_pay_info_line) else "",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun SettleRow(
    t: TransferDto,
    members: List<TripMemberDto>,
    myMemberId: String?,
    currency: String,
    busy: Boolean,
    onSettle: () -> Unit,
    onRemind: () -> Unit,
    onPay: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val toMe = t.toMemberId == myMemberId
    val fromMe = t.fromMemberId == myMemberId
    val other = if (toMe) t.fromMemberId else t.toMemberId
    val otherMember = members.find(other)
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MemberAvatar(otherMember?.displayName ?: "?", other, size = 36.dp, isGuest = otherMember?.userId == null)
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    toMe -> stringResource(Res.string.money_pays_you, members.nameOf(t.fromMemberId))
                    fromMe -> stringResource(Res.string.money_you_pay, members.nameOf(t.toMemberId))
                    else -> stringResource(Res.string.shared_pays_to, members.nameOf(t.fromMemberId), members.nameOf(t.toMemberId))
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            // the amount always shows whole: one line, a little smaller when the column is narrow
            val amountStyle = MaterialTheme.typography.titleMedium
            Text(
                formatMoney(t.amountMinor, currency),
                style = amountStyle,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                    minFontSize = 12.sp, maxFontSize = amountStyle.fontSize, stepSize = 0.5.sp,
                ),
            )
            if (toMe && otherMember?.userId == null) {
                Text(stringResource(Res.string.money_guest_via_whatsapp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        when {
            toMe -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // a bell only (owner, 27 Oct 2026); the word stays for screen readers
                Box(
                    Modifier.size(40.dp).clip(CircleShape).border(1.dp, scheme.outline, CircleShape)
                        .clickable(role = Role.Button, onClick = onRemind),
                    contentAlignment = Alignment.Center,
                ) { Symbol(Symbols.notificationsActive, size = 18.dp, contentDescription = stringResource(Res.string.money_remind)) }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).border(1.dp, scheme.outline, CircleShape)
                        .clickable(enabled = !busy, role = Role.Button, onClick = onSettle),
                    contentAlignment = Alignment.Center,
                ) { Symbol(Symbols.check, size = 18.dp, contentDescription = stringResource(Res.string.money_mark_settled)) }
            }
            fromMe -> Button(onClick = onPay, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(stringResource(Res.string.money_pay), style = MaterialTheme.typography.labelLarge)
            }
            else -> LoadingOutlinedButton(onClick = onSettle, loading = busy) {
                Text(stringResource(Res.string.money_mark_settled), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SettledRow(t: TransferDto, members: List<TripMemberDto>, myMemberId: String?, currency: String) {
    val scheme = MaterialTheme.colorScheme
    val other = if (t.toMemberId == myMemberId) t.fromMemberId else t.toMemberId
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MemberAvatar(members.nameOf(other), other, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.shared_pays_to, members.nameOf(t.fromMemberId), members.nameOf(t.toMemberId)), style = MaterialTheme.typography.bodyMedium)
            Text(formatMoney(t.amountMinor, currency), style = MaterialTheme.typography.titleMedium)
        }
        Row(
            Modifier.clip(CircleShape).background(scheme.primaryContainer).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Symbol(Symbols.checkCircle, size = 16.dp, filled = true, tint = scheme.onPrimaryContainer)
            Text(stringResource(Res.string.shared_settled), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ClaimRow(
    claim: SettlementDto,
    members: List<TripMemberDto>,
    currency: String,
    waiting: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceContainerLow).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val other = if (waiting) claim.toMemberId else claim.fromMemberId
            MemberAvatar(members.nameOf(other), other, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (waiting) stringResource(Res.string.money_claim_waiting, members.nameOf(claim.toMemberId))
                    else stringResource(Res.string.money_claim_from, members.nameOf(claim.fromMemberId)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(formatMoney(claim.amountMinor, currency), style = MaterialTheme.typography.titleMedium)
            }
            if (waiting) Symbol(Symbols.schedule, tint = scheme.onSurfaceVariant)
        }
        if (!waiting) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadingButton(onClick = onConfirm, loading = busy, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) {
                    Text(stringResource(Res.string.money_claim_confirm), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = onReject, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) {
                    Text(stringResource(Res.string.money_claim_reject), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ── Ingatkan (WhatsApp) ─────────────────────────────────────────────────────

/** Design "IngatkanWA": a prefilled message the user sends from their own WhatsApp. RANSEL sends nothing. */
@Composable
fun RemindSheet(
    transfer: TransferDto,
    member: TripMemberDto?,
    myName: String,
    tripTitle: String,
    currency: String,
    myMethods: List<PaymentMethodDto>?,
    includePayInfo: Boolean,
    onIncludePayInfo: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val share = rememberShareText()
    val name = member?.displayName ?: "?"
    val holderWord = stringResource(Res.string.payto_holder)
    val base = stringResource(Res.string.remind_message, name, tripTitle, formatMoney(transfer.amountMinor, currency), myName)
    val methods = myMethods.orEmpty()
    val message = if (includePayInfo && methods.isNotEmpty()) {
        base + " " + stringResource(Res.string.remind_message_pay_info, methods.asMessageLines(holderWord))
    } else {
        base
    }
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SheetHeader(stringResource(Res.string.remind_title, name), stringResource(Res.string.remind_subtitle), onDismiss)
            Text(
                message,
                Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceContainerLow).padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (methods.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.remind_include), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(Res.string.remind_include_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Switch(checked = includePayInfo, onCheckedChange = onIncludePayInfo)
                }
            }
            Button(
                onClick = {
                    val link = whatsappLink(member?.phone, message)
                    if (link != null) uriHandler.openUri(link) else share(message)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Symbol(Symbols.share, size = 20.dp)
                Text(stringResource(Res.string.remind_send), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            }
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(message)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Symbol(Symbols.contentCopy, size = 20.dp)
                Text(stringResource(Res.string.remind_copy), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

// ── Bayar ke ────────────────────────────────────────────────────────────────

/** Design "BayarKe": how much, where to transfer it, then "Saya sudah bayar" so the receiver can confirm. */
@Composable
fun PayToSheet(
    state: PayToState,
    members: List<TripMemberDto>,
    currency: String,
    busy: Boolean,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val name = state.info?.displayName ?: members.nameOf(state.transfer.toMemberId)
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SheetHeader(stringResource(Res.string.payto_title, name), null, onDismiss)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemberAvatar(members.nameOf(state.transfer.fromMemberId), state.transfer.fromMemberId, size = 40.dp)
                    Symbol(Symbols.arrowForward, tint = scheme.onSurfaceVariant)
                    MemberAvatar(name, state.transfer.toMemberId, size = 40.dp)
                }
                Text(
                    formatMoney(state.info?.amountMinor ?: state.transfer.amountMinor, state.info?.currency ?: currency),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(stringResource(Res.string.payto_share_line, name), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.info?.methods.isNullOrEmpty() -> Text(
                    stringResource(Res.string.payto_no_info, name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                else -> state.info!!.methods.forEach { method ->
                    Row(
                        Modifier.fillMaxWidth().clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Symbol(
                            if (method.kind == PaymentMethodKind.BANK) Symbols.accountBalance else Symbols.accountBalanceWallet,
                            filled = true, tint = scheme.primary,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(method.provider, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            Text(method.accountNumber, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(Res.string.payto_holder) + " " + method.holderName, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { clipboard.setText(AnnotatedString(method.accountNumber.filter { it.isDigit() })) }) {
                            Symbol(Symbols.contentCopy, size = 18.dp)
                            Text(stringResource(Res.string.payto_copy), Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.info, size = 18.dp, tint = scheme.onSurfaceVariant)
                Text(stringResource(Res.string.payto_note, name), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            LoadingButton(onClick = onClaim, loading = busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.payto_claim), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

// ── pieces ──────────────────────────────────────────────────────────────────

@Composable
internal fun SheetHeader(title: String, subtitle: String?, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
            Symbol(Symbols.close, contentDescription = stringResource(Res.string.itinerary_close))
        }
    }
}

