package app.ransel.feature.explore

import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import org.jetbrains.compose.resources.StringResource
import ransel.composeapp.generated.resources.*

val Interest.label: StringResource
    get() = when (this) {
        Interest.NATURE -> Res.string.explore_interest_nature
        Interest.FOOD -> Res.string.explore_interest_food
        Interest.CULTURE -> Res.string.explore_interest_culture
        Interest.SHOPPING -> Res.string.explore_interest_shopping
        Interest.ADVENTURE -> Res.string.explore_interest_adventure
        Interest.NIGHTLIFE -> Res.string.explore_interest_nightlife
        Interest.PHOTO -> Res.string.explore_interest_photo
        Interest.RELAX -> Res.string.explore_interest_relax
    }

/** "Murah" / "Sedang" / "Mewah" (the `$` signs are drawn next to it). */
val PriceLevel.label: StringResource
    get() = when (this) {
        PriceLevel.LOW -> Res.string.explore_price_low
        PriceLevel.MEDIUM -> Res.string.explore_price_mid
        PriceLevel.HIGH -> Res.string.explore_price_high
    }

val PriceLevel.example: StringResource
    get() = when (this) {
        PriceLevel.LOW -> Res.string.explore_price_low_example
        PriceLevel.MEDIUM -> Res.string.explore_price_mid_example
        PriceLevel.HIGH -> Res.string.explore_price_high_example
    }

val PriceLevel.dollars: String
    get() = when (this) {
        PriceLevel.LOW -> "$"
        PriceLevel.MEDIUM -> "$$"
        PriceLevel.HIGH -> "$$$"
    }

val TravelStyle.label: StringResource
    get() = when (this) {
        TravelStyle.SANTAI -> Res.string.explore_style_relaxed
        TravelStyle.PADAT -> Res.string.explore_style_packed
    }
