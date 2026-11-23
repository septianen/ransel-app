package app.ransel.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.data.repository.AppLanguage
import app.ransel.feature.currency.RadioMark
import app.ransel.shared.api.ErrorCode
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.ThemeMode
import org.jetbrains.compose.resources.StringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.account_name_failed
import ransel.composeapp.generated.resources.account_name_field
import ransel.composeapp.generated.resources.account_name_required
import ransel.composeapp.generated.resources.account_name_sheet_hint
import ransel.composeapp.generated.resources.account_name_sheet_title
import ransel.composeapp.generated.resources.account_photo_name
import ransel.composeapp.generated.resources.action_save
import ransel.composeapp.generated.resources.language_english
import ransel.composeapp.generated.resources.language_indonesian
import ransel.composeapp.generated.resources.language_sheet_hint
import ransel.composeapp.generated.resources.language_sheet_title
import ransel.composeapp.generated.resources.language_system
import ransel.composeapp.generated.resources.theme_dark
import ransel.composeapp.generated.resources.theme_light
import ransel.composeapp.generated.resources.theme_sheet_hint
import ransel.composeapp.generated.resources.theme_sheet_system_note
import ransel.composeapp.generated.resources.theme_sheet_title
import ransel.composeapp.generated.resources.theme_system

internal const val NAME_MAX = 60

internal val ThemeMode.label: StringResource
    get() = when (this) {
        ThemeMode.SYSTEM -> Res.string.theme_system
        ThemeMode.LIGHT -> Res.string.theme_light
        ThemeMode.DARK -> Res.string.theme_dark
    }

internal val AppLanguage.label: StringResource
    get() = when (this) {
        AppLanguage.SYSTEM -> Res.string.language_system
        AppLanguage.INDONESIAN -> Res.string.language_indonesian
        AppLanguage.ENGLISH -> Res.string.language_english
    }

@Composable
internal fun ThemeSheet(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Text(stringResource(Res.string.theme_sheet_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.theme_sheet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(IrisShape.lg)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            IrisShape.lg,
                        )
                        .selectable(isSelected, role = Role.RadioButton) { onSelect(mode) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemePreview(mode)
                    Row(
                        Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioMark(isSelected)
                        Text(
                            stringResource(mode.label),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Symbol(Symbols.info, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(Res.string.theme_sheet_system_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Miniature of a screen in the chosen theme (light, dark, or split for "follow system"). */
@Composable
private fun ThemePreview(mode: ThemeMode) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(IrisShape.md)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, IrisShape.md),
    ) {
        if (mode != ThemeMode.DARK) PreviewHalf(light = true, modifier = Modifier.weight(1f))
        if (mode != ThemeMode.LIGHT) PreviewHalf(light = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PreviewHalf(light: Boolean, modifier: Modifier) {
    val colors = if (light) app.ransel.ui.theme.IrisLightColors else app.ransel.ui.theme.IrisDarkColors
    Column(
        modifier.background(colors.surface).heightIn(min = 96.dp).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = 36.dp, height = 8.dp).clip(IrisShape.xs).background(colors.onSurface))
        Box(Modifier.fillMaxWidth().heightIn(min = 22.dp).clip(IrisShape.xs).background(colors.primaryContainer))
        Box(Modifier.size(width = 28.dp, height = 6.dp).clip(IrisShape.xs).background(colors.outlineVariant))
    }
}

@Composable
internal fun PhotoAndNameSheet(
    name: String,
    avatarUrl: String?,
    photoBusy: Boolean,
    nameState: NameState,
    onChangePhoto: () -> Unit,
    onSaveName: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Text(stringResource(Res.string.account_photo_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.account_name_sheet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PhotoNameHeader(name, avatarUrl, photoBusy, onChangePhoto)
        NameSheetFields(name, nameState, onSaveName)
    }
}

@Composable
internal fun NameSheet(current: String, state: NameState, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Text(stringResource(Res.string.account_name_sheet_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.account_name_sheet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NameSheetFields(current, state, onSave)
    }
}

@Composable
private fun NameSheetFields(current: String, state: NameState, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(current) }
    IrisTextField(
        label = stringResource(Res.string.account_name_field),
        value = value,
        onValueChange = { value = it.take(NAME_MAX) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        error = when (state.problem) {
            null -> null
            ErrorCode.VALIDATION_FAILED -> stringResource(Res.string.account_name_required)
            else -> stringResource(Res.string.account_name_failed)
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
    )
    LoadingButton(
        onClick = { onSave(value) },
        loading = state.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
    ) { Text(stringResource(Res.string.action_save), style = MaterialTheme.typography.titleMedium) }
}

@Composable
internal fun LanguageSheet(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Text(stringResource(Res.string.language_sheet_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.language_sheet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.padding(top = 8.dp).selectableGroup()) {
            AppLanguage.entries.forEachIndexed { index, language ->
                val isSelected = language == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(isSelected, role = Role.RadioButton) { onSelect(language) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(language.label),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    RadioMark(isSelected)
                }
                if (index < AppLanguage.entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
