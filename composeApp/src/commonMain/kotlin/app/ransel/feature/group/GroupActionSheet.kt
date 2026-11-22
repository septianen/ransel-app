package app.ransel.feature.group

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.album_add
import ransel.composeapp.generated.resources.album_add_hint
import ransel.composeapp.generated.resources.group_action_document
import ransel.composeapp.generated.resources.group_action_document_hint
import ransel.composeapp.generated.resources.credits_package_credits
import ransel.composeapp.generated.resources.group_action_explore_free
import ransel.composeapp.generated.resources.group_action_explore_solo
import ransel.composeapp.generated.resources.group_action_explore_solo_hint
import ransel.composeapp.generated.resources.group_action_explore_vote
import ransel.composeapp.generated.resources.group_action_explore_vote_hint
import ransel.composeapp.generated.resources.group_action_explore_vote_no_destination
import ransel.composeapp.generated.resources.group_action_invite
import ransel.composeapp.generated.resources.group_action_invite_hint
import ransel.composeapp.generated.resources.group_action_poll
import ransel.composeapp.generated.resources.group_action_poll_hint
import ransel.composeapp.generated.resources.group_action_soon
import ransel.composeapp.generated.resources.group_sheet_title

/**
 * What the + button on the Group tab offers (design: Buat polling, Ajak anggota, Unggah dokumen, Tambah album). Documents arrive in a
 * later step: that row shows "Segera hadir" and does nothing. Only the owner can invite.
 *
 * [onExplore] (plan fab-cari-tempat-voting and Rockstar's spec, 22 Nov 2026): the trip-first place search for this trip,
 * after "Buat polling"; null hides it ([canSearchPlacesFromSheet]). On a solo trip the swipe saves to "Belum dijadwalkan",
 * so [exploreSolo] uses copy that does not promise a vote. [explorePrice] is the trailing chip; [exploreBlockedReason] dims
 * the row and replaces its hint (a trip without a destination).
 */
@Composable
fun GroupActionSheet(
    canInvite: Boolean,
    onPoll: (() -> Unit)?,
    onInvite: () -> Unit,
    onDismiss: () -> Unit,
    onDocument: (() -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    onExplore: (() -> Unit)? = null,
    exploreSolo: Boolean = false,
    explorePrice: ExplorePrice? = null,
    exploreBlocked: Boolean = false,
) {
    AdaptiveSheet(onDismiss = onDismiss) {
        GroupActionContent(canInvite, onPoll, onInvite, onDocument, onAlbum, onExplore, exploreSolo, explorePrice, exploreBlocked)
    }
}

/** The price chip of the Explore row: a number of credits, or free because today's free credits pay for it. */
sealed interface ExplorePrice {
    data class Credits(val amount: Int) : ExplorePrice
    data object FreeToday : ExplorePrice
}

/**
 * The chip for a new search, from the server's numbers (never hard-coded): none while unknown, when the search is free, or
 * when an unfinished session resumes without a charge.
 */
fun explorePrice(costs: app.ransel.shared.dto.CreditsDto?, resumes: Boolean): ExplorePrice? {
    if (costs == null || resumes) return null
    val cost = costs.costs.search
    return when {
        cost <= 0 -> null
        costs.dailyFreeLeft >= cost -> ExplorePrice.FreeToday
        else -> ExplorePrice.Credits(cost)
    }
}

/**
 * Who sees "Cari tempat dengan AI": an OWNER or EDITOR (viewers cannot start a search or a round, voting spec) on a trip
 * that has not ended (the server answers 422 TRIP_ENDED). Same rule as the Explore trip picker.
 */
fun canSearchPlacesFromSheet(role: app.ransel.shared.model.Role?, phase: app.ransel.feature.trips.TripPhase): Boolean =
    role?.atLeast(app.ransel.shared.model.Role.EDITOR) == true && phase != app.ransel.feature.trips.TripPhase.FINISHED

@Composable
private fun GroupActionContent(
    canInvite: Boolean,
    onPoll: (() -> Unit)?,
    onInvite: () -> Unit,
    onDocument: (() -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    onExplore: (() -> Unit)? = null,
    exploreSolo: Boolean = false,
    explorePrice: ExplorePrice? = null,
    exploreBlocked: Boolean = false,
) {
    val accents = IrisTheme.accents
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.group_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (onPoll != null) ActionRow(Symbols.checklist, accents.magenta, Res.string.group_action_poll, Res.string.group_action_poll_hint, soon = false, onClick = onPoll)
        // after "Buat polling" (both are about voting; on a solo trip that row is hidden, so this one comes first). The AI
        // action gets the secondary container tile: the spec keeps secondary for AI-origin elements.
        if (onExplore != null) {
            ActionRow(
                Symbols.autoAwesome,
                MaterialTheme.colorScheme.secondaryContainer,
                if (exploreSolo) Res.string.group_action_explore_solo else Res.string.group_action_explore_vote,
                when {
                    exploreBlocked -> Res.string.group_action_explore_vote_no_destination
                    exploreSolo -> Res.string.group_action_explore_solo_hint
                    else -> Res.string.group_action_explore_vote_hint
                },
                soon = false,
                onClick = onExplore,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                dimmed = exploreBlocked,
                trailing = if (exploreBlocked) null else when (explorePrice) {
                    is ExplorePrice.Credits -> stringResource(Res.string.credits_package_credits, explorePrice.amount)
                    ExplorePrice.FreeToday -> stringResource(Res.string.group_action_explore_free)
                    null -> null
                },
            )
        }
        if (canInvite) {
            ActionRow(Symbols.personAdd, accents.green, Res.string.group_action_invite, Res.string.group_action_invite_hint, soon = false, onClick = onInvite)
        }
        // editors add documents; everyone else sees them on the Group tab
        if (onDocument != null) {
            ActionRow(Symbols.cloudUpload, accents.blue, Res.string.group_action_document, Res.string.group_action_document_hint, soon = false, onClick = onDocument)
        }
        // and albums (design "TambahDiRingkasanAlbum": the + sheet, no button of its own)
        if (onAlbum != null) {
            ActionRow(Symbols.addPhotoAlternate, accents.purple, Res.string.album_add, Res.string.album_add_hint, soon = false, onClick = onAlbum)
        }
    }
}

@Composable
private fun ActionRow(
    symbol: String,
    tile: androidx.compose.ui.graphics.Color,
    title: StringResource,
    hint: StringResource,
    soon: Boolean,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    /** Shown but not usable: 38 % and no click (the hint says why). */
    dimmed: Boolean = false,
    /** A small chip at the end (a price). */
    trailing: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(IrisShape.md)
            .clickable(enabled = !soon && !dimmed, role = Role.Button, onClick = onClick)
            .graphicsLayer(alpha = if (dimmed) 0.38f else 1f)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(IrisShape.md).background(if (soon) MaterialTheme.colorScheme.surfaceVariant else tile),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(symbol, tint = if (soon) MaterialTheme.colorScheme.onSurfaceVariant else iconTint ?: IrisTheme.accents.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = if (soon) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null && !soon) {
            Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (soon) {
            Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(
                    stringResource(Res.string.group_action_soon),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun GroupActionSheetPreview() = IrisPreview {
    Box(Modifier.padding(20.dp)) { GroupActionContent(canInvite = true, onPoll = {}, onInvite = {}, onDocument = {}, onAlbum = {}, onExplore = {}, explorePrice = ExplorePrice.Credits(3)) }
}

@Preview
@Composable
private fun GroupActionSheetSoloPreview() = IrisPreview {
    Box(Modifier.padding(20.dp)) { GroupActionContent(canInvite = true, onPoll = null, onInvite = {}, onDocument = {}, onAlbum = {}, onExplore = {}, exploreSolo = true, explorePrice = ExplorePrice.FreeToday) }
}

@Preview
@Composable
private fun GroupActionSheetNoDestinationPreview() = IrisPreview {
    Box(Modifier.padding(20.dp)) { GroupActionContent(canInvite = true, onPoll = {}, onInvite = {}, onExplore = {}, exploreBlocked = true) }
}
