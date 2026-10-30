package app.ransel.feature.trips

import androidx.compose.runtime.Composable
import app.ransel.content.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.trip_countdown_day
import ransel.composeapp.generated.resources.trip_countdown_days
import ransel.composeapp.generated.resources.trip_countdown_months
import ransel.composeapp.generated.resources.trip_countdown_no_dates
import ransel.composeapp.generated.resources.trip_starts_today

/** "38 hari lagi" / "5 bulan lagi" / "Mulai hari ini"; "Tanggal belum pasti" for a trip without dates (null). */
@Composable
fun countdownLabel(daysUntilStart: Int?): String = when {
    daysUntilStart == null -> stringResource(Res.string.trip_countdown_no_dates)
    daysUntilStart <= 0 -> stringResource(Res.string.trip_starts_today)
    daysUntilStart == 1 -> stringResource(Res.string.trip_countdown_day)
    daysUntilStart < 60 -> stringResource(Res.string.trip_countdown_days, daysUntilStart)
    else -> stringResource(Res.string.trip_countdown_months, daysUntilStart / 30)
}
