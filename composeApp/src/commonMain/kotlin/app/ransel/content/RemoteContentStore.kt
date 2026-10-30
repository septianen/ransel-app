package app.ransel.content

import app.ransel.data.analytics.CrashReporter
import app.ransel.data.analytics.NoCrashReporter
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PreferencesRepository
import app.ransel.shared.dto.ContentDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Keeps [LiveContent] filled with what Karung published (admin-spec stage E). The last version is saved on the device
 * (Room preferences on mobile, localStorage on the web), so the app shows it offline and right after start; [refresh]
 * asks `GET /content` for anything newer. Without a saved copy or a connection the bundled copy is all there is.
 */
class RemoteContentStore(
    private val api: RanselApi,
    private val preferences: PreferencesRepository,
    private val crashes: CrashReporter = NoCrashReporter,
    private val clock: () -> Instant = { Clock.System.now() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val loaded: Job = scope.launch {
        // A saved copy that no longer parses is dropped; the next refresh downloads it again.
        val saved = runCatching { preferences.getString(KEY)?.let { json.decodeFromString<ContentDto>(it) } }
            .onFailure(crashes::record).getOrNull()
        if (saved != null && saved.version > LiveContent.current.version) LiveContent.current = RemoteContent(saved)
    }
    private var lastAsked: Instant? = null

    /** On start and when the app comes back to the front; at most every 5 minutes (the server's cache time). */
    fun refresh() {
        val now = clock()
        if (lastAsked?.let { now - it < MIN_INTERVAL } == true) return
        lastAsked = now
        scope.launch {
            loaded.join()
            val known = LiveContent.current.version.takeIf { it > 0 }
            val fresh = (api.content(known) as? ApiResult.Success)?.value ?: return@launch
            LiveContent.current = RemoteContent(fresh)
            preferences.putString(KEY, json.encodeToString(ContentDto.serializer(), fresh))
        }
    }

    private companion object {
        const val KEY = "remote_content"
        val MIN_INTERVAL = 5.minutes
    }
}
