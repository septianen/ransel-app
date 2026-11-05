package app.ransel.ui.tour

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import app.ransel.data.credit.CreditsStore
import app.ransel.data.repository.PreferencesRepository
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource

/** One tour; its flag is `tour.<userId>.<key>` in the local preferences (never on the server). */
enum class TourId(val key: String) {
    HOME("home"), TRIP("trip"), ITINERARY("itinerary"), PACKING("packing"), EXPENSES("expenses"), SWIPE("swipe"),
}

/** Elements a tour points at; a screen registers each with [Modifier.tourTarget]. */
enum class TourTarget {
    HOME_CREATE, TAB_TRIPS, TAB_EXPLORE, TAB_ACCOUNT, TRIP_TABS, TRIP_ADD, ITINERARY_DAYS, PACKING_SWITCH, EXPENSES_SWITCH, SWIPE_CARDS,
}

/** Where a target is (root coordinates, px), its corner radius (dp) and what tapping it does (null = nothing). */
class TargetInfo(val bounds: Rect, val radius: Float, val onClick: (() -> Unit)?)

/** [body] is shown with [bodyArgs] (the credit numbers); an empty list = a plain string. */
data class TourStep(val target: TourTarget, val title: StringResource, val body: StringResource, val bodyArgs: List<Any> = emptyList())

/** The tour on screen: its steps with a target that showed up, and the one shown now. */
data class TourRun(val id: TourId, val userId: String, val steps: List<TourStep>, val index: Int = 0) {
    val step: TourStep get() = steps[index]
    val isLast: Boolean get() = index == steps.lastIndex
    val isSingle: Boolean get() = steps.size == 1
}

/**
 * The new-user tour (design "Panduan pengguna baru", owner, 5 Nov 2026): a coach-mark layer over the real screens.
 * Signed-in users only; once per account and device; at most [MAX_STEPS_PER_SESSION] steps per app launch (the rest
 * waits for the next one); a flag is written however the tour is closed. No server call: works offline.
 */
class TourController(
    private val preferences: PreferencesRepository,
    /** The signed-in user's id, null for a guest (guests never see a tour). */
    private val currentUser: () -> String?,
    private val credits: CreditsStore,
    /** Waits are shortened in tests. */
    private val startDelayMillis: Long = START_DELAY_MILLIS,
) {
    /** Registered targets, kept by [Modifier.tourTarget] while they are on screen (main thread only). */
    val targets = mutableStateMapOf<TourTarget, TargetInfo>()

    var run: TourRun? by mutableStateOf(null)
        private set

    /** Something that must not be covered is open (the notification permission sheet, the update dialog). */
    var blocked: Boolean by mutableStateOf(false)

    /** "Ulangi panduan" asked for the Home tour, whatever the trips. Home consumes it. */
    var replayHome: Boolean by mutableStateOf(false)
        private set

    private var shownThisSession = 0

    fun userId(): String? = currentUser()

    /** "Jatah gratis %1$d kredit tiap hari, satu pencarian baru pakai %2$d kredit": the live numbers, else the last ones seen. */
    suspend fun creditNumbers(): Pair<Int, Int>? {
        credits.state.value?.let { c ->
            val numbers = c.dailyFreeLimit to c.costs.search
            preferences.putString(CREDITS_KEY, "${numbers.first},${numbers.second}")
            return numbers
        }
        val saved = preferences.getString(CREDITS_KEY)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return null
        return if (saved.size == 2) saved[0] to saved[1] else null
    }

    suspend fun isDone(id: TourId): Boolean {
        val user = userId() ?: return true
        return preferences.getString(flagKey(user, id)) != null
    }

    /**
     * Starts [id] with the [steps] whose target appears within [TARGET_WAIT_MILLIS]; nothing when it is done, another
     * tour is on screen, the screen is blocked, or this launch has no room left. No step left = not shown, not recorded.
     */
    suspend fun start(id: TourId, steps: List<TourStep>, force: Boolean = false) {
        val user = userId() ?: return
        if (run != null || steps.isEmpty()) return
        if (!force && preferences.getString(flagKey(user, id)) != null) return
        delay(startDelayMillis)
        var waited = 0L
        while ((blocked || steps.any { it.target !in targets }) && waited < TARGET_WAIT_MILLIS * startDelayMillis / START_DELAY_MILLIS) {
            delay(POLL_MILLIS)
            waited += POLL_MILLIS
        }
        if (blocked || run != null) return
        val shown = steps.filter { it.target in targets }
        if (shown.isEmpty()) return
        if (shownThisSession + shown.size > MAX_STEPS_PER_SESSION) return
        shownThisSession += shown.size
        run = TourRun(id, user, shown)
    }

    fun next() {
        val r = run ?: return
        if (r.isLast) finish() else run = r.copy(index = r.index + 1)
    }

    /** Selesai, Oke, Lewati, Back, Esc, or the target left the screen: the whole tour is recorded as seen. */
    fun finish() {
        val r = run ?: return
        run = null
        flagsToWrite += flagKey(r.userId, r.id)
    }

    /** The spotlight was tapped: the element does its job and the tour ends. */
    fun tapSpotlight() {
        val action = run?.let { targets[it.step.target]?.onClick }
        finish()
        action?.invoke()
    }

    /** Flags written by [persistFlags] (a coroutine of the overlay), so [finish] can be called from a click. */
    internal val flagsToWrite = mutableListOf<String>()

    internal suspend fun persistFlags() {
        while (flagsToWrite.isNotEmpty()) preferences.putString(flagsToWrite.removeAt(0), "1")
    }

    /** "Ulangi panduan": forget this user's tours on this device, then Home starts its tour. */
    suspend fun replay() {
        val user = userId() ?: return
        run = null
        TourId.entries.forEach { preferences.putString(flagKey(user, it), null) }
        shownThisSession = 0
        replayHome = true
    }

    fun replayConsumed() {
        replayHome = false
    }

    private fun flagKey(user: String, id: TourId) = "tour.$user.${id.key}"

    companion object {
        const val MAX_STEPS_PER_SESSION = 5
        const val START_DELAY_MILLIS = 600L
        const val TARGET_WAIT_MILLIS = 3_000L
        private const val POLL_MILLIS = 100L
        private const val CREDITS_KEY = "tour.credits"
    }
}
