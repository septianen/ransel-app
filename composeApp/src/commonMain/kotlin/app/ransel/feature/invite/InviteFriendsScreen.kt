package app.ransel.feature.invite

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.data.repository.TripRepository
import app.ransel.shared.domain.whatsappShareLink
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.ic_itinerary_empty
import ransel.composeapp.generated.resources.invite_copy
import ransel.composeapp.generated.resources.invite_failed
import ransel.composeapp.generated.resources.invite_friends_add_name
import ransel.composeapp.generated.resources.invite_friends_body
import ransel.composeapp.generated.resources.invite_friends_copied
import ransel.composeapp.generated.resources.invite_friends_later
import ransel.composeapp.generated.resources.invite_friends_link_label
import ransel.composeapp.generated.resources.invite_friends_title
import ransel.composeapp.generated.resources.invite_friends_whatsapp
import ransel.composeapp.generated.resources.invite_share_text
import ransel.composeapp.generated.resources.state_retry

/**
 * Design `AjakTeman`, right after "Buat trip" (signed in): an Editor link to share on WhatsApp or copy, or friends
 * added by name. "Nanti saja" and close go to the new trip's Ringkasan. Uses the invite sheet's ViewModel, which
 * waits for a trip made offline to reach the server before asking for the link.
 */
@Composable
fun InviteFriendsScreen(
    tripId: String,
    onDone: () -> Unit,
    onAddByName: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteSheetViewModel = koinViewModel(key = "invite-friends-$tripId") { parametersOf(tripId) },
    trips: TripRepository = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val trip by remember(tripId) { trips.observeTrip(tripId) }.collectAsState(null)
    val title = trip?.title.orEmpty()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val invite = state.invite
    val shareText = invite?.let { stringResource(Res.string.invite_share_text, title, it.url) }

    InviteFriendsContent(
        tripTitle = title,
        link = invite?.url,
        loading = state.loading,
        onCopy = { invite?.let { clipboard.setText(AnnotatedString(it.url)) } },
        onWhatsApp = { shareText?.let { uriHandler.openUri(whatsappShareLink(it)) } },
        onAddByName = onAddByName,
        onRetry = viewModel::retry,
        onDone = onDone,
        modifier = modifier,
    )
}

/** [link] null with [loading] false = the invite could not be made (offline): retry, or leave with "Nanti saja". */
@Composable
fun InviteFriendsContent(
    tripTitle: String,
    link: String?,
    loading: Boolean,
    onCopy: () -> Unit,
    onWhatsApp: () -> Unit,
    onAddByName: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Box(
            Modifier.padding(horizontal = 4.dp).size(48.dp).clip(CircleShape)
                .clickable(role = SemanticsRole.Button, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.close, contentDescription = stringResource(Res.string.action_close)) }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // capybara-world-map
                Image(painterResource(Res.drawable.ic_itinerary_empty), contentDescription = null, modifier = Modifier.size(200.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.invite_friends_title, tripTitle),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(Res.string.invite_friends_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                LinkBox(link, loading, copied, onCopy = { onCopy(); copied = true }, onRetry = onRetry)
            }
        }

        // Bottom bar, raised like the design's shadowed footer.
        Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val ready = link != null
                Button(onClick = onWhatsApp, enabled = ready, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(48.dp)) {
                    Symbol(Symbols.share, size = 22.dp, tint = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(Res.string.invite_friends_whatsapp), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                }
                // Members by name live on the server too, so this waits for the trip to get there like the link.
                OutlinedButton(onClick = onAddByName, enabled = ready, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(48.dp)) {
                    Symbol(Symbols.personAdd, size = 22.dp)
                    Text(stringResource(Res.string.invite_friends_add_name), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onDone, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.invite_friends_later), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun LinkBox(link: String?, loading: Boolean, copied: Boolean, onCopy: () -> Unit, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, IrisShape.lg)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.invite_friends_link_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                link != null -> Text(
                    if (copied) stringResource(Res.string.invite_friends_copied) else link.removePrefix("https://"),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                loading -> CircularProgressIndicator(Modifier.padding(vertical = 2.dp).size(20.dp), strokeWidth = 2.dp)
                else -> Text(
                    stringResource(Res.string.invite_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (link == null && !loading) {
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.state_retry)) }
        } else {
            FilledTonalButton(onClick = onCopy, enabled = link != null, modifier = Modifier.height(36.dp)) {
                Symbol(Symbols.contentCopy, size = 18.dp)
                Text(stringResource(Res.string.invite_copy), Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Preview
@Composable
private fun InviteFriendsPreview() = IrisPreview {
    InviteFriendsContent("Bali 4 hari", "https://ransel.mietek.tech/j/BALI-7K2QM9XA", loading = false, {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun InviteFriendsLoadingPreview() = IrisPreview {
    InviteFriendsContent("Bali 4 hari", null, loading = true, {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun InviteFriendsOfflinePreview() = IrisPreview {
    InviteFriendsContent("Bali 4 hari", null, loading = false, {}, {}, {}, {}, {})
}
