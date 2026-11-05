package app.ransel.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.ransel.data.auth.GoogleProof
import app.ransel.data.auth.GoogleResult
import app.ransel.data.auth.GoogleSignInLauncher
import kotlinx.coroutines.CompletableDeferred

/** Loads Google Identity Services ahead of the tap, so the popup opens inside the user's click (browsers block it otherwise). */
@JsFun(
    """() => {
        if (window.google && window.google.accounts && window.google.accounts.oauth2) return;
        if (document.getElementById('gsi-client')) return;
        const s = document.createElement('script');
        s.id = 'gsi-client';
        s.src = 'https://accounts.google.com/gsi/client';
        s.async = true;
        document.head.appendChild(s);
    }""",
)
private external fun loadGoogleIdentity()

/**
 * GIS code client in popup mode (our own button, not Google's rendered one). The one-time code goes to the server, which
 * exchanges it with the client secret. [fail] gets GIS's error type: "popup_closed" = the user closed it.
 */
@JsFun(
    """(clientId, ok, fail) => {
        const run = () => {
            try {
                window.google.accounts.oauth2.initCodeClient({
                    client_id: clientId,
                    scope: 'openid email profile',
                    ux_mode: 'popup',
                    callback: (r) => r.code ? ok(r.code) : fail(r.error || 'error'),
                    error_callback: (e) => fail((e && e.type) || 'error'),
                }).requestCode();
            } catch (e) { fail('error'); }
        };
        if (window.google && window.google.accounts && window.google.accounts.oauth2) { run(); return; }
        const s = document.getElementById('gsi-client') || document.createElement('script');
        s.addEventListener('load', run);
        s.addEventListener('error', () => fail('load'));
        if (!s.parentNode) { s.id = 'gsi-client'; s.src = 'https://accounts.google.com/gsi/client'; s.async = true; document.head.appendChild(s); }
    }""",
)
private external fun requestGoogleCode(clientId: String, ok: (String) -> Unit, fail: (String) -> Unit)

@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher = remember {
    loadGoogleIdentity()
    GoogleSignInLauncher { config ->
        val clientId = config.webClientId ?: return@GoogleSignInLauncher GoogleResult.Failed
        val result = CompletableDeferred<GoogleResult>()
        requestGoogleCode(
            clientId,
            ok = { code -> result.complete(GoogleResult.Success(GoogleProof.AuthCode(code))) },
            fail = { type -> result.complete(if (type == "popup_closed") GoogleResult.Cancelled else GoogleResult.Failed) },
        )
        result.await()
    }
}
