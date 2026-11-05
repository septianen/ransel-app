package app.ransel.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import app.ransel.data.auth.GoogleProof
import app.ransel.data.auth.GoogleResult
import app.ransel.data.auth.GoogleSignInLauncher
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Credential Manager's "Sign in with Google" sheet (system UI). The server's web client id is the token's audience. */
@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher {
    val context = LocalContext.current
    return remember(context) {
        GoogleSignInLauncher { config ->
            val webClientId = config.webClientId ?: return@GoogleSignInLauncher GoogleResult.Failed
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
            try {
                val credential = CredentialManager.create(context).getCredential(context, request).credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    GoogleResult.Success(GoogleProof.IdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken))
                } else {
                    GoogleResult.Failed
                }
            } catch (e: GetCredentialCancellationException) {
                GoogleResult.Cancelled
            } catch (e: GetCredentialException) {
                GoogleResult.Failed
            } catch (e: GoogleIdTokenParsingException) {
                GoogleResult.Failed
            }
        }
    }
}
