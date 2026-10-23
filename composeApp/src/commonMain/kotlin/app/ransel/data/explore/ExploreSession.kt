package app.ransel.data.explore

import app.ransel.data.remote.ApiJson
import app.ransel.data.repository.PreferencesRepository
import app.ransel.shared.dto.RecommendationItem
import app.ransel.shared.model.Category
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * What the user typed in the Explore form (design `PreferensiAI`). The trip supplies the dates (display only);
 * [destination] is where this search looks, the trip's own destination unless the user changed it.
 */
@Serializable
data class ExploreForm(
    val tripId: String,
    val tripTitle: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val priceLevel: PriceLevel = PriceLevel.MEDIUM,
    val interests: List<Interest> = emptyList(),
    /** Ticked "Khas <destination>" chips, sent as `extraInterests`. */
    val extraInterests: List<String> = emptyList(),
    val style: TravelStyle = TravelStyle.SANTAI,
    val notes: String = "",
)

@Serializable
data class ExploreCard(val category: Category, val item: RecommendationItem)

/** One swipe the user made; kept so Undo can reverse exactly it. */
@Serializable
data class ExploreSwipe(val placeId: String, val liked: Boolean, val itineraryItemId: String? = null)

/**
 * The unfinished search ("Lanjutkan" card, per device, never synced): the form, the cards received so far
 * and what was done with them. [cards] is empty while the first request is still being made.
 */
@Serializable
data class ExploreSession(
    val form: ExploreForm,
    val cards: List<ExploreCard> = emptyList(),
    val swipes: List<ExploreSwipe> = emptyList(),
    /** No more batches to fetch (the background request came back empty). */
    val exhausted: Boolean = false,
    /**
     * Set when the search became a voting round (a trip with 2+ members): the cards live on the server and are swiped
     * on the vote screen; the session only keeps the form so the next batch can be asked for the same round.
     */
    val roundId: String? = null,
) {
    val position: Int get() = swipes.size
    val saved: Int get() = swipes.count { it.liked }
    val finished: Boolean get() = cards.isNotEmpty() && position >= cards.size && exhausted
    val seenIds: List<String> get() = swipes.map { it.placeId }
}

/**
 * Persists the unfinished searches in the per-device preferences: one per trip, so a user can have several at once
 * (the Explore picker shows each inside its trip), and tells the tab bar whether to show the dot.
 */
class ExploreSessionStore(private val preferences: PreferencesRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _sessions = MutableStateFlow<Map<String, ExploreSession>>(emptyMap())

    /** Unfinished searches by trip id. */
    val sessions: StateFlow<Map<String, ExploreSession>> = _sessions.asStateFlow()

    init {
        scope.launch {
            val saved = runCatching { preferences.getString(KEY)?.let(::decode) }.getOrNull() ?: legacySession()
            // whatever was saved while this was loading is newer than what was on disk
            if (!saved.isNullOrEmpty()) _sessions.update { saved + it }
        }
    }

    fun session(tripId: String?): ExploreSession? = tripId?.let { _sessions.value[it] }

    /** The form of the session that was just dropped ("Ubah preferensi"), so the next form starts with the same answers. */
    private var lastForm: ExploreForm? = null
    fun takeLastForm(tripId: String): ExploreForm? = lastForm?.takeIf { it.tripId == tripId }.also { lastForm = null }

    /** Keeps [session] as the unfinished search of its trip, replacing an earlier one of the same trip. */
    suspend fun save(session: ExploreSession) {
        _sessions.update { it + (session.form.tripId to session) }
        persist()
    }

    /** Drops the unfinished search of [tripId] (finished, discarded, or changed preferences). */
    suspend fun clear(tripId: String) {
        _sessions.value[tripId]?.let { lastForm = it.form }
        _sessions.update { it - tripId }
        persist()
    }

    private suspend fun persist() {
        val all = _sessions.value
        preferences.putString(KEY, if (all.isEmpty()) null else ApiJson.encodeToString(SERIALIZER, all))
    }

    private fun decode(raw: String): Map<String, ExploreSession> = ApiJson.decodeFromString(SERIALIZER, raw)

    /** Before sessions were per trip there was a single one under another key: carry it over once. */
    private suspend fun legacySession(): Map<String, ExploreSession>? {
        val raw = runCatching { preferences.getString(LEGACY_KEY) }.getOrNull() ?: return null
        val old = runCatching { ApiJson.decodeFromString(ExploreSession.serializer(), raw) }.getOrNull()
        preferences.putString(LEGACY_KEY, null)
        return old?.let { mapOf(it.form.tripId to it) }
    }

    private companion object {
        const val KEY = "explore_sessions"
        const val LEGACY_KEY = "explore_session"
        val SERIALIZER = MapSerializer(String.serializer(), ExploreSession.serializer())
    }
}
