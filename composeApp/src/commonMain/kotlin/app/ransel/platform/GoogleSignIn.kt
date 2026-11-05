package app.ransel.platform

import androidx.compose.runtime.Composable
import app.ransel.data.auth.GoogleSignInLauncher

/** The platform's Google step for "Lanjutkan dengan Google" (see [GoogleSignInLauncher]). */
@Composable
expect fun rememberGoogleSignInLauncher(): GoogleSignInLauncher
