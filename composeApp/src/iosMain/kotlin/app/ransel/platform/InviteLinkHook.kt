package app.ransel.platform

import app.ransel.data.invite.InviteLinks
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Called from Swift (`.onOpenURL` / universal link continuation) with the opened URL. */
object InviteLinkHook : KoinComponent {
    private val links: InviteLinks by inject()

    fun open(url: String) = links.offerUrl(url)
}
