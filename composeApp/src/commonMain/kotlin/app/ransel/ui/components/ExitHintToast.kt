package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.exit_hint

/** "Press back again to exit", shown after the first back press on the home tab. */
@Composable
fun ExitHintToast(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 80.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            stringResource(Res.string.exit_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
