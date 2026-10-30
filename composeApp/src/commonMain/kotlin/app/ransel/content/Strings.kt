package app.ransel.content

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.content_locale
import org.jetbrains.compose.resources.getString as bundledString
import org.jetbrains.compose.resources.stringResource as bundledStringResource

/*
 * Drop-in replacements for Compose Resources' `stringResource` and `getString`: the text Karung published for the
 * key in the language the app shows, else the bundled one. Every file in composeApp imports these instead of the
 * library's (composeApp/CLAUDE.md "Remote content"). Plurals and string arrays are not remote content.
 */

@Composable
fun stringResource(resource: StringResource): String {
    val bundled = bundledStringResource(resource)
    return LiveContent.current.text(contentLocale(), resource.key) ?: bundled
}

@Composable
fun stringResource(resource: StringResource, vararg formatArgs: Any): String {
    val bundled = bundledStringResource(resource, *formatArgs)
    val remote = LiveContent.current.text(contentLocale(), resource.key)
    return remote?.let { formatContent(it, formatArgs) } ?: bundled
}

/** "id" or "en": the language Compose Resources picked for the bundled strings, so remote copy matches it. */
@Composable
internal fun contentLocale(): String = bundledStringResource(Res.string.content_locale)

suspend fun getString(resource: StringResource): String {
    val content = LiveContent.current
    if (content.isEmpty) return bundledString(resource)
    return content.text(bundledString(Res.string.content_locale), resource.key) ?: bundledString(resource)
}

suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
    val content = LiveContent.current
    val remote = if (content.isEmpty) null else content.text(bundledString(Res.string.content_locale), resource.key)
    return remote?.let { formatContent(it, formatArgs) } ?: bundledString(resource, *formatArgs)
}

private val FORMAT_ARG = Regex("""%(\d+)\$[ds]""")

/** Fills `%1$s` / `%2$d` the way Compose Resources does; Karung only publishes texts with the bundled placeholders. */
internal fun formatContent(text: String, args: Array<out Any>): String =
    FORMAT_ARG.replace(text) { m -> args.getOrNull(m.groupValues[1].toInt() - 1)?.toString() ?: m.value }
