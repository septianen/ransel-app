package app.ransel.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.credits.rememberCredits
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.account_credit_history
import ransel.composeapp.generated.resources.account_payment
import ransel.composeapp.generated.resources.paymethod_title

@Composable
fun PaymentScreen(
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenPaymentMethods: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val credits by rememberCredits()
    PaymentContent(
        credits = credits?.available ?: 0,
        onBack = onBack,
        onTopUp = onOpenCredits,
        onCreditHistory = onOpenCredits,
        onPaymentInfo = onOpenPaymentMethods,
        modifier = modifier,
    )
}

@Composable
internal fun PaymentContent(
    credits: Int,
    onBack: () -> Unit,
    onTopUp: () -> Unit,
    onCreditHistory: () -> Unit,
    onPaymentInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AccountSubScreenTopBar(stringResource(Res.string.account_payment), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp),
        ) {
            CreditsBalanceCard(credits, onTopUp)
            AccountGroupCard(Modifier.padding(top = 16.dp)) {
                AccountSettingRow(Symbols.schedule, stringResource(Res.string.account_credit_history), onClick = onCreditHistory)
                AccountSettingRow(Symbols.creditCard, stringResource(Res.string.paymethod_title), onClick = onPaymentInfo, showDivider = true)
            }
        }
    }
}

@Preview
@Composable
private fun PaymentPreview() = IrisPreview {
    PaymentContent(credits = 20, onBack = {}, onTopUp = {}, onCreditHistory = {}, onPaymentInfo = {})
}
