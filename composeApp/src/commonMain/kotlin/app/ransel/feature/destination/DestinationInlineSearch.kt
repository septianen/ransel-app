package app.ransel.feature.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.platform.rememberLocationProvider
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.destination_around
import ransel.composeapp.generated.resources.destination_clear

/**
 * The destination of Layar 1 ("Mau ke mana?"): a real input field with its suggestions on the same screen, under it (spec
 * 1572903, section 1), not the full-screen picker. Before typing: "Gunakan lokasi saat ini", up to 3 recent and the popular
 * ones; from 2 characters: up to 5 suggestions. A pick fills the field ([selectedName]) with a clear button.
 */
@Composable
fun InlineDestinationSearch(
    selectedName: String,
    pinFilled: Boolean,
    placeholder: String,
    onPicked: (DestinationChoice) -> Unit,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DestinationPickerViewModel = koinViewModel(key = "inline-destination")
    val state by viewModel.state.collectAsState()
    val location = rememberLocationProvider()
    val around = stringResource(Res.string.destination_around, "%s")
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val selected = selectedName.isNotEmpty()
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(viewModel) {
        viewModel.picked.collect {
            viewModel.onQuery("")
            onPicked(it)
        }
    }
    // the field has the focus while nothing is chosen (spec 1a)
    LaunchedEffect(selected) { if (!selected) runCatching { focus.requestFocus() } }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(IrisShape.md)
                .border(if (focused && !selected) 2.dp else 1.dp, if (focused && !selected) scheme.primary else scheme.outline, IrisShape.md)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(
                if (selected) Symbols.locationOn else Symbols.search,
                size = 20.dp,
                filled = selected && pinFilled,
                tint = if (selected && pinFilled) scheme.primary else scheme.onSurfaceVariant,
            )
            if (selected) {
                Text(
                    selectedName,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(Modifier.weight(1f)) {
                    if (state.query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    BasicTextField(
                        value = state.query,
                        onValueChange = viewModel::onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
                    )
                }
            }
            if (selected || state.query.isNotEmpty()) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) {
                        viewModel.onQuery("")
                        if (selected) onCleared()
                    },
                    contentAlignment = Alignment.Center,
                ) { Symbol(Symbols.close, size = 18.dp, contentDescription = stringResource(Res.string.destination_clear)) }
            }
        }
        if (!selected) {
            if (state.resolving || state.searching) {
                Box(Modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            DestinationResults(
                state = state,
                onPick = viewModel::pick,
                onUseTyped = viewModel::useTyped,
                onRecent = viewModel::pickRecent,
                onPopular = viewModel::pickPopular,
                onArea = { viewModel.pickArea(it, around) },
                onUseLocation = { viewModel.useLocation(location.request) },
                onOpenSettings = location.openSettings,
                maxRecents = 3,
                maxSuggestions = 5,
            )
        }
    }
}
