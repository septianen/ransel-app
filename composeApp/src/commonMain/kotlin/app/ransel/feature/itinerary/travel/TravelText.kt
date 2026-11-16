package app.ransel.feature.itinerary.travel

import androidx.compose.runtime.Composable
import app.ransel.content.stringResource
import app.ransel.shared.domain.LegKind
import app.ransel.shared.domain.TravelDistance
import app.ransel.shared.domain.TravelLeg
import app.ransel.shared.domain.hoursMinutes
import app.ransel.shared.domain.travelDistanceOf
import app.ransel.shared.model.MainTravelMode
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.StringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.travel_capsule
import ransel.composeapp.generated.resources.travel_capsule_far
import ransel.composeapp.generated.resources.travel_capsule_far_warning
import ransel.composeapp.generated.resources.travel_capsule_transit
import ransel.composeapp.generated.resources.travel_decimal_separator
import ransel.composeapp.generated.resources.travel_distance_km
import ransel.composeapp.generated.resources.travel_distance_m
import ransel.composeapp.generated.resources.travel_gap_hour
import ransel.composeapp.generated.resources.travel_gap_hours
import ransel.composeapp.generated.resources.travel_gap_hours_minutes
import ransel.composeapp.generated.resources.travel_gap_minutes
import ransel.composeapp.generated.resources.travel_hours
import ransel.composeapp.generated.resources.travel_hours_minutes
import ransel.composeapp.generated.resources.travel_main_car
import ransel.composeapp.generated.resources.travel_main_motorcycle
import ransel.composeapp.generated.resources.travel_main_public
import ransel.composeapp.generated.resources.travel_minutes
import ransel.composeapp.generated.resources.travel_mode_car
import ransel.composeapp.generated.resources.travel_mode_motorcycle
import ransel.composeapp.generated.resources.travel_mode_transit
import ransel.composeapp.generated.resources.travel_mode_walk

/* The words of a travel leg (spec "Perjalanan antar tempat V1"); the numbers come from shared/domain/TravelFormat.kt. */

/** "35 mnt", "1 j", "1 j 10 mnt" (no "±": the caller puts it). */
@Composable
fun travelMinutesText(minutes: Int): String {
    val parts = hoursMinutes(minutes)
    return when {
        parts.hours == 0 -> stringResource(Res.string.travel_minutes, parts.minutes)
        parts.minutes == 0 -> stringResource(Res.string.travel_hours, parts.hours)
        else -> stringResource(Res.string.travel_hours_minutes, parts.hours, parts.minutes)
    }
}

/** "600 m", "5,2 km", "18 km". */
@Composable
fun travelDistanceText(roadKm: Double): String = when (val distance = travelDistanceOf(roadKm)) {
    is TravelDistance.Meters -> stringResource(Res.string.travel_distance_m, distance.meters)
    is TravelDistance.Km -> {
        val separator = stringResource(Res.string.travel_decimal_separator)
        stringResource(Res.string.travel_distance_km, "${distance.whole}$separator${distance.decimal}")
    }
    is TravelDistance.WholeKm -> stringResource(Res.string.travel_distance_km, distance.km.toString())
}

/** The gap in the warning: "15 mnt", "1 jam", "1 jam 30 mnt" (the hour is written out here, spec). */
@Composable
fun travelGapText(minutes: Int): String {
    val parts = hoursMinutes(minutes.coerceAtLeast(0))
    return when {
        parts.hours == 0 -> stringResource(Res.string.travel_gap_minutes, parts.minutes)
        parts.minutes == 0 && parts.hours == 1 -> stringResource(Res.string.travel_gap_hour)
        parts.minutes == 0 -> stringResource(Res.string.travel_gap_hours, parts.hours)
        else -> stringResource(Res.string.travel_gap_hours_minutes, parts.hours, parts.minutes)
    }
}

/** "Jalan kaki", "Motor/ojol", "Mobil/taksi", "Umum"; empty for a leg too far to name a mode. */
@Composable
fun legModeLabel(kind: LegKind): String = when (kind) {
    LegKind.WALK -> stringResource(Res.string.travel_mode_walk)
    LegKind.MOTORCYCLE -> stringResource(Res.string.travel_mode_motorcycle)
    LegKind.CAR -> stringResource(Res.string.travel_mode_car)
    LegKind.PUBLIC_TRANSPORT -> stringResource(Res.string.travel_mode_transit)
    LegKind.FAR -> ""
}

/** The icon of a mode; none for a leg too far to guess (spec 1b). */
fun legSymbol(kind: LegKind): String? = when (kind) {
    LegKind.WALK -> Symbols.directionsWalk
    LegKind.MOTORCYCLE -> Symbols.twoWheeler
    LegKind.CAR -> Symbols.directionsCar
    LegKind.PUBLIC_TRANSPORT -> Symbols.directionsTransit
    LegKind.FAR -> null
}

/** The text of the trip's main mode: "Motor", "Mobil", "Transportasi umum". */
fun mainModeStringRes(mode: MainTravelMode): StringResource = when (mode) {
    MainTravelMode.MOTORCYCLE -> Res.string.travel_main_motorcycle
    MainTravelMode.CAR -> Res.string.travel_main_car
    MainTravelMode.PUBLIC_TRANSPORT -> Res.string.travel_main_public
}

@Composable
fun mainModeLabel(mode: MainTravelMode): String = stringResource(mainModeStringRes(mode))

/**
 * The capsule's line: "Motor/ojol · ±15 mnt · 5,2 km", "Umum · 5,2 km", "Jarak jauh · ±150 km". A tight leg that is too far to
 * guess also shows its time ("Jarak jauh ±150 km · ±3 j"), which the plain capsule never does (spec 1b and 1c).
 */
@Composable
fun capsuleText(leg: TravelLeg, warned: Boolean): String {
    val distance = travelDistanceText(leg.roadKm)
    return when (leg.kind) {
        LegKind.FAR ->
            if (warned && leg.warningMinutes != null) {
                stringResource(Res.string.travel_capsule_far_warning, distance, travelMinutesText(leg.warningMinutes!!))
            } else {
                stringResource(Res.string.travel_capsule_far, distance)
            }
        LegKind.PUBLIC_TRANSPORT -> stringResource(Res.string.travel_capsule_transit, legModeLabel(leg.kind), distance)
        else -> stringResource(Res.string.travel_capsule, legModeLabel(leg.kind), travelMinutesText(leg.minutes ?: 0), distance)
    }
}
