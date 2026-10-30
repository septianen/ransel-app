package app.ransel.feature.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.dto.TripMemberDto
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.format.formatMoney
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.receipt_extras_spread
import ransel.composeapp.generated.resources.receipt_match
import ransel.composeapp.generated.resources.receipt_mismatch
import ransel.composeapp.generated.resources.receipt_per_person
import ransel.composeapp.generated.resources.receipt_per_person_extras

/**
 * The receipt lines of a "Per item" split (design "HasilStruk"): each line with its amount and, when [picking], the
 * avatars to tick who shares it. After a scan and when editing a per-item expense.
 */
@Composable
fun ItemSplitLines(split: ItemSplit, currency: String, members: List<TripMemberDto>, picking: Boolean) {
    val scheme = MaterialTheme.colorScheme
    split.lines.forEachIndexed { index, line ->
        Column(
            Modifier.fillMaxWidth().clip(IrisShape.md).border(1.dp, scheme.outlineVariant, IrisShape.md).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatMoney(line.amountMinor, currency), style = MaterialTheme.typography.titleSmall)
            }
            if (picking && index == split.extrasIndex) {
                Text(stringResource(Res.string.receipt_extras_spread), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (picking && index != split.extrasIndex) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    split.peopleOf(index).forEach { id ->
                        val on = id in split.sharersOf(index)
                        // someone who left stays on the line, dimmed, and cannot be taken off
                        val gone = id in split.locked
                        Box(
                            Modifier.clip(CircleShape)
                                .alpha(if (gone) 0.5f else 1f)
                                .border(2.dp, if (on) scheme.primary else scheme.outlineVariant, CircleShape)
                                .then(if (gone) Modifier else Modifier.clickable(role = Role.Checkbox) { split.toggle(index, id) })
                                .padding(2.dp),
                        ) {
                            MemberAvatar(members.firstOrNull { it.memberId == id }?.displayName ?: "?", id, size = 32.dp, isGuest = !on)
                        }
                    }
                }
            }
        }
    }
}

/** "Bagian tiap orang" and the check that the lines add up to [total]; [name] = how a person is called. */
@Composable
fun ItemSplitSummary(
    shares: List<PersonShare>,
    difference: Long,
    total: Long,
    currency: String,
    members: List<TripMemberDto>,
    name: (String) -> String,
) {
    val scheme = MaterialTheme.colorScheme
    if (shares.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.receipt_per_person), style = MaterialTheme.typography.labelLarge)
            shares.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemberAvatar(members.firstOrNull { it.memberId == p.memberId }?.displayName ?: "?", p.memberId, size = 24.dp)
                    Text(name(p.memberId), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatMoney(p.totalMinor, currency), style = MaterialTheme.typography.titleSmall)
                        if (p.extrasMinor > 0) {
                            Text(
                                stringResource(Res.string.receipt_per_person_extras, formatMoney(p.extrasMinor, currency)),
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.md).background(if (difference == 0L) scheme.primaryContainer else scheme.errorContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(if (difference == 0L) Symbols.checkCircle else Symbols.error, size = 20.dp, filled = true, tint = if (difference == 0L) scheme.onPrimaryContainer else scheme.error)
        Text(
            if (difference == 0L) stringResource(Res.string.receipt_match) else stringResource(Res.string.receipt_mismatch, formatMoney(kotlin.math.abs(difference), currency)),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = if (difference == 0L) scheme.onPrimaryContainer else scheme.onErrorContainer,
        )
        Text(formatMoney(total, currency), style = MaterialTheme.typography.titleSmall)
    }
}
