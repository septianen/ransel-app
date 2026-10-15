package app.ransel.server.security

import org.slf4j.LoggerFactory

/** Outgoing email. The provider is not chosen yet (open question); add its implementation behind this. */
interface EmailSender {
    suspend fun sendPasswordReset(toEmail: String, displayName: String, link: String)
}

/**
 * Stand-in until a provider exists: writes the reset link to the log so the flow can be used in
 * development. In production it only says that nothing was sent, because the link is a credential.
 */
class LoggingEmailSender(private val production: Boolean) : EmailSender {
    private val log = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    override suspend fun sendPasswordReset(toEmail: String, displayName: String, link: String) {
        if (production) {
            log.warn("Password reset requested but no email provider is configured; nothing was sent")
        } else {
            log.info("DEV EMAIL to={} subject=\"Reset your RANSEL password\" link={}", toEmail, link)
        }
    }
}
