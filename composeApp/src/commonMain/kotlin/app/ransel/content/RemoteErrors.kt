package app.ransel.content

import androidx.compose.runtime.Composable
import app.ransel.shared.dto.ErrorTextDto
import org.jetbrains.compose.resources.DrawableResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.allDrawableResources

/**
 * Karung's copy for one `ErrorCode` in the language the app shows; a null field keeps the screen's own. `{cost}`,
 * `{available}`, ... are filled from the error's `details` (a placeholder without a value is left out).
 */
data class ErrorCopy(
    val title: String?,
    val message: String?,
    val button: String?,
    val illustration: DrawableResource?,
)

@Composable
fun remoteErrorCopy(code: String?, details: Map<String, String>? = null): ErrorCopy? {
    val text = code?.let { LiveContent.current.error(it) } ?: return null
    return text.copyFor(contentLocale(), details.orEmpty())
}

internal fun ErrorTextDto.copyFor(locale: String, details: Map<String, String>): ErrorCopy {
    fun pick(id: String?, en: String?) = (if (locale == "id") id else en)?.takeIf { it.isNotBlank() }?.let { fillDetails(it, details) }
    return ErrorCopy(
        title = pick(titleId, titleEn),
        message = pick(messageId, messageEn),
        button = pick(buttonId, buttonEn),
        illustration = illustration?.let { Res.allDrawableResources[it] },
    )
}

private val NAMED = Regex("""\{([A-Za-z]+)\}""")

internal fun fillDetails(text: String, details: Map<String, String>): String =
    NAMED.replace(text) { m -> details[m.groupValues[1]].orEmpty() }.replace(Regex(" {2,}"), " ").trim()
