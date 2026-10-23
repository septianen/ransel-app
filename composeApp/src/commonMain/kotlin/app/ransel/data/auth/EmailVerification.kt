package app.ransel.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Email verification after sign-up (designs `VerifikasiEmail`, `EmailTerverifikasi`): what the navigation still has
 * to show. Like [app.ransel.data.invite.InviteLinks], a value stays until its screen dealt with it.
 */
class EmailVerification {
    private val _prompt = MutableStateFlow<String?>(null)

    /** The email of an account that just signed up: "Satu langkah lagi" is shown once. */
    val prompt: StateFlow<String?> = _prompt.asStateFlow()

    private val _token = MutableStateFlow<String?>(null)

    /** The token of a verification link that was opened (App Link, or `/verify-email?token=` on the web). */
    val token: StateFlow<String?> = _token.asStateFlow()

    fun prompt(email: String) {
        _prompt.value = email
    }

    fun clearPrompt() {
        _prompt.value = null
    }

    /** Takes a full URL; ignores anything that is not a verification link. */
    fun offerUrl(url: String) {
        parseVerifyToken(url)?.let { _token.value = it }
    }

    fun offerToken(token: String) {
        _token.value = token.trim().takeIf { it.isNotEmpty() }
    }

    fun clearToken() {
        _token.value = null
    }
}

private val verifyUrl = Regex("""^(?:https?://)?ransel(?:-dev)?\.mietek\.tech/verify-email/?\?(?:[^#]*&)?token=([A-Za-z0-9_-]+)(?:[&#].*)?$""")

/** `https://ransel.mietek.tech/verify-email?token=abc` (or staging `ransel-dev.mietek.tech`) → `abc`; null for any other URL. */
fun parseVerifyToken(url: String): String? = verifyUrl.matchEntire(url.trim())?.groupValues?.get(1)
