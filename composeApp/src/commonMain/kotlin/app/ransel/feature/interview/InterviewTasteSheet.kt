package app.ransel.feature.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.explore.label
import app.ransel.shared.domain.InterviewTaste
import app.ransel.shared.domain.TASTE_NOTES_MAX
import app.ransel.shared.domain.canSaveFromSheet
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.IrisTextField
import org.jetbrains.compose.resources.StringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_notes
import ransel.composeapp.generated.resources.explore_notes_hint
import ransel.composeapp.generated.resources.explore_optional
import ransel.composeapp.generated.resources.explore_price_high
import ransel.composeapp.generated.resources.explore_price_high_example
import ransel.composeapp.generated.resources.explore_price_low
import ransel.composeapp.generated.resources.explore_price_low_example
import ransel.composeapp.generated.resources.explore_price_mid
import ransel.composeapp.generated.resources.explore_price_mid_example
import ransel.composeapp.generated.resources.explore_price_relative
import ransel.composeapp.generated.resources.explore_price_title
import ransel.composeapp.generated.resources.explore_special_title
import ransel.composeapp.generated.resources.interview_price_from_budget
import ransel.composeapp.generated.resources.interview_save
import ransel.composeapp.generated.resources.interview_taste_general
import ransel.composeapp.generated.resources.interview_taste_sheet_counter
import ransel.composeapp.generated.resources.interview_taste_sheet_error
import ransel.composeapp.generated.resources.interview_taste_sheet_title
import ransel.composeapp.generated.resources.interview_taste_style

/** The "$" signs of a price range, the same as the Explore form. */
internal fun PriceLevel.signs(): String = when (this) {
    PriceLevel.LOW -> "$"
    PriceLevel.MEDIUM -> "$$"
    PriceLevel.HIGH -> "$$$"
}

internal val PriceLevel.labelRes: StringResource
    get() = when (this) {
        PriceLevel.LOW -> Res.string.explore_price_low
        PriceLevel.MEDIUM -> Res.string.explore_price_mid
        PriceLevel.HIGH -> Res.string.explore_price_high
    }

private val PriceLevel.exampleRes: StringResource
    get() = when (this) {
        PriceLevel.LOW -> Res.string.explore_price_low_example
        PriceLevel.MEDIUM -> Res.string.explore_price_mid_example
        PriceLevel.HIGH -> Res.string.explore_price_high_example
    }

/**
 * "Ubah seleramu" (spec "Wawancara Buat trip", section 5), opened from the Jelajah summary: the interests (the destination's
 * own and the general ones, at least one), the style, the price range with examples and an optional note of at most 500
 * characters. It changes this trip's Seleramu only. [draft] is applied by "Simpan" and dropped when the sheet is closed.
 */
@Composable
internal fun TasteSheet(
    draft: InterviewTaste,
    khas: KhasState,
    destination: String,
    onInterest: (Interest) -> Unit,
    onExtra: (String) -> Unit,
    onStyle: (TravelStyle) -> Unit,
    onPrice: (PriceLevel) -> Unit,
    onNotes: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    /** The range that is selected while the draft has none: Sedang, or the trip budget's ([priceFromBudget] adds "Dari budget trip"). */
    defaultPrice: PriceLevel = PriceLevel.MEDIUM,
    priceFromBudget: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(Res.string.interview_taste_sheet_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
            )
            TasteFields(
                draft = draft,
                khas = khas,
                destination = destination,
                onInterest = onInterest,
                onExtra = onExtra,
                onStyle = onStyle,
                onPrice = onPrice,
                onNotes = onNotes,
                defaultPrice = defaultPrice,
                priceFromBudget = priceFromBudget,
            )
        }
        InterviewBottomBar(
            primaryLabel = stringResource(Res.string.interview_save),
            primaryEnabled = draft.canSaveFromSheet(),
            onPrimary = onSave,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Interests, style, price and notes: the sheet and the Edit-trip full screen share this. */
@Composable
internal fun TasteFields(
    draft: InterviewTaste,
    khas: KhasState,
    destination: String,
    onInterest: (Interest) -> Unit,
    onExtra: (String) -> Unit,
    onStyle: (TravelStyle) -> Unit,
    onPrice: (PriceLevel) -> Unit,
    onNotes: (String) -> Unit,
    defaultPrice: PriceLevel = PriceLevel.MEDIUM,
    priceFromBudget: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (khas is KhasState.Loaded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.explore_special_title, destination), style = MaterialTheme.typography.labelLarge)
                ChipFlow { khas.interests.forEach { InterestChip(it, it in draft.extraInterests, { onExtra(it) }, special = true) } }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.interview_taste_general), style = MaterialTheme.typography.labelLarge)
            ChipFlow { Interest.entries.forEach { InterestChip(stringResource(it.label), it in draft.interests, { onInterest(it) }) } }
            if (!draft.canSaveFromSheet()) {
                Text(stringResource(Res.string.interview_taste_sheet_error), style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.interview_taste_style), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TravelStyle.entries.forEach { style ->
                    StyleCard(stringResource(style.labelRes), stringResource(style.hintRes), draft.style == style, { onStyle(style) }, Modifier.weight(1f))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.explore_price_title), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.explore_price_relative), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            if (priceFromBudget && draft.priceLevel == null) {
                Text(
                    stringResource(Res.string.interview_price_from_budget),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = scheme.tertiary,
                )
            }
            // "Sedang" is the start of a trip without a budget; the card of the effective range is the selected one
            PriceLevel.entries.forEach { level ->
                ChoiceRow(
                    symbol = priceSymbol(level),
                    label = level.signs() + " " + stringResource(level.labelRes),
                    effect = stringResource(level.exampleRes),
                    selected = (draft.priceLevel ?: defaultPrice) == level,
                    onClick = { onPrice(level) },
                )
            }
        }
        IrisTextField(
            label = stringResource(Res.string.explore_notes),
            labelSuffix = stringResource(Res.string.explore_optional),
            value = draft.notes,
            onValueChange = onNotes,
            placeholder = stringResource(Res.string.explore_notes_hint),
            singleLine = false,
        )
        if (draft.notes.isNotEmpty()) {
            Text(
                stringResource(Res.string.interview_taste_sheet_counter, draft.notes.length, TASTE_NOTES_MAX),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun priceSymbol(level: PriceLevel): String = app.ransel.ui.symbols.Symbols.payments
