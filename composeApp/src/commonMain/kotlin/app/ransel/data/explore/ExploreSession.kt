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
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** What the user typed in the Explore form (design `PreferensiAI`). The trip supplies destination and dates (display only). */
@Serializable
data class ExploreForm(
    val tripId: String,
    val tripTitle: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val focusArea: String = "",
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
) {
    val position: Int get() = swipes.size
    val saved: Int get() = swipes.count { it.liked }
    val finished: Boolean get() = cards.isNotEmpty() && position >= cards.size && exhausted
    val seenIds: List<String> get() = swipes.map { it.placeId }
}

/** Persists the session in the per-device preferences and tells the tab bar whether to show the dot. */
class ExploreSessionStore(private val preferences: PreferencesRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _session = MutableStateFlow<ExploreSession?>(null)
    val session: StateFlow<ExploreSession?> = _session.asStateFlow()

    init {
        scope.launch {
            val raw = runCatching { preferences.getString(KEY) }.getOrNull() ?: return@launch
            runCatching { ApiJson.decodeFromString(ExploreSession.serializer(), raw) }.getOrNull()
                ?.let { if (_session.value == null) _session.value = it }
        }
    }

    /** The form of the session that was just dropped ("Ubah preferensi"), so the next form starts with the same answers. */
    private var lastForm: ExploreForm? = null
    val lastTripId: String? get() = lastForm?.tripId
    fun takeLastForm(tripId: String): ExploreForm? = lastForm?.takeIf { it.tripId == tripId }.also { lastForm = null }

    suspend fun save(session: ExploreSession?) {
        if (session == null) _session.value?.form?.let { lastForm = it }
        _session.value = session
        preferences.putString(KEY, session?.let { ApiJson.encodeToString(ExploreSession.serializer(), it) })
    }

    private companion object {
        const val KEY = "explore_session"
    }
}
