package app.ransel.feature.credits

import io.ktor.http.encodeURLParameter

/** Receives credit pack orders until a payment provider exists: the owner answers with payment instructions, then a voucher. */
const val CREDIT_ORDER_EMAIL = "developer.septianen@gmail.com"

/** A `mailto:` link with the draft filled in. RFC 6068 asks for CRLF line breaks in the body. */
fun creditOrderMailto(subject: String, body: String): String =
    "mailto:$CREDIT_ORDER_EMAIL?subject=${subject.encodeURLParameter()}&body=${body.replace("\n", "\r\n").encodeURLParameter()}"
