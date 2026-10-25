package app.ransel.shared.domain

/*
 * Trip members have their own id (TRD "Group trip features"): expenses, splits, payers and settlements point at
 * trip_members.id, and a member does not need an account.
 *
 * The member row of an account is named by its trip and user, so the app can write `paidByMemberId` offline
 * without asking the server first, and the server and the V15 migration derive the same id.
 */

/**
 * Stable id of [userId]'s own member row in [tripId]: the first 16 bytes of SHA-256("<tripId>:<userId>") in lower
 * case, shaped like a UUID (version 5, RFC 4122 variant). Members without an account get a random UUID instead.
 * The V15 migration has an SQL copy of this function; `MemberIdMigrationTest` keeps the two equal.
 */
fun memberIdFor(tripId: String, userId: String): String {
    val hash = sha256("${tripId.lowercase()}:${userId.lowercase()}".encodeToByteArray())
    val b = hash.copyOf(16)
    b[6] = ((b[6].toInt() and 0x0f) or 0x50).toByte()
    b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}

private val SHA256_K = intArrayOf(
    0x428a2f98u.toInt(), 0x71374491u.toInt(), 0xb5c0fbcfu.toInt(), 0xe9b5dba5u.toInt(), 0x3956c25bu.toInt(), 0x59f111f1u.toInt(), 0x923f82a4u.toInt(), 0xab1c5ed5u.toInt(),
    0xd807aa98u.toInt(), 0x12835b01u.toInt(), 0x243185beu.toInt(), 0x550c7dc3u.toInt(), 0x72be5d74u.toInt(), 0x80deb1feu.toInt(), 0x9bdc06a7u.toInt(), 0xc19bf174u.toInt(),
    0xe49b69c1u.toInt(), 0xefbe4786u.toInt(), 0x0fc19dc6u.toInt(), 0x240ca1ccu.toInt(), 0x2de92c6fu.toInt(), 0x4a7484aau.toInt(), 0x5cb0a9dcu.toInt(), 0x76f988dau.toInt(),
    0x983e5152u.toInt(), 0xa831c66du.toInt(), 0xb00327c8u.toInt(), 0xbf597fc7u.toInt(), 0xc6e00bf3u.toInt(), 0xd5a79147u.toInt(), 0x06ca6351u.toInt(), 0x14292967u.toInt(),
    0x27b70a85u.toInt(), 0x2e1b2138u.toInt(), 0x4d2c6dfcu.toInt(), 0x53380d13u.toInt(), 0x650a7354u.toInt(), 0x766a0abbu.toInt(), 0x81c2c92eu.toInt(), 0x92722c85u.toInt(),
    0xa2bfe8a1u.toInt(), 0xa81a664bu.toInt(), 0xc24b8b70u.toInt(), 0xc76c51a3u.toInt(), 0xd192e819u.toInt(), 0xd6990624u.toInt(), 0xf40e3585u.toInt(), 0x106aa070u.toInt(),
    0x19a4c116u.toInt(), 0x1e376c08u.toInt(), 0x2748774cu.toInt(), 0x34b0bcb5u.toInt(), 0x391c0cb3u.toInt(), 0x4ed8aa4au.toInt(), 0x5b9cca4fu.toInt(), 0x682e6ff3u.toInt(),
    0x748f82eeu.toInt(), 0x78a5636fu.toInt(), 0x84c87814u.toInt(), 0x8cc70208u.toInt(), 0x90befffau.toInt(), 0xa4506cebu.toInt(), 0xbef9a3f7u.toInt(), 0xc67178f2u.toInt(),
)

/** Plain SHA-256, kept here because Kotlin common code has no hash function. */
internal fun sha256(message: ByteArray): ByteArray {
    val bitLength = message.size.toLong() * 8
    val paddedSize = ((message.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedSize)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) padded[paddedSize - 1 - i] = (bitLength ushr (8 * i)).toByte()

    var h0 = 0x6a09e667; var h1 = 0xbb67ae85u.toInt(); var h2 = 0x3c6ef372; var h3 = 0xa54ff53au.toInt()
    var h4 = 0x510e527f; var h5 = 0x9b05688cu.toInt(); var h6 = 0x1f83d9ab; var h7 = 0x5be0cd19
    val w = IntArray(64)
    for (chunk in 0 until paddedSize step 64) {
        for (i in 0 until 16) {
            val o = chunk + i * 4
            w[i] = ((padded[o].toInt() and 0xff) shl 24) or ((padded[o + 1].toInt() and 0xff) shl 16) or
                ((padded[o + 2].toInt() and 0xff) shl 8) or (padded[o + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h0; var b = h1; var c = h2; var d = h3; var e = h4; var f = h5; var g = h6; var h = h7
        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = h + s1 + ch + SHA256_K[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
    }
    val out = ByteArray(32)
    intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).forEachIndexed { i, v ->
        out[i * 4] = (v ushr 24).toByte(); out[i * 4 + 1] = (v ushr 16).toByte()
        out[i * 4 + 2] = (v ushr 8).toByte(); out[i * 4 + 3] = v.toByte()
    }
    return out
}

private fun Int.rotateRight(bits: Int) = (this ushr bits) or (this shl (32 - bits))
