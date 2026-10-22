package app.ransel.platform

import app.ransel.data.auth.EmailVerification
import app.ransel.data.invite.InviteLinks
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Called from Swift (`.onOpenURL` / universal link continuation) with the opened URL. */
object InviteLinkHook : KoinComponent {
    private val links: InviteLinks by inject()
    private val verification: EmailVerification by inject()

    /** Invite links and email verification links (each ignores the other's URLs). */
    fun open(url: String) {
        links.offerUrl(url)
        verification.offerUrl(url)
    }
}
