package app.ransel.server.routes

import app.ransel.server.db.PollOptions
import app.ransel.server.db.Polls
import app.ransel.server.db.Trips
import app.ransel.server.service.InviteService
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.model.PollStatus
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.ZoneId
import app.ransel.server.plugins.GuestRateLimit

/**
 * Link previews (PRD "Sharing to WhatsApp"): a small HTML page with Open Graph tags for WhatsApp and other crawlers.
 * nginx sends only crawler user agents for /p/<code> and /j/<code> here; people get the web app. One static image per
 * kind (owner decision 26 Oct 2026), the text is the poll's or the trip's. Indonesian unless the crawler asks for
 * English. Unknown or turned-off links get a plain RANSEL preview, never an error page.
 */
fun Route.ogRoutes(invites: InviteService, appBaseUrl: String) {
    val base = appBaseUrl.trimEnd('/')
    rateLimit(GuestRateLimit) {
        get(ApiRoutes.ogPoll("{code}")) {
            val code = call.parameters["code"].orEmpty().trim().uppercase()
            val en = call.english()
            val preview = transaction { pollPreview(code, en) }
            call.respondOg(
                title = preview?.first ?: "RANSEL",
                description = preview?.second ?: generic(en),
                image = "$base/og/poll.png",
                url = "$base/p/$code",
            )
        }
        get(ApiRoutes.ogInvite("{code}")) {
            val code = call.parameters["code"].orEmpty().trim().uppercase()
            val en = call.english()
            val preview = runCatching { invites.preview(code) }.getOrNull()
            call.respondOg(
                title = preview?.let { if (en) "${it.ownerName} invites you to ${it.tripTitle}" else "${it.ownerName} ngajak kamu ke ${it.tripTitle}" } ?: "RANSEL",
                description = preview?.let {
                    val people = if (en) "${it.memberCount} people" else "${it.memberCount} orang"
                    val start = it.startDate
                    val end = it.endDate
                    // a trip without dates says so, like the app's "Tanggal belum ditetapkan"
                    val dates = if (start != null && end != null) dateRange(start.toJavaLocalDate(), end.toJavaLocalDate(), en)
                    else if (en) "dates not set yet" else "tanggal belum ditetapkan"
                    listOf(dates, people, if (en) "open it without signing up" else "buka tanpa harus daftar").joinToString(" · ")
                } ?: generic(en),
                image = "$base/og/invite.png",
                url = "$base/j/$code",
            )
        }
    }
}

/** Title and description of a shared poll, or null when the code leads nowhere. */
private fun pollPreview(code: String, en: Boolean): Pair<String, String>? {
    val poll = Polls.selectAll().where { (Polls.shareCode eq code) and Polls.deletedAt.isNull() }.singleOrNull() ?: return null
    val trip = Trips.selectAll().where { (Trips.id eq poll[Polls.tripId]) and Trips.deletedAt.isNull() }.singleOrNull() ?: return null
    val options = PollOptions.select(PollOptions.id).where { (PollOptions.pollId eq poll[Polls.id]) and PollOptions.deletedAt.isNull() }.count()
    val zone = trip[Trips.timeZone]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("Asia/Jakarta")
    val closes = poll[Polls.closesAt].atZone(zone)
    val day = (if (en) EN_DAYS else ID_DAYS)[closes.dayOfWeek.value - 1]
    val time = if (en) "%02d:%02d".format(closes.hour, closes.minute) else "%02d.%02d".format(closes.hour, closes.minute)
    val status = when {
        poll[Polls.status] != PollStatus.OPEN -> if (en) "voting is over" else "polling sudah selesai"
        en -> "closes $day $time"
        else -> "tutup $day $time"
    }
    val count = if (en) "$options options" else "$options pilihan"
    val free = if (en) "vote without signing up" else "pilih tanpa daftar"
    return "${poll[Polls.question]} · ${trip[Trips.title]}" to listOf(count, status, free).joinToString(" · ")
}

private val ID_DAYS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
private val EN_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val ID_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
private val EN_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "20–23 Nov", "28 Nov – 2 Des". */
internal fun dateRange(start: LocalDate, end: LocalDate, en: Boolean): String {
    val months = if (en) EN_MONTHS else ID_MONTHS
    fun d(date: LocalDate) = "${date.dayOfMonth} ${months[date.monthValue - 1]}"
    return when {
        start == end -> d(start)
        start.month == end.month && start.year == end.year -> "${start.dayOfMonth}–${d(end)}"
        else -> "${d(start)} – ${d(end)}"
    }
}

private fun generic(en: Boolean) =
    if (en) "Plan trips together: itinerary, packing and shared costs." else "Rencanakan trip bareng: itinerary, packing, dan patungan."

private fun ApplicationCall.english() =
    request.headers[HttpHeaders.AcceptLanguage]?.trim()?.lowercase()?.startsWith("en") == true

private suspend fun ApplicationCall.respondOg(title: String, description: String, image: String, url: String) {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val html = """<!doctype html>
<html lang="id"><head><meta charset="utf-8">
<title>${esc(title)}</title>
<meta name="description" content="${esc(description)}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="RANSEL">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(description)}">
<meta property="og:url" content="${esc(url)}">
<meta property="og:image" content="${esc(image)}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
</head><body><a href="${esc(url)}">${esc(title)}</a></body></html>"""
    // crawlers re-read now and then; a short cache keeps the vote count fresh enough
    response.header(HttpHeaders.CacheControl, "public, max-age=300")
    respondText(html, ContentType.Text.Html.withParameter("charset", "utf-8"), HttpStatusCode.OK)
}

private fun kotlinx.datetime.LocalDate.toJavaLocalDate(): LocalDate = LocalDate.of(year, month.ordinal + 1, day)
