package app.ransel.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.app_launcher

/**
 * Opening screen while preferences and the stored session load: the app illustration, large and centred on the
 * surface. It is decorative (`contentDescription = null`). Wide windows (web) cap it at 420 dp.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Image(
            painterResource(Res.drawable.app_launcher),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 420.dp).fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun SplashScreenPreview() = IrisPreview { SplashScreen() }
