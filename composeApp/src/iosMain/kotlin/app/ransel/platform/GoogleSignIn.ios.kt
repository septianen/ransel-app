package app.ransel.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.ransel.data.auth.GoogleProof
import app.ransel.data.auth.GoogleResult
import app.ransel.data.auth.GoogleSignInLauncher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Swift side of Google sign-in: the Google Sign-In SDK (system "wants to use google.com to sign in" alert, then the web
 * sheet) lives in the Xcode project, which sets [handler] at start-up. Called with the iOS client id and the server
 * (web) client id; it answers once with the ID token, or null with `cancelled` = true when the user closed the sheet.
 * Until the Xcode project sets it, Google sign-in fails softly.
 */
object GoogleSignInHook {
    var handler: ((iosClientId: String, serverClientId: String, onResult: (idToken: String?, cancelled: Boolean) -> Unit) -> Unit)? = null
}

@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher = remember {
    GoogleSignInLauncher { config ->
        val start = GoogleSignInHook.handler
        val iosClientId = config.iosClientId
        val serverClientId = config.webClientId
        if (start == null || iosClientId == null || serverClientId == null) {
            GoogleResult.Failed
        } else {
            suspendCancellableCoroutine { continuation ->
                start(iosClientId, serverClientId) { idToken, cancelled ->
                    if (continuation.isActive) {
                        continuation.resume(
                            when {
                                idToken != null -> GoogleResult.Success(GoogleProof.IdToken(idToken))
                                cancelled -> GoogleResult.Cancelled
                                else -> GoogleResult.Failed
                            },
                        )
                    }
                }
            }
        }
    }
}
