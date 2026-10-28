package app.ransel.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.have_account
import ransel.composeapp.generated.resources.sign_in_action
import ransel.composeapp.generated.resources.ic_expenses_empty
import ransel.composeapp.generated.resources.ic_packing_empty
import ransel.composeapp.generated.resources.ic_itinerary_empty
import ransel.composeapp.generated.resources.onboarding_body_1
import ransel.composeapp.generated.resources.onboarding_body_2
import ransel.composeapp.generated.resources.onboarding_body_3
import ransel.composeapp.generated.resources.onboarding_next
import ransel.composeapp.generated.resources.onboarding_page
import ransel.composeapp.generated.resources.onboarding_previous
import ransel.composeapp.generated.resources.onboarding_skip
import ransel.composeapp.generated.resources.onboarding_start
import ransel.composeapp.generated.resources.onboarding_title_1
import ransel.composeapp.generated.resources.onboarding_title_2
import ransel.composeapp.generated.resources.onboarding_title_3

private data class OnboardingPage(val title: StringResource, val body: StringResource, val illustration: DrawableResource)

// The group story (design OnboardingGrup1–3): plan together, split costs, who brings what.
private val Pages = listOf(
    OnboardingPage(Res.string.onboarding_title_1, Res.string.onboarding_body_1, Res.drawable.ic_itinerary_empty), // capybara-world-map
    OnboardingPage(Res.string.onboarding_title_2, Res.string.onboarding_body_2, Res.drawable.ic_expenses_empty), // capybara-counting-coins
    OnboardingPage(Res.string.onboarding_title_3, Res.string.onboarding_body_3, Res.drawable.ic_packing_empty), // capybara-opening-backpack
)

/**
 * Three introduction pages on first launch (design OnboardingGrup1–3). "Skip" and "Get started" go straight into
 * the app; the last page also offers "Sudah punya akun? Masuk".
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier, onSignIn: (() -> Unit)? = null) {
    val pager = rememberPagerState(pageCount = { Pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == Pages.lastIndex

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // header: back on pages 2–3, skip on pages 1–2
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable {
                        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(
                        Symbols.arrowBack,
                        contentDescription = stringResource(Res.string.onboarding_previous),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Box(Modifier.weight(1f))
            if (!last) {
                Text(
                    stringResource(Res.string.onboarding_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onFinish)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Image(painterResource(Pages[index].illustration), contentDescription = null, modifier = Modifier.size(280.dp))
                Text(
                    stringResource(Pages[index].title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(Pages[index].body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val pageDescription = stringResource(Res.string.onboarding_page, pager.currentPage + 1, Pages.size)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp).semantics { contentDescription = pageDescription },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            repeat(Pages.size) { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier
                        .size(height = 8.dp, width = if (active) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
        Button(
            onClick = {
                if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).heightIn(min = 48.dp),
        ) {
            Text(
                stringResource(if (last) Res.string.onboarding_start else Res.string.onboarding_next),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (last && onSignIn != null) {
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                app.ransel.feature.auth.AuthFooter(
                    prefix = stringResource(Res.string.have_account),
                    action = stringResource(Res.string.sign_in_action),
                    onClick = onSignIn,
                )
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingPreview() = IrisPreview {
    Box(Modifier.size(width = 360.dp, height = 600.dp)) { OnboardingScreen(onFinish = {}) }
}
