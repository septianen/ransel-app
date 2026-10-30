package app.ransel.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.ransel.content.stringResource
import org.jetbrains.compose.resources.painterResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.ic_data_not_found
import ransel.composeapp.generated.resources.ic_confuse
import ransel.composeapp.generated.resources.state_back_home
import ransel.composeapp.generated.resources.state_coming_soon_title
import ransel.composeapp.generated.resources.state_connection_body
import ransel.composeapp.generated.resources.state_connection_title
import ransel.composeapp.generated.resources.state_retry
import ransel.composeapp.generated.resources.state_server_body
import ransel.composeapp.generated.resources.state_server_title

/**
 * The three shared full-screen states of the design ("State: error koneksi / error server / segera
 * hadir"): the confused capybara for connection and server errors, the "not found" capybara for coming soon. [errorCode] is the API
 * `ErrorCode`, shown small under the text.
 */
@Composable
fun ConnectionErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    errorCode: String? = null,
    onBack: (() -> Unit)? = null,
) {
    FullScreenState(
        modifier = modifier,
        title = stringResource(Res.string.state_connection_title),
        body = stringResource(Res.string.state_connection_body),
        illustration = painterResource(Res.drawable.ic_confuse),
        errorCode = errorCode,
        primaryLabel = stringResource(Res.string.state_retry),
        onPrimary = onRetry,
        secondaryLabel = onBack?.let { stringResource(Res.string.state_back_home) },
        onSecondary = onBack,
    )
}

@Composable
fun ServerErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    errorCode: String? = null,
    onBack: (() -> Unit)? = null,
) {
    FullScreenState(
        modifier = modifier,
        title = stringResource(Res.string.state_server_title),
        body = stringResource(Res.string.state_server_body),
        illustration = painterResource(Res.drawable.ic_confuse),
        errorCode = errorCode,
        primaryLabel = stringResource(Res.string.state_retry),
        onPrimary = onRetry,
        secondaryLabel = onBack?.let { stringResource(Res.string.state_back_home) },
        onSecondary = onBack,
    )
}

@Composable
fun ComingSoonState(
    body: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    FullScreenState(
        modifier = modifier,
        title = stringResource(Res.string.state_coming_soon_title),
        body = body,
        illustration = painterResource(Res.drawable.ic_data_not_found),
        primaryLabel = primaryLabel,
        onPrimary = onPrimary,
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
    )
}
