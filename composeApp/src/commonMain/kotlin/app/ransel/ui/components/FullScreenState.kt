package app.ransel.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.content.remoteErrorCopy
import app.ransel.content.stringResource
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.ic_confuse
import ransel.composeapp.generated.resources.error_code

/**
 * Full-screen state / empty state (design: capybara 220 dp, title 22/28 w800, body-large muted,
 * full-width primary button, optional text button, small error code).
 *
 * [illustration] is decorative (`contentDescription = null`). Pass the capybara that the screen's
 * design uses (see `docs/design/README.md`); the default confused capybara fits generic errors.
 * `null` leaves an empty box of the same size so layouts do not shift. With an [errorCode], what Karung published
 * for that code replaces the title, body, primary button and illustration.
 */
@Composable
fun FullScreenState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: Painter? = painterResource(Res.drawable.ic_confuse),
    illustrationSize: androidx.compose.ui.unit.Dp = 220.dp,
    errorCode: String? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    // Karung's copy for this error code wins over the screen's own (remote content, stage E)
    val remote = remoteErrorCopy(errorCode)
    val title = remote?.title ?: title
    val body = remote?.message ?: body
    val primaryLabel = if (primaryLabel != null) remote?.button ?: primaryLabel else null
    val illustration = remote?.illustration?.let { painterResource(it) } ?: illustration
    // Centered column, never wider than 480 dp: on a wide web window a full-width button looks wrong.
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        if (illustration != null) {
            Image(painter = illustration, contentDescription = null, modifier = Modifier.size(illustrationSize))
        } else {
            Box(Modifier.size(illustrationSize))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (errorCode != null) {
            Text(
                text = stringResource(Res.string.error_code, errorCode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (primaryLabel != null && onPrimary != null) {
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp),
            ) { Text(primaryLabel, style = MaterialTheme.typography.titleMedium) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(
                onClick = onSecondary,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(secondaryLabel, style = MaterialTheme.typography.titleMedium) }
        }
    }
    }
}

@Preview
@Composable
private fun FullScreenStatePreview() = IrisPreview {
    FullScreenState(
        title = "Waduh, sinyalnya lagi tidur",
        body = "Periksa koneksimu, lalu coba lagi.",
        errorCode = "NETWORK_TIMEOUT",
        primaryLabel = "Coba lagi",
        onPrimary = {},
        secondaryLabel = "Pakai offline",
        onSecondary = {},
    )
}
