package app.ransel.feature.currency

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.currency_search_placeholder

/** Pick a currency: search field + radio list (design "Akun: pilih mata uang"). */
@Composable
fun CurrencySheet(
    title: String,
    subtitle: String?,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val names = SupportedCurrencies.associate { it.code to stringResource(it.name) }
    val shown = SupportedCurrencies.filter {
        query.isBlank() ||
            it.code.contains(query.trim(), ignoreCase = true) ||
            (names[it.code] ?: "").contains(query.trim(), ignoreCase = true)
    }
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(IrisShape.md)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.search, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(Res.string.currency_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                shown.forEachIndexed { index, option ->
                    val isSelected = option.code == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(isSelected, role = Role.RadioButton) {
                                onSelect(option.code)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .width(40.dp)
                                .heightIn(min = 32.dp)
                                .clip(IrisShape.sm)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                option.symbol,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.code,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            )
                            Text(
                                names[option.code].orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RadioMark(isSelected)
                    }
                    if (index < shown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** 20 dp radio circle used by the IRIS sheets (selected = primary ring with dot). */
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier.size(20.dp).border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    }
}
