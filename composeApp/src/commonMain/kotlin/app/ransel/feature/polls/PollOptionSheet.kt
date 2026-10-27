package app.ransel.feature.polls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ButtonDefaults
import app.ransel.shared.dto.LinkPreviewDto
import kotlinx.coroutines.launch
import ransel.composeapp.generated.resources.poll_link_error
import ransel.composeapp.generated.resources.poll_link_fetch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ransel.feature.tripform.ThousandsVisualTransformation
import app.ransel.feature.tripform.TripDateRangePicker
import app.ransel.shared.domain.validatePollOption
import app.ransel.shared.dto.PollOptionDto
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollPriceUnit
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.feature.destination.DestinationField
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.format.amountToInput
import app.ransel.shared.domain.currencyDecimals
import app.ransel.ui.format.formatMoney
import app.ransel.ui.format.parseAmountMinor
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.format.sanitizeAmountInput
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.newId
import app.ransel.util.today
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.poll_budget_range
import ransel.composeapp.generated.resources.poll_option_add_title
import ransel.composeapp.generated.resources.poll_option_budget_max
import ransel.composeapp.generated.resources.poll_option_budget_min
import ransel.composeapp.generated.resources.poll_option_dates
import ransel.composeapp.generated.resources.poll_option_dates_pick
import ransel.composeapp.generated.resources.poll_option_edit_title
import ransel.composeapp.generated.resources.poll_option_edit_warning
import ransel.composeapp.generated.resources.poll_option_error_invalid
import ransel.composeapp.generated.resources.poll_option_error_required
import ransel.composeapp.generated.resources.poll_option_label
import ransel.composeapp.generated.resources.poll_option_link
import ransel.composeapp.generated.resources.poll_option_optional
import ransel.composeapp.generated.resources.poll_option_price_night
import ransel.composeapp.generated.resources.poll_option_price_person
import ransel.composeapp.generated.resources.poll_option_save
import ransel.composeapp.generated.resources.poll_price_night
import ransel.composeapp.generated.resources.poll_price_person
import app.ransel.ui.components.LoadingButton

/** Options that can come from a pasted link. */
private val LINK_KINDS = setOf(PollKind.STAY, PollKind.FOOD, PollKind.PLACE)

/** The fields of one option, as typed. Which of them count depends on the poll kind. */
data class OptionDraft(
    val label: String = "",
    val price: String = "",
    val url: String = "",
    /** From a pasted link; sent with the option so the poll can show the picture. */
    val imageUrl: String = "",
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val min: String = "",
    val max: String = "",
)

private fun PollOptionDto.toDraft(currency: String) = OptionDraft(
    label = label,
    price = priceMinor?.let { amountToInput(it, currency) }.orEmpty(),
    url = url.orEmpty(),
    imageUrl = imageUrl.orEmpty(),
    start = startDate,
    end = endDate,
    min = minMinor?.let { amountToInput(it, currency) }.orEmpty(),
    max = maxMinor?.let { amountToInput(it, currency) }.orEmpty(),
)

/** The option to send, and the fields that are wrong (field name → true). A DATE or BUDGET option names itself. */
fun OptionDraft.toInput(kind: PollKind, currency: String, id: String, keep: PollOptionDto? = null): Pair<PollOptionInput, Set<String>> {
    val problems = mutableSetOf<String>()
    fun amount(text: String, field: String): Long? =
        if (text.isBlank()) null else (parseAmountMinor(text, currency) ?: run { problems += field; null })
    val priceMinor = amount(price, "priceMinor")
    val minMinor = amount(min, "minMinor")
    val maxMinor = amount(max, "maxMinor")
    val input = PollOptionInput(
        id = id,
        label = label.trim(),
        startDate = start.takeIf { kind == PollKind.DATE },
        endDate = end.takeIf { kind == PollKind.DATE },
        placeId = keep?.placeId,
        url = url.trim().ifEmpty { null }.takeIf { kind in LINK_KINDS },
        imageUrl = imageUrl.ifEmpty { null }.takeIf { kind in LINK_KINDS } ?: keep?.imageUrl,
        priceMinor = priceMinor.takeIf { kind == PollKind.STAY || kind == PollKind.FOOD },
        priceUnit = when (kind) {
            PollKind.STAY -> PollPriceUnit.NIGHT
            PollKind.FOOD -> PollPriceUnit.PERSON
            else -> null
        }.takeIf { priceMinor != null },
        minMinor = minMinor.takeIf { kind == PollKind.BUDGET },
        maxMinor = maxMinor.takeIf { kind == PollKind.BUDGET },
    )
    problems += validatePollOption(kind, input).keys
    return input to problems
}

/**
 * Add or edit one option of a poll. Dates and budgets are a range, a stay has a price per night and a link, a place to
 * eat has a price per person, everything else is just a name.
 */
@Composable
fun PollOptionSheet(
    kind: PollKind,
    currency: String,
    initial: PollOptionDto?,
    tripStart: LocalDate?,
    tripEnd: LocalDate?,
    onSave: (PollOptionInput) -> Unit,
    onDismiss: () -> Unit,
    /** Reads a pasted link (title and picture); null = the link cannot be read. Null parameter = no "fetch" button. */
    onPreview: (suspend (String) -> LinkPreviewDto?)? = null,
) {
    var fetching by remember { mutableStateOf(false) }
    var fetchFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(initial?.toDraft(currency) ?: OptionDraft()) }
    var wrong by remember { mutableStateOf(emptySet<String>()) }
    var pickRange by remember { mutableStateOf(false) }
    val dates = rememberDateFormatter()
    val optionId = remember { initial?.id ?: newId() }

    fun save() {
        val (input, problems) = draft.toInput(kind, currency, optionId, initial)
        if (problems.isEmpty()) onSave(input) else wrong = problems
    }

    val required = stringResource(Res.string.poll_option_error_required)
    val invalid = stringResource(Res.string.poll_option_error_invalid)
    fun error(field: String, isRequired: Boolean = false): String? =
        if (field in wrong) (if (isRequired) required else invalid) else null

    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(if (initial == null) Res.string.poll_option_add_title else Res.string.poll_option_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (initial != null) {
                Text(
                    stringResource(Res.string.poll_option_edit_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (kind) {
                PollKind.DATE -> {
                    Text(stringResource(Res.string.poll_option_dates), style = MaterialTheme.typography.labelLarge)
                    PickerButton(
                        symbol = Symbols.calendarMonth,
                        text = draft.start?.let { s -> dates.range(s, draft.end ?: s) } ?: stringResource(Res.string.poll_option_dates_pick),
                        onClick = { pickRange = true },
                    )
                    if ("startDate" in wrong || "endDate" in wrong) {
                        Text(required, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    IrisTextField(
                        label = stringResource(Res.string.poll_option_label),
                        labelSuffix = stringResource(Res.string.poll_option_optional),
                        value = draft.label,
                        onValueChange = { draft = draft.copy(label = it); wrong = wrong - "label" },
                        error = error("label"),
                    )
                }
                PollKind.BUDGET -> {
                    MoneyField(stringResource(Res.string.poll_option_budget_min), draft.min, currency, error("minMinor", true)) { draft = draft.copy(min = it); wrong = wrong - "minMinor" }
                    MoneyField(stringResource(Res.string.poll_option_budget_max), draft.max, currency, error("maxMinor", true)) { draft = draft.copy(max = it); wrong = wrong - "maxMinor" }
                }
                else -> {
                    if (kind in LINK_KINDS) {
                        IrisTextField(
                            label = stringResource(Res.string.poll_option_link),
                            labelSuffix = stringResource(Res.string.poll_option_optional),
                            value = draft.url,
                            onValueChange = { draft = draft.copy(url = it); wrong = wrong - "url"; fetchFailed = false },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            error = error("url"),
                        )
                        if (onPreview != null && draft.url.isNotBlank()) {
                            LoadingButton(
                                onClick = {
                                    scope.launch {
                                        fetching = true
                                        val page = onPreview(draft.url.trim())
                                        fetching = false
                                        if (page == null) {
                                            fetchFailed = true
                                        } else {
                                            fetchFailed = false
                                            draft = draft.copy(
                                                label = draft.label.ifBlank { page.title.orEmpty() },
                                                imageUrl = page.imageUrl ?: draft.imageUrl,
                                                url = page.url,
                                            )
                                        }
                                    }
                                },
                                loading = fetching,
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(),
                            ) { Text(stringResource(Res.string.poll_link_fetch)) }
                        }
                        if (fetchFailed) {
                            Text(stringResource(Res.string.poll_link_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (kind == PollKind.DESTINATION) {
                        DestinationField(
                            label = stringResource(Res.string.poll_option_label),
                            value = draft.label,
                            onPicked = { draft = draft.copy(label = it.name); wrong = wrong - "label" },
                            picked = draft.label.isNotEmpty(),
                            error = error("label", true),
                        )
                    } else {
                        IrisTextField(
                            label = stringResource(Res.string.poll_option_label),
                            value = draft.label,
                            onValueChange = { draft = draft.copy(label = it); wrong = wrong - "label" },
                            error = error("label", true),
                        )
                    }
                    if (kind == PollKind.STAY || kind == PollKind.FOOD) {
                        MoneyField(
                            label = stringResource(if (kind == PollKind.STAY) Res.string.poll_option_price_night else Res.string.poll_option_price_person),
                            value = draft.price,
                            currency = currency,
                            error = error("priceMinor"),
                            optional = true,
                        ) { draft = draft.copy(price = it); wrong = wrong - "priceMinor" }
                    }
                }
            }
            LoadingButton(onClick = ::save, loading = false, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(Res.string.poll_option_save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (pickRange) {
        val from = draft.start ?: tripStart ?: today()
        TripDateRangePicker(
            start = from,
            end = draft.end ?: tripEnd ?: from,
            onConfirm = { s, e ->
                draft = draft.copy(start = s, end = e)
                wrong = wrong - "startDate" - "endDate"
                pickRange = false
            },
            onDismiss = { pickRange = false },
            earliest = app.ransel.util.today(),
        )
    }
}

@Composable
internal fun MoneyField(label: String, value: String, currency: String, error: String?, optional: Boolean = false, onChange: (String) -> Unit) {
    IrisTextField(
        label = label,
        labelSuffix = if (optional) stringResource(Res.string.poll_option_optional) else null,
        value = value,
        onValueChange = { onChange(sanitizeAmountInput(it, currencyDecimals(currency))) },
        prefix = currency,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = ThousandsVisualTransformation,
        error = error,
    )
}

@Composable
internal fun PickerButton(symbol: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), IrisShape.md)
            .clip(IrisShape.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(symbol, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
    }
}

/** Text under an option: dates, a price, or a budget range. Null when there is nothing to add to the name. */
@Composable
fun pollOptionSubtitle(option: PollOptionDto, currency: String): String? {
    val dates = rememberDateFormatter()
    val start = option.startDate
    val end = option.endDate
    return when {
        start != null && end != null -> dates.range(start, end)
        option.minMinor != null && option.maxMinor != null ->
            stringResource(
                Res.string.poll_budget_range,
                formatMoney(option.minMinor!!, currency),
                formatMoney(option.maxMinor!!, currency),
            )
        option.priceMinor != null -> stringResource(
            if (option.priceUnit == PollPriceUnit.NIGHT) Res.string.poll_price_night else Res.string.poll_price_person,
            formatMoney(option.priceMinor!!, currency),
        )
        else -> null
    }
}

