package app.ransel.feature.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.ransel.shared.domain.minutesFromWib
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.math.abs
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.overseas_auto
import ransel.composeapp.generated.resources.overseas_currency_body
import ransel.composeapp.generated.resources.overseas_currency_title
import ransel.composeapp.generated.resources.overseas_time_title
import ransel.composeapp.generated.resources.zone_ahead
import ransel.composeapp.generated.resources.zone_behind
import ransel.composeapp.generated.resources.zone_same
import ransel.composeapp.generated.resources.zone_local_note

/** "Tokyo" from "Asia/Tokyo", "Ho Chi Minh" from "Asia/Ho_Chi_Minh". */
fun zoneCity(timeZone: String): String = timeZone.substringAfterLast('/').replace('_', ' ')

/** "GMT+9", "GMT+5:30" now in [timeZone]; null for a zone the device does not know. */
fun gmtLabel(timeZone: String): String? = runCatching {
    val seconds = TimeZone.of(timeZone).offsetAt(Clock.System.now()).totalSeconds
    val sign = if (seconds < 0) "-" else "+"
    val h = abs(seconds) / 3600
    val m = abs(seconds) % 3600 / 60
    "GMT$sign$h" + if (m > 0) ":" + m.toString().padStart(2, '0') else ""
}.getOrNull()

/** "2 jam lebih cepat dari WIB", "sama dengan WIB", "1,5 jam lebih lambat dari WIB"; null for an unknown zone. */
@Composable
fun zoneDifferenceText(timeZone: String): String? {
    val minutes = minutesFromWib(timeZone, Clock.System.now()) ?: return null
    if (minutes == 0) return stringResource(Res.string.zone_same)
    val hours = abs(minutes) / 60.0
    val text = if (abs(minutes) % 60 == 0) (abs(minutes) / 60).toString() else hours.toString().replace('.', ',')
    return stringResource(if (minutes > 0) Res.string.zone_ahead else Res.string.zone_behind, text)
}

/** Design "BuatTripLuarNegeri": what the destination sets on its own (local currency, local time). */
@Composable
fun OverseasCard(baseCurrency: String, localCurrency: String?, timeZone: String?, modifier: Modifier = Modifier) {
    if (localCurrency == null && timeZone == null) return
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.tertiaryContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Symbol(Symbols.autoAwesome, size = 16.dp, filled = true, tint = scheme.onTertiaryContainer)
            Text(stringResource(Res.string.overseas_auto), style = MaterialTheme.typography.labelMedium, color = scheme.onTertiaryContainer)
        }
        if (localCurrency != null && !localCurrency.equals(baseCurrency, ignoreCase = true)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // an icon, like the clock below: a code without a symbol ("LAK") would wrap in a 24 dp column
                Symbol(Symbols.currencyExchange, size = 22.dp, tint = scheme.onTertiaryContainer)
                Column {
                    Text(stringResource(Res.string.overseas_currency_title, localCurrency), style = MaterialTheme.typography.titleSmall, color = scheme.onTertiaryContainer)
                    Text(stringResource(Res.string.overseas_currency_body, localCurrency, baseCurrency), style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer)
                }
            }
        }
        if (timeZone != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Symbol(Symbols.schedule, size = 22.dp, tint = scheme.onTertiaryContainer)
                Column {
                    Text(
                        stringResource(Res.string.overseas_time_title, zoneCity(timeZone), gmtLabel(timeZone) ?: timeZone),
                        style = MaterialTheme.typography.titleSmall, color = scheme.onTertiaryContainer,
                    )
                    Text(
                        listOfNotNull(zoneDifferenceText(timeZone), stringResource(Res.string.zone_local_note)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

/** The zone a trip's times and days are read in: its own, else the device's. */
fun app.ransel.shared.dto.TripDto.zone(): TimeZone =
    timeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
