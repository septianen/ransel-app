package app.ransel.data.invite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The invite code the user arrived with (link tapped, or `/j/<code>` opened on the web) and has not dealt
 * with yet. It stays until the join screen is closed or accepted, so a guest can sign in first and still land
 * on the invite afterwards.
 */
class InviteLinks {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Takes a full URL or a bare code; ignores anything that is not an invite link. */
    fun offerUrl(url: String) {
        parseInviteCode(url)?.let { _pending.value = it }
    }

    fun offerCode(code: String) {
        _pending.value = code.trim().takeIf { it.isNotEmpty() }
    }

    fun clear() {
        _pending.value = null
    }
}

private val inviteUrl = Regex("""^(?:https?://)?ransel(?:-dev)?\.mietek\.tech/j/([A-Za-z0-9_-]+)/?(?:[?#].*)?$""")

/** `https://ransel.mietek.tech/j/BALI-7K2Q` (or staging `ransel-dev.mietek.tech`) → `BALI-7K2Q`; null for any other URL. Codes are upper-cased. */
fun parseInviteCode(url: String): String? =
    inviteUrl.matchEntire(url.trim())?.groupValues?.get(1)?.uppercase()
