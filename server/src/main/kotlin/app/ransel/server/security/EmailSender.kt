package app.ransel.server.security

import app.ransel.server.AppConfig
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties

/** Outgoing email. [SmtpEmailSender] when `SMTP_HOST` is set, otherwise [LoggingEmailSender]. */
interface EmailSender {
    suspend fun sendPasswordReset(toEmail: String, displayName: String, link: String)
    suspend fun sendEmailVerification(toEmail: String, displayName: String, link: String)
    /** An admin created the account in Karung; the link sets the first password. */
    suspend fun sendAccountInvite(toEmail: String, displayName: String, link: String)
}

/**
 * Stand-in until a provider exists: writes the link to the log so the flows can be used in development.
 * In production it only says that nothing was sent, because the link is a credential.
 */
class LoggingEmailSender(private val production: Boolean) : EmailSender {
    private val log = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    override suspend fun sendPasswordReset(toEmail: String, displayName: String, link: String) =
        logLink("Reset your RANSEL password", toEmail, link)

    override suspend fun sendEmailVerification(toEmail: String, displayName: String, link: String) =
        logLink("Verify your RANSEL email", toEmail, link)

    override suspend fun sendAccountInvite(toEmail: String, displayName: String, link: String) =
        logLink("Your RANSEL account is ready", toEmail, link)

    private fun logLink(subject: String, toEmail: String, link: String) {
        if (production) {
            log.warn("Email \"{}\" requested but no email provider is configured; nothing was sent", subject)
        } else {
            log.info("DEV EMAIL to={} subject=\"{}\" link={}", toEmail, subject, link)
        }
    }
}

/**
 * Plain-text mail over SMTP with STARTTLS (port 587) or implicit TLS (port 465). Any provider with an SMTP relay works,
 * so choosing or switching the provider is only a matter of env vars. The text is Indonesian first, English below,
 * because sign-up does not tell us the user's language.
 */
class SmtpEmailSender(private val smtp: AppConfig.Smtp) : EmailSender {
    private val session: Session = Session.getInstance(
        Properties().apply {
            put("mail.smtp.host", smtp.host)
            put("mail.smtp.port", smtp.port.toString())
            put("mail.smtp.auth", smtp.username.isNotEmpty().toString())
            if (smtp.port == 465) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
        },
    )

    override suspend fun sendPasswordReset(toEmail: String, displayName: String, link: String) = send(
        toEmail,
        subject = "Atur ulang kata sandi RANSEL / Reset your RANSEL password",
        body = """
            Hai $displayName,

            Buka link ini untuk membuat kata sandi baru. Link berlaku 30 menit dan hanya bisa dipakai sekali:
            $link

            Kalau kamu tidak meminta ini, abaikan saja email ini. Kata sandimu tidak berubah.

            ---

            Hi $displayName,

            Open this link to choose a new password. It works once and expires in 30 minutes:
            $link

            If you did not ask for this, ignore this email. Your password stays the same.

            RANSEL
        """.trimIndent(),
    )

    override suspend fun sendEmailVerification(toEmail: String, displayName: String, link: String) = send(
        toEmail,
        subject = "Verifikasi email RANSEL kamu / Verify your RANSEL email",
        body = """
            Hai $displayName,

            Terima kasih sudah daftar di RANSEL. Buka link ini untuk verifikasi email kamu. Setelah itu Jelajah dan kredit awalmu langsung aktif. Link berlaku 24 jam:
            $link

            Kalau kamu tidak membuat akun RANSEL, abaikan saja email ini.

            ---

            Hi $displayName,

            Thanks for signing up for RANSEL. Open this link to verify your email; Explore and your starter credits unlock right after. The link expires in 24 hours:
            $link

            If you did not create a RANSEL account, ignore this email.

            RANSEL
        """.trimIndent(),
    )

    override suspend fun sendAccountInvite(toEmail: String, displayName: String, link: String) = send(
        toEmail,
        subject = "Akun RANSEL kamu sudah dibuat / Your RANSEL account is ready",
        body = """
            Hai $displayName,

            Tim RANSEL sudah membuatkan akun untuk email ini. Buka link ini untuk mengatur kata sandimu, lalu masuk di aplikasi RANSEL. Link berlaku 7 hari dan hanya bisa dipakai sekali:
            $link

            Kalau kamu tidak mengharapkan email ini, abaikan saja.

            ---

            Hi $displayName,

            The RANSEL team created an account for this email. Open this link to set your password, then sign in to the RANSEL app. It works once and expires in 7 days:
            $link

            If you were not expecting this, ignore this email.

            RANSEL
        """.trimIndent(),
    )

    private suspend fun send(toEmail: String, subject: String, body: String) = withContext(Dispatchers.IO) {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(smtp.from))
            setRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
        }
        if (smtp.username.isNotEmpty()) {
            Transport.send(message, smtp.username, smtp.password)
        } else {
            Transport.send(message)
        }
    }
}
