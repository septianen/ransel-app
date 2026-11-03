package app.ransel.content

import androidx.compose.runtime.Composable
import app.ransel.shared.api.ApiErrorText
import org.jetbrains.compose.resources.DrawableResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.allDrawableResources
import kotlin.concurrent.Volatile

/**
 * Karung's copy for one `ErrorCode`, as the server sent it with the error (`ApiError.text`: already in the app's
 * language, `{cost}` and the other details filled in). A null field keeps the screen's own copy.
 */
data class ErrorCopy(
    val title: String?,
    val message: String?,
    val button: String?,
    val illustration: DrawableResource?,
)

/**
 * The last copy the server sent per error code. Screens only get the code (`FullScreenState(errorCode = ...)`), so
 * `RanselApi` records the copy here when the error arrives and the screen looks it up when it shows that error.
 * Plain memory, not snapshot state: it is written from network threads and must never be read before it exists.
 * Client-only codes (offline, timeout, location) never have server copy and keep the bundled one.
 */
object ServerErrorCopy {
    @Volatile private var copies: Map<String, ApiErrorText> = emptyMap()

    /** A null [text] forgets the code: Karung went back to the app's own copy. */
    fun record(code: String, text: ApiErrorText?) {
        val known = copies[code]
        if (known == text) return
        copies = if (text == null) copies - code else copies + (code to text)
    }

    fun forCode(code: String): ApiErrorText? = copies[code]

    internal fun clear() {
        copies = emptyMap()
    }
}

/** Karung's copy for [code] from the last error response with it; null = the screen's own copy. */
@Composable
fun remoteErrorCopy(code: String?): ErrorCopy? =
    code?.let(ServerErrorCopy::forCode)?.toCopy()

internal fun ApiErrorText.toCopy(): ErrorCopy = ErrorCopy(
    title = title?.takeIf { it.isNotBlank() },
    message = message?.takeIf { it.isNotBlank() },
    button = button?.takeIf { it.isNotBlank() },
    illustration = illustration?.let { Res.allDrawableResources[it] },
)
