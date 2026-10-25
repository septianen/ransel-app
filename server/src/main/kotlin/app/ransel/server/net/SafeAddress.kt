package app.ransel.server.net

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Which addresses a link may point at (TRD "Fetching links that users paste"). The server fetches pages for users, so
 * anything that is not on the public internet is refused: loopback, private ranges, link-local (the cloud metadata
 * address 169.254.169.254 lives there), carrier-grade NAT, documentation and multicast ranges, and IPv6 forms that
 * carry an IPv4 address inside (mapped, NAT64, 6to4).
 */
object SafeAddress {
    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val b = address.address.map { it.toInt() and 0xff }
        return when (b.size) {
            4 -> publicV4(b[0], b[1], b[2])
            16 -> publicV6(b)
            else -> false
        }
    }

    private fun publicV4(a: Int, b: Int, c: Int): Boolean = when {
        a == 0 -> false // "this network"
        a == 10 -> false
        a == 100 && b in 64..127 -> false // carrier-grade NAT
        a == 127 -> false
        a == 169 && b == 254 -> false
        a == 172 && b in 16..31 -> false
        a == 192 && b == 0 && c == 0 -> false // IETF protocol assignments
        a == 192 && b == 0 && c == 2 -> false // documentation
        a == 192 && b == 168 -> false
        a == 198 && b in 18..19 -> false // benchmarking
        a == 198 && b == 51 && c == 100 -> false // documentation
        a == 203 && b == 0 && c == 113 -> false // documentation
        a >= 224 -> false // multicast, reserved, broadcast
        else -> true
    }

    private fun publicV6(b: List<Int>): Boolean {
        val firstTenZero = b.take(10).all { it == 0 }
        // ::, ::1 and IPv4-compatible / mapped forms (::a.b.c.d, ::ffff:a.b.c.d): judge the embedded IPv4
        if (firstTenZero && (b[10] == 0xff && b[11] == 0xff || b[10] == 0 && b[11] == 0)) return publicV4(b[12], b[13], b[14])
        // 64:ff9b::/96 NAT64
        if (b[0] == 0x00 && b[1] == 0x64 && b[2] == 0xff && b[3] == 0x9b && b.subList(4, 12).all { it == 0 }) return publicV4(b[12], b[13], b[14])
        // 2002::/16 6to4 carries an IPv4 address in bytes 2..5
        if (b[0] == 0x20 && b[1] == 0x02) return publicV4(b[2], b[3], b[4])
        if (b[0] and 0xfe == 0xfc) return false // fc00::/7 unique local
        if (b[0] == 0xfe && b[1] and 0xc0 == 0x80) return false // fe80::/10 link-local
        if (b[0] == 0xfe && b[1] and 0xc0 == 0xc0) return false // fec0::/10 site-local (deprecated)
        if (b[0] == 0xff) return false // multicast
        if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8) return false // documentation
        return true
    }

    /** True when [host] is written as an IP address (so name resolution never happens and must be checked up front). */
    fun isLiteral(host: String): Boolean =
        host.startsWith("[") || host.contains(':') || (host.isNotEmpty() && host.all { it.isDigit() || it == '.' })

    /** Parses a literal host without any DNS lookup, or null when it is not a literal. */
    fun literal(host: String): InetAddress? {
        if (!isLiteral(host)) return null
        val bare = host.removePrefix("[").removeSuffix("]")
        return runCatching { InetAddress.getByName(bare) }.getOrNull()?.takeIf { it is Inet6Address || bare.all { c -> c.isDigit() || c == '.' } }
    }
}
