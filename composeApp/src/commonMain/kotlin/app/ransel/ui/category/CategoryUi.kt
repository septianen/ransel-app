package app.ransel.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.ransel.shared.model.Category
import app.ransel.shared.model.PackingCategory
import app.ransel.shared.model.PaymentMethod
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.StringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.expense_category_attraction
import ransel.composeapp.generated.resources.expense_category_food
import ransel.composeapp.generated.resources.expense_category_lodging
import ransel.composeapp.generated.resources.expense_category_other
import ransel.composeapp.generated.resources.expense_category_shopping
import ransel.composeapp.generated.resources.expense_category_transport
import ransel.composeapp.generated.resources.itinerary_category_attraction
import ransel.composeapp.generated.resources.itinerary_category_food
import ransel.composeapp.generated.resources.itinerary_category_lodging
import ransel.composeapp.generated.resources.itinerary_category_other
import ransel.composeapp.generated.resources.itinerary_category_shopping
import ransel.composeapp.generated.resources.itinerary_category_transport
import ransel.composeapp.generated.resources.packing_category_clothing
import ransel.composeapp.generated.resources.packing_category_documents
import ransel.composeapp.generated.resources.packing_category_electronics
import ransel.composeapp.generated.resources.packing_category_health
import ransel.composeapp.generated.resources.packing_category_other
import ransel.composeapp.generated.resources.packing_category_toiletries
import ransel.composeapp.generated.resources.payment_method_cash
import ransel.composeapp.generated.resources.payment_method_credit_card
import ransel.composeapp.generated.resources.payment_method_debit_card
import ransel.composeapp.generated.resources.payment_method_e_wallet
import ransel.composeapp.generated.resources.payment_method_other
import ransel.composeapp.generated.resources.payment_method_transfer

/*
 * UI-only mappings over the shared enums (docs/specs/itinerary-spec.md, packing-spec.md).
 * Itinerary and expenses share `Category`: same tile color and icon, different label sets.
 */

val Category.itineraryLabel: StringResource
    get() = when (this) {
        Category.ATTRACTION -> Res.string.itinerary_category_attraction
        Category.FOOD -> Res.string.itinerary_category_food
        Category.TRANSPORT -> Res.string.itinerary_category_transport
        Category.LODGING -> Res.string.itinerary_category_lodging
        Category.SHOPPING -> Res.string.itinerary_category_shopping
        Category.OTHER -> Res.string.itinerary_category_other
    }

val Category.expenseLabel: StringResource
    get() = when (this) {
        Category.ATTRACTION -> Res.string.expense_category_attraction
        Category.FOOD -> Res.string.expense_category_food
        Category.TRANSPORT -> Res.string.expense_category_transport
        Category.LODGING -> Res.string.expense_category_lodging
        Category.SHOPPING -> Res.string.expense_category_shopping
        Category.OTHER -> Res.string.expense_category_other
    }

/** Default icon. Attractions and transport may use a more specific one (spec), still on the same tile. */
val Category.symbol: String
    get() = when (this) {
        Category.ATTRACTION -> Symbols.attractions
        Category.FOOD -> Symbols.restaurant
        Category.TRANSPORT -> Symbols.directionsCar
        Category.LODGING -> Symbols.hotel
        Category.SHOPPING -> Symbols.shoppingBag
        Category.OTHER -> Symbols.event
    }

@Composable
fun Category.tileColor(): Color {
    val accents = IrisTheme.accents
    return when (this) {
        Category.ATTRACTION -> accents.blue
        Category.FOOD -> accents.orange
        Category.TRANSPORT -> accents.teal
        Category.LODGING -> accents.purple
        Category.SHOPPING -> accents.yellow
        Category.OTHER -> accents.brown
    }
}

val PackingCategory.label: StringResource
    get() = when (this) {
        PackingCategory.CLOTHING -> Res.string.packing_category_clothing
        PackingCategory.DOCUMENTS -> Res.string.packing_category_documents
        PackingCategory.ELECTRONICS -> Res.string.packing_category_electronics
        PackingCategory.TOILETRIES -> Res.string.packing_category_toiletries
        PackingCategory.HEALTH -> Res.string.packing_category_health
        PackingCategory.OTHER -> Res.string.packing_category_other
    }

val PackingCategory.symbol: String
    get() = when (this) {
        PackingCategory.CLOTHING -> Symbols.apparel
        PackingCategory.DOCUMENTS -> Symbols.badge
        PackingCategory.ELECTRONICS -> Symbols.power
        PackingCategory.TOILETRIES -> Symbols.soap
        PackingCategory.HEALTH -> Symbols.medicalServices
        PackingCategory.OTHER -> Symbols.category
    }

@Composable
fun PackingCategory.tileColor(): Color {
    val accents = IrisTheme.accents
    return when (this) {
        PackingCategory.CLOTHING -> accents.purple
        PackingCategory.DOCUMENTS -> accents.blue
        PackingCategory.ELECTRONICS -> accents.orange
        PackingCategory.TOILETRIES -> accents.teal
        PackingCategory.HEALTH -> accents.red
        PackingCategory.OTHER -> accents.brown
    }
}

val PaymentMethod.label: StringResource
    get() = when (this) {
        PaymentMethod.CASH -> Res.string.payment_method_cash
        PaymentMethod.DEBIT_CARD -> Res.string.payment_method_debit_card
        PaymentMethod.CREDIT_CARD -> Res.string.payment_method_credit_card
        PaymentMethod.TRANSFER -> Res.string.payment_method_transfer
        PaymentMethod.E_WALLET -> Res.string.payment_method_e_wallet
        PaymentMethod.OTHER -> Res.string.payment_method_other
    }

/** Expenses show "Aktivitas" with a ticket icon (`local_activity`) instead of the itinerary's `attractions`. */
val Category.expenseSymbol: String
    get() = if (this == Category.ATTRACTION) Symbols.localActivity else symbol

val PaymentMethod.symbol: String
    get() = when (this) {
        PaymentMethod.CASH -> Symbols.payments
        PaymentMethod.DEBIT_CARD, PaymentMethod.CREDIT_CARD -> Symbols.creditCard
        PaymentMethod.TRANSFER -> Symbols.accountBalance
        PaymentMethod.E_WALLET -> Symbols.wallet
        PaymentMethod.OTHER -> Symbols.moreHoriz
    }
