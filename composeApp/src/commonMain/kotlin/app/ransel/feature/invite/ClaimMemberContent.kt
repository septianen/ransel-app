package app.ransel.feature.invite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Instant
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.claim_body
import ransel.composeapp.generated.resources.claim_error
import ransel.composeapp.generated.resources.claim_none
import ransel.composeapp.generated.resources.claim_title

/**
 * "Kamu yang mana?": shown right after joining a trip that has people added by name. Picking a name moves that
 * person's expenses and shares to the new member's account; "Bukan salah satunya" skips it.
 */
@Composable
fun ClaimMemberContent(
    candidates: List<TripMemberDto>,
    claiming: Boolean,
    failed: Boolean,
    onClaim: (TripMemberDto) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(Res.string.claim_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(Res.string.claim_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            candidates.forEach { member ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(IrisShape.lg)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, IrisShape.lg)
                        .clickable(enabled = !claiming, role = SemanticsRole.Button) { onClaim(member) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MemberAvatar(member.displayName, member.memberId, isGuest = true)
                    Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (failed) {
                Text(
                    stringResource(Res.string.claim_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        TextButton(
            onClick = onSkip,
            enabled = !claiming,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp).height(48.dp),
        ) {
            Text(stringResource(Res.string.claim_none), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val previewGuests = listOf(
    TripMemberDto("t", "m1", null, "Rina", null, Role.VIEWER, Instant.parse("2026-10-01T00:00:00Z")),
    TripMemberDto("t", "m2", null, "Budi", null, Role.VIEWER, Instant.parse("2026-10-02T00:00:00Z")),
)

@Preview
@Composable
private fun ClaimMemberPreview() = IrisPreview {
    ClaimMemberContent(previewGuests, claiming = false, failed = false, onClaim = {}, onSkip = {})
}

@Preview
@Composable
private fun ClaimMemberFailedPreview() = IrisPreview {
    ClaimMemberContent(previewGuests, claiming = false, failed = true, onClaim = {}, onSkip = {})
}
