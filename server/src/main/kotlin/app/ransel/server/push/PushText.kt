package app.ransel.server.push

import app.ransel.shared.domain.VoteStage
import app.ransel.shared.model.NotificationType

/**
 * Push text in the language the device reported ("id" or "en"). The inbox stores type + params and the app renders it
 * itself; this is only for the system notification. A missing param drops its part of the sentence.
 */
object PushText {
    fun render(type: NotificationType, params: Map<String, String>, locale: String): Pair<String, String> {
        val en = locale.lowercase().startsWith("en")
        val trip = params["tripTitle"].orEmpty()
        val actor = params["actorName"] ?: if (en) "Someone" else "Seseorang"
        return when (type) {
            NotificationType.MEMBER_JOINED ->
                (if (en) "New member in $trip" else "Anggota baru di $trip") to
                    (if (en) "$actor joined the trip." else "$actor bergabung ke trip.")
            NotificationType.MEMBER_REMOVED ->
                (if (en) "Removed from $trip" else "Kamu dikeluarkan dari $trip") to
                    (if (en) "You are no longer a member of this trip." else "Kamu tidak lagi jadi anggota trip ini.")
            NotificationType.ROLE_CHANGED ->
                (if (en) "Your role changed in $trip" else "Perananmu berubah di $trip") to
                    roleLine(params["role"], en)
            NotificationType.EXPENSE_ADDED -> {
                val amount = money(params["amountMinor"], params["currency"])
                val share = money(params["shareMinor"], params["currency"])
                (if (en) "New expense in $trip" else "Pengeluaran baru di $trip") to when {
                    en && share != null -> "$actor added $amount. Your share is $share."
                    en -> "$actor added $amount."
                    share != null -> "$actor mencatat $amount. Bagianmu $share."
                    else -> "$actor mencatat $amount."
                }
            }
            NotificationType.SETTLEMENT_MARKED -> {
                val amount = money(params["amountMinor"], params["currency"])
                if (params["stage"] == "CLAIMED") {
                    (if (en) "$actor says they paid you" else "$actor bilang sudah bayar") to
                        (if (en) "$amount for $trip. Confirm once it has arrived." else "$amount untuk $trip. Konfirmasi kalau sudah masuk.")
                } else {
                    (if (en) "Payment recorded in $trip" else "Pembayaran dicatat di $trip") to
                        (if (en) "$actor marked $amount as settled." else "$actor menandai $amount lunas.")
                }
            }
            NotificationType.MEMBER_CLAIMED -> {
                val guest = params["guestName"].orEmpty()
                (if (en) "$actor took a spot in $trip" else "$actor memilih namanya di $trip") to
                    (if (en) "They are \"$guest\" in the member list." else "Dia adalah \"$guest\" di daftar anggota.")
            }
            NotificationType.COMMENT_MENTION -> {
                val snippet = params["snippet"].orEmpty()
                (if (en) "$actor mentioned you in $trip" else "$actor menyebutmu di $trip") to snippet
            }
            NotificationType.POLL_REQUESTED -> {
                val question = params["question"].orEmpty()
                when (params["stage"]) {
                    "CLOSING" -> (if (en) "Poll closes soon" else "Polling segera ditutup") to
                        (if (en) "Vote on \"$question\" in $trip." else "Pilih untuk \"$question\" di $trip.")
                    "RESET" -> (if (en) "Choose again" else "Pilih ulang") to
                        (if (en) "\"${params["optionLabel"].orEmpty()}\" changed in \"$question\"." else "\"${params["optionLabel"].orEmpty()}\" berubah di \"$question\".")
                    "REOPENED" -> (if (en) "Poll reopened" else "Polling dibuka lagi") to
                        (if (en) "Vote on \"$question\" in $trip." else "Pilih untuk \"$question\" di $trip.")
                    else -> (if (en) "$actor started a poll" else "$actor membuat polling") to
                        (if (en) "\"$question\" in $trip. Cast your vote." else "\"$question\" di $trip. Ayo pilih.")
                }
            }
            NotificationType.POLL_RESULT -> {
                val question = params["question"].orEmpty()
                if (params["stage"] == "TIED") {
                    (if (en) "It is a tie" else "Hasil polling seri") to
                        (if (en) "Pick the winner of \"$question\" in $trip." else "Pilih pemenang \"$question\" di $trip.")
                } else {
                    val winner = params["winner"]
                    (if (en) "Poll decided" else "Polling diputuskan") to when {
                        en && winner != null -> "$actor chose \"$winner\" for \"$question\"."
                        en -> "$actor closed \"$question\" without choosing."
                        winner != null -> "$actor memilih \"$winner\" untuk \"$question\"."
                        else -> "$actor menutup \"$question\" tanpa memilih."
                    }
                }
            }
            NotificationType.PACKING_LIST -> {
                val left = params["requiredLeft"]?.toIntOrNull() ?: 0
                when (params["stage"]) {
                    "UPDATED" -> (if (en) "Packing list updated" else "Daftar bawaan diperbarui") to
                        (if (en) "$actor changed the list for $trip." else "$actor mengubah daftar untuk $trip.")
                    "REMIND" -> (if (en) "Don't forget your packing" else "Jangan lupa bawaanmu") to when {
                        en && left > 0 -> "$left required items left for $trip."
                        en -> "Check your list for $trip."
                        left > 0 -> "Masih $left barang wajib untuk $trip."
                        else -> "Cek daftar bawaanmu untuk $trip."
                    }
                    else -> (if (en) "$actor sent a packing list" else "$actor mengirim daftar bawaan") to
                        (if (en) "For $trip. It is in your list now." else "Untuk $trip. Sudah masuk ke daftarmu.")
                }
            }
            NotificationType.PACKING_REMINDER -> {
                val n = params["remaining"].orEmpty()
                (if (en) "Packing for $trip" else "Packing untuk $trip") to
                    (if (en) "$n items are not packed yet. Departure is tomorrow." else "$n barang belum siap. Berangkat besok.")
            }
            NotificationType.ACTIVITY_REMINDER -> {
                val title = params["title"].orEmpty()
                val time = params["time"].orEmpty()
                (if (en) "Coming up: $title" else "Sebentar lagi: $title") to
                    (if (en) "At $time in $trip." else "Pukul $time di $trip.")
            }
            NotificationType.VOTE_REQUESTED -> {
                if (params["stage"] == VoteStage.CLOSING) {
                    val left = params["remaining"]
                    (if (en) "Voting closes in a few hours" else "Voting segera ditutup") to when {
                        en && left != null -> "You have not picked $left places in $trip yet."
                        en -> "Pick your places in $trip."
                        left != null -> "$left tempat belum kamu pilih di $trip."
                        else -> "Pilih tempatmu di $trip."
                    }
                } else if (params["recipientRole"] == "OWNER") {
                    val n = params["placeCount"]
                    (if (en) "$actor suggested ${n ?: "some"} places" else "$actor mengusulkan ${n ?: "beberapa"} tempat") to
                        (if (en) "Vote too, then decide what goes into $trip." else "Ikut pilih, lalu putuskan mana yang masuk itinerary $trip.")
                } else {
                    val n = params["placeCount"]
                    (if (en) "$actor wants your picks" else "$actor meminta pilihanmu") to when {
                        en && n != null -> "$n places for $trip. Swipe the ones you like."
                        en -> "Places for $trip. Swipe the ones you like."
                        n != null -> "$n tempat untuk $trip. Geser kanan yang kamu suka."
                        else -> "Tempat untuk $trip. Geser kanan yang kamu suka."
                    }
                }
            }
            NotificationType.VOTE_RESULT -> {
                if (params["stage"] == VoteStage.READY) {
                    val voted = params["voted"]
                    val members = params["members"]
                    (if (en) "Voting is done, time to decide" else "Voting selesai, saatnya memutuskan") to when {
                        en && voted != null && members != null -> "$voted of $members members picked places for $trip."
                        en -> "Choose which places go into $trip."
                        voted != null && members != null -> "$voted dari $members anggota sudah memilih untuk $trip."
                        else -> "Pilih tempat yang masuk itinerary $trip."
                    }
                } else {
                    val chosen = params["chosenCount"]?.toIntOrNull()
                    when {
                        chosen == 0 -> (if (en) "Voting closed in $trip" else "Voting ditutup di $trip") to
                            (if (en) "$actor closed it without choosing places." else "$actor menutup voting tanpa memilih tempat.")
                        chosen != null -> (if (en) "$actor chose $chosen places" else "$actor memilih $chosen tempat") to
                            (if (en) "They are in Unscheduled in $trip." else "Sudah ada di Belum dijadwalkan di $trip.")
                        else -> (if (en) "Voting result for $trip" else "Hasil voting untuk $trip") to
                            (if (en) "The owner chose the places." else "Owner sudah memilih tempatnya.")
                    }
                }
            }
        }
    }

    private fun roleLine(role: String?, en: Boolean): String {
        val name = when (role) {
            "EDITOR" -> "Editor"
            "VIEWER" -> if (en) "Viewer" else "Pembaca"
            "OWNER" -> if (en) "Owner" else "Pemilik"
            else -> role.orEmpty()
        }
        return if (en) "You are now $name." else "Kamu sekarang $name."
    }

    /** "Rp50.000", "US$12,50". Null when the amount is missing. */
    internal fun money(amountMinor: String?, currency: String?): String? {
        val minor = amountMinor?.toLongOrNull() ?: return null
        val code = (currency ?: return null).uppercase()
        val decimals = if (code in ZERO_DECIMALS) 0 else 2
        var divisor = 1L
        repeat(decimals) { divisor *= 10 }
        val whole = (kotlin.math.abs(minor) / divisor).toString().reversed().chunked(3).joinToString(".").reversed()
        val fraction = if (decimals == 0) "" else "," + (kotlin.math.abs(minor) % divisor).toString().padStart(decimals, '0')
        val symbol = SYMBOLS[code] ?: "$code "
        return "${if (minor < 0) "-" else ""}$symbol$whole$fraction"
    }

    private val ZERO_DECIMALS = setOf("IDR", "JPY", "KRW", "VND")
    private val SYMBOLS = mapOf("IDR" to "Rp", "USD" to "US$", "EUR" to "€", "JPY" to "¥", "SGD" to "S$", "MYR" to "RM")
}
