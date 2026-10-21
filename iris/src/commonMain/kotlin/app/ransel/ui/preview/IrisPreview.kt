package app.ransel.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.ThemeMode

/**
 * Renders [content] twice, light theme on top and dark theme below, so one `@Preview`
 * shows both. Every component in `ui/components` and every screen's stateless content
 * composable gets at least one preview wrapped in this.
 *
 * ```
 * @Preview
 * @Composable
 * private fun FullScreenStatePreview() = IrisPreview {
 *     FullScreenState(title = "...", body = "...", actionLabel = "...", onAction = {})
 * }
 * ```
 */
@Composable
fun IrisPreview(content: @Composable () -> Unit) {
    Column {
        IrisPreviewPane(ThemeMode.LIGHT, content)
        IrisPreviewPane(ThemeMode.DARK, content)
    }
}

@Composable
private fun IrisPreviewPane(mode: ThemeMode, content: @Composable () -> Unit) {
    IrisTheme(mode = mode) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}
