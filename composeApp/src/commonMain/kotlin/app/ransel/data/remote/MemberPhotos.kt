package app.ransel.data.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.GuestMemberDto
import app.ransel.shared.dto.TripMemberDto

/** Absolute URL of an image path from the API (relative to `ApiRoutes.BASE`); null for none. */
fun apiImageUrl(path: String?): String? {
    val p = path?.takeIf { it.isNotBlank() } ?: return null
    if (p.startsWith("http")) return p
    return apiOrigin().trimEnd('/') + ApiRoutes.BASE + "/" + p.trimStart('/')
}

/**
 * Profile photos by member id and user id (B19.1). Many screens show a member from a DTO that only carries the id and
 * name (balances, comments, documents), so instead of passing the photo through each of them, every members list the
 * app loads lands here and `MemberAvatar` looks its key up. A snapshot state: avatars update when a list arrives.
 * Kept in memory only; until the members of a trip load, avatars show initials.
 */
object MemberPhotos {
    private var photos: Map<String, String> by mutableStateOf(emptyMap())
    private val userOfMember = mutableMapOf<String, String>()

    fun urlFor(key: String): String? = photos[key]

    fun record(members: List<TripMemberDto>) {
        val next = photos.toMutableMap()
        members.forEach { m ->
            m.userId?.let { userOfMember[m.memberId] = it }
            next.put(m.memberId, m.avatarUrl)
            m.userId?.let { next.put(it, m.avatarUrl) }
        }
        photos = next
    }

    fun recordGuestPage(members: List<GuestMemberDto>) {
        val next = photos.toMutableMap()
        members.forEach { next.put(it.memberId, it.avatarUrl) }
        photos = next
    }

    /** The signed-in user's own photo (sign-in, `/me`, after changing it): also on each of their memberships seen so far. */
    fun recordUser(userId: String, url: String?) {
        val next = photos.toMutableMap()
        next.put(userId, url)
        userOfMember.filterValues { it == userId }.keys.forEach { next.put(it, url) }
        photos = next
    }

    private fun MutableMap<String, String>.put(key: String, url: String?) {
        if (url.isNullOrBlank()) remove(key) else this[key] = url
    }
}
