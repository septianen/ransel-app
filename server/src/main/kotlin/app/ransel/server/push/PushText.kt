package app.ransel.server.push

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
                (if (en) "Payment recorded in $trip" else "Pembayaran dicatat di $trip") to
                    (if (en) "$actor marked $amount as settled." else "$actor menandai $amount lunas.")
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
            NotificationType.VOTE_REQUESTED ->
                (if (en) "Your vote is needed in $trip" else "Suaramu dibutuhkan di $trip") to
                    (if (en) "Pick the places you like." else "Pilih tempat yang kamu suka.")
            NotificationType.VOTE_RESULT ->
                (if (en) "Voting result for $trip" else "Hasil voting untuk $trip") to
                    (if (en) "The owner chose the places." else "Owner sudah memilih tempatnya.")
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
