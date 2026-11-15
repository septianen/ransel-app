package app.ransel.feature.itinerary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.domain.MAX_ACTIVITY_LINKS
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.activity_link_add
import ransel.composeapp.generated.resources.activity_link_count
import ransel.composeapp.generated.resources.activity_link_duplicate
import ransel.composeapp.generated.resources.activity_link_hint
import ransel.composeapp.generated.resources.activity_link_invalid
import ransel.composeapp.generated.resources.activity_link_label
import ransel.composeapp.generated.resources.activity_link_max
import ransel.composeapp.generated.resources.activity_link_remove
import ransel.composeapp.generated.resources.activity_links_title
import ransel.composeapp.generated.resources.trip_optional

/**
 * The "Link" section of the activity form (spec "Banyak link di aktivitas"): one row per link, the automatic label of a
 * valid row under it, "Tambah link" with "n dari 5" (replaced by "Maksimal 5 link" at the limit). A row shows its
 * problem once the user has left it, so typing "booking.c" does not flash an error.
 */
@Composable
fun ActivityLinksSection(
    draft: LinkDraft,
    onChange: (key: Int, text: String) -> Unit,
    onRemove: (key: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    var previousCount by remember { mutableIntStateOf(draft.rows.size) }
    // a new row takes the focus, so "Tambah link" leads straight to typing
    LaunchedEffect(draft.rows.size) {
        if (draft.rows.size > previousCount) runCatching { requesters[draft.rows.last().key]?.requestFocus() }
        previousCount = draft.rows.size
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(stringResource(Res.string.activity_links_title), style = MaterialTheme.typography.labelLarge)
            Text(
                " " + stringResource(Res.string.trip_optional),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        }
        val problems = draft.problems
        draft.rows.forEachIndexed { index, row ->
            key(row.key) {
                var focused by remember { mutableStateOf(false) }
                val problem = problems[row.key]?.takeIf { !focused }
                val label = draft.labelOf(row)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IrisTextField(
                        label = "",
                        value = row.text,
                        onValueChange = { onChange(row.key, it) },
                        modifier = Modifier.onFocusChanged { focused = it.hasFocus },
                        leadingSymbol = Symbols.link,
                        placeholder = stringResource(Res.string.activity_link_hint),
                        error = when (problem) {
                            LinkProblem.INVALID -> stringResource(Res.string.activity_link_invalid)
                            LinkProblem.DUPLICATE -> stringResource(Res.string.activity_link_duplicate)
                            null -> null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = if (index == draft.rows.lastIndex) ImeAction.Done else ImeAction.Next,
                        ),
                        focusRequester = requesters.getOrPut(row.key) { FocusRequester() },
                        trailing = {
                            // 48 dp touch area; the last remaining row is emptied, not removed
                            Box(
                                Modifier.size(48.dp).clickable(role = Role.Button) { onRemove(row.key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Symbol(
                                    Symbols.close,
                                    size = 20.dp,
                                    tint = scheme.onSurfaceVariant,
                                    contentDescription = stringResource(Res.string.activity_link_remove),
                                )
                            }
                        },
                    )
                    if (label != null && problem == null) {
                        Text(
                            stringResource(Res.string.activity_link_label, label),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        if (draft.atMax) {
            Text(
                stringResource(Res.string.activity_link_max),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAdd, enabled = draft.canAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                    Symbol(Symbols.add, size = 20.dp)
                    Text(
                        stringResource(Res.string.activity_link_add),
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Box(Modifier.weight(1f))
                Text(
                    stringResource(Res.string.activity_link_count, draft.rows.size, MAX_ACTIVITY_LINKS),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ActivityLinksSectionPreview() = IrisPreview {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ActivityLinksSection(LinkDraft(), { _, _ -> }, {}, {})
        ActivityLinksSection(
            LinkDraft.of(listOf("https://www.booking.com/hotel/id/x", "https://www.instagram.com/hotelkuta")),
            { _, _ -> }, {}, {},
        )
        ActivityLinksSection(
            LinkDraft.of(listOf("https://a.example/1", "https://b.example/2", "https://c.example/3", "https://d.example/4", "https://e.example/5")),
            { _, _ -> }, {}, {},
        )
    }
}
