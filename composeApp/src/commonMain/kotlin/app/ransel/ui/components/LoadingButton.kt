package app.ransel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Primary button with a loading state. Use it for every action that waits for the API: while [loading] the label
 * is replaced by a small progress indicator, the button keeps its size and colour (it does not turn grey like a
 * disabled one) and it cannot be clicked. [enabled] still means "not allowed right now" and greys it out as usual.
 */
@Composable
fun LoadingButton(
    onClick: () -> Unit,
    loading: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        // Disabled while loading, so the click never reaches onClick; the colours below hide the disabled look.
        enabled = enabled && !loading,
        modifier = modifier,
        colors = if (loading) {
            ButtonDefaults.buttonColors(
                containerColor = colors.containerColor,
                contentColor = colors.contentColor,
                disabledContainerColor = colors.containerColor,
                disabledContentColor = colors.contentColor,
            )
        } else {
            colors
        },
        contentPadding = contentPadding,
    ) {
        LoadingContent(loading, indicatorColor = if (colors.contentColor == Color.Unspecified) scheme.onPrimary else colors.contentColor, content)
    }
}

/** [LoadingButton] in the outlined style (secondary actions). */
@Composable
fun LoadingOutlinedButton(
    onClick: () -> Unit,
    loading: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val contentColor = if (colors.contentColor == Color.Unspecified) MaterialTheme.colorScheme.primary else colors.contentColor
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
        colors = if (loading) {
            ButtonDefaults.outlinedButtonColors(
                contentColor = contentColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = contentColor,
            )
        } else {
            colors
        },
        border = if (loading) ButtonDefaults.outlinedButtonBorder(enabled = true) else ButtonDefaults.outlinedButtonBorder(enabled),
        contentPadding = contentPadding,
    ) {
        LoadingContent(loading, indicatorColor = contentColor, content)
    }
}

/** The label stays in the layout (invisible while loading) so the button does not change size. */
@Composable
private fun LoadingContent(loading: Boolean, indicatorColor: Color, content: @Composable RowScope.() -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Row(
            Modifier.alpha(if (loading) 0f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = indicatorColor, strokeWidth = 2.dp)
        }
    }
}

@Preview
@Composable
private fun LoadingButtonPreview() = IrisPreview {
    Box(Modifier.size(width = 280.dp, height = 220.dp)) {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadingButton(onClick = {}, loading = false) { Text("Masuk") }
            LoadingButton(onClick = {}, loading = true) { Text("Masuk") }
            LoadingButton(onClick = {}, loading = false, enabled = false) { Text("Masuk") }
            LoadingOutlinedButton(onClick = {}, loading = false) { Text("Tandai lunas") }
            LoadingOutlinedButton(onClick = {}, loading = true) { Text("Tandai lunas") }
        }
    }
}
