package app.ransel.feature.interview

import app.ransel.shared.model.Interest

/** What an Explore Inspirasi card hands to "Buat trip" ([InterviewViewModel.applyPrefill]). */
data class InterviewPrefill(
    val destination: String,
    val countryCode: String? = null,
    val interests: Set<Interest> = emptySet(),
    /**
     * The idea's activity: after "Buat trip" it becomes one item in "Belum dijadwalkan" (title = activity, place = the
     * region as an area, no note, no label; plan idea-to-trip-flow v2, owner 22 Nov 2026). Null = nothing is added.
     */
    val activity: String? = null,
) {
    companion object {
        /** From the route's text fields; unknown interest names are dropped. Null when there is no destination. */
        fun fromRoute(destination: String?, countryCode: String?, interests: String?, activity: String? = null): InterviewPrefill? {
            val name = destination?.trim().orEmpty()
            if (name.isEmpty()) return null
            val chosen = interests.orEmpty().split(',').mapNotNull { n -> Interest.entries.firstOrNull { it.name == n.trim() } }.toSet()
            return InterviewPrefill(name, countryCode, chosen, activity?.trim()?.takeIf { it.isNotEmpty() }?.take(200))
        }
    }
}
