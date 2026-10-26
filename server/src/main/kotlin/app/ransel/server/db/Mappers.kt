package app.ransel.server.db

import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.shared.dto.ExpensePayerDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.KittyDepositDto
import app.ransel.shared.dto.KittyDto
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PaymentMethodDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Role
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.time.toKotlinInstant

/** java.time (Exposed) -> kotlin.time / kotlinx-datetime (contract DTOs). */
fun java.time.Instant.toTimestamp(): kotlin.time.Instant = toKotlinInstant()

fun ResultRow.toTripDto(role: Role) = TripDto(
    id = this[Trips.id].toString(),
    ownerId = this[Trips.ownerId].toString(),
    title = this[Trips.title],
    destination = this[Trips.destination],
    startDate = this[Trips.startDate].toKotlinLocalDate(),
    endDate = this[Trips.endDate].toKotlinLocalDate(),
    baseCurrency = this[Trips.baseCurrency],
    budgetMinor = this[Trips.budgetMinor],
    coverUrl = this[Trips.coverUrl],
    myRole = role,
    createdAt = this[Trips.createdAt].toTimestamp(),
    updatedAt = this[Trips.updatedAt].toTimestamp(),
    deletedAt = this[Trips.deletedAt]?.toTimestamp(),
)

fun ResultRow.toItineraryItemDto() = ItineraryItemDto(
    id = this[ItineraryItems.id].toString(),
    tripId = this[ItineraryItems.tripId].toString(),
    dayDate = this[ItineraryItems.dayDate]?.toKotlinLocalDate(),
    startTime = this[ItineraryItems.startTime]?.toKotlinLocalTime(),
    endTime = this[ItineraryItems.endTime]?.toKotlinLocalTime(),
    title = this[ItineraryItems.title],
    category = this[ItineraryItems.category],
    placeName = this[ItineraryItems.placeName],
    placeId = this[ItineraryItems.placeId],
    lat = this[ItineraryItems.lat],
    lng = this[ItineraryItems.lng],
    durationMin = this[ItineraryItems.durationMin],
    notes = this[ItineraryItems.notes],
    bookingUrl = this[ItineraryItems.bookingUrl],
    source = this[ItineraryItems.itemSource],
    sortOrder = this[ItineraryItems.sortOrder],
    createdAt = this[ItineraryItems.createdAt].toTimestamp(),
    updatedAt = this[ItineraryItems.updatedAt].toTimestamp(),
    deletedAt = this[ItineraryItems.deletedAt]?.toTimestamp(),
)

fun ResultRow.toPackingItemDto() = PackingItemDto(
    id = this[PackingItems.id].toString(),
    tripId = this[PackingItems.tripId].toString(),
    category = this[PackingItems.category],
    name = this[PackingItems.name],
    quantity = this[PackingItems.quantity],
    isPacked = this[PackingItems.isPacked],
    sortOrder = this[PackingItems.sortOrder],
    createdAt = this[PackingItems.createdAt].toTimestamp(),
    updatedAt = this[PackingItems.updatedAt].toTimestamp(),
    deletedAt = this[PackingItems.deletedAt]?.toTimestamp(),
)

fun ResultRow.toExpenseDto(
    splits: List<ExpenseSplitDto> = emptyList(),
    payers: List<ExpensePayerDto> = emptyList(),
    items: List<ExpenseItemDto> = emptyList(),
) = ExpenseDto(
    id = this[Expenses.id].toString(),
    tripId = this[Expenses.tripId].toString(),
    amountMinor = this[Expenses.amountMinor],
    currency = this[Expenses.currency],
    fxRateToBase = this[Expenses.fxRateToBase]?.stripTrailingZeros()?.toPlainString(),
    category = this[Expenses.category],
    paymentMethod = this[Expenses.paymentMethod],
    note = this[Expenses.note],
    spentAt = this[Expenses.spentAt].toTimestamp(),
    paidByMemberId = this[Expenses.paidByMemberId].toString(),
    // the payer the expense names first, then the biggest amounts: the same order on every read
    payers = payers.sortedWith(
        compareByDescending<ExpensePayerDto> { it.memberId == this[Expenses.paidByMemberId].toString() }
            .thenByDescending { it.amountMinor }
            .thenBy { it.memberId },
    ),
    splits = splits,
    paidFromKitty = this[Expenses.paidFromKitty],
    isEstimate = this[Expenses.isEstimate],
    items = items,
    createdAt = this[Expenses.createdAt].toTimestamp(),
    updatedAt = this[Expenses.updatedAt].toTimestamp(),
    deletedAt = this[Expenses.deletedAt]?.toTimestamp(),
)

fun ResultRow.toSettlementDto() = SettlementDto(
    id = this[Settlements.id].toString(),
    tripId = this[Settlements.tripId].toString(),
    fromMemberId = this[Settlements.fromMemberId].toString(),
    toMemberId = this[Settlements.toMemberId].toString(),
    amountMinor = this[Settlements.amountMinor],
    settledAt = this[Settlements.settledAt].toTimestamp(),
    status = this[Settlements.status],
    claimedAt = this[Settlements.claimedAt]?.toTimestamp(),
    createdAt = this[Settlements.createdAt].toTimestamp(),
    updatedAt = this[Settlements.updatedAt].toTimestamp(),
    deletedAt = this[Settlements.deletedAt]?.toTimestamp(),
)

fun ResultRow.toKittyDto() = KittyDto(
    tripId = this[Kitties.tripId].toString(),
    amountPerMemberMinor = this[Kitties.amountPerMemberMinor],
    treasurerMemberId = this[Kitties.treasurerMemberId].toString(),
    createdAt = this[Kitties.createdAt].toTimestamp(),
    updatedAt = this[Kitties.updatedAt].toTimestamp(),
    deletedAt = this[Kitties.deletedAt]?.toTimestamp(),
)

fun ResultRow.toKittyDepositDto() = KittyDepositDto(
    id = this[KittyDeposits.id].toString(),
    tripId = this[KittyDeposits.tripId].toString(),
    memberId = this[KittyDeposits.memberId].toString(),
    amountMinor = this[KittyDeposits.amountMinor],
    receivedByMemberId = this[KittyDeposits.receivedByMemberId].toString(),
    receivedAt = this[KittyDeposits.receivedAt].toTimestamp(),
    createdAt = this[KittyDeposits.createdAt].toTimestamp(),
    updatedAt = this[KittyDeposits.updatedAt].toTimestamp(),
    deletedAt = this[KittyDeposits.deletedAt]?.toTimestamp(),
)

fun ResultRow.toPaymentMethodDto() = PaymentMethodDto(
    id = this[PaymentMethods.id].toString(),
    kind = this[PaymentMethods.kind],
    provider = this[PaymentMethods.provider],
    accountNumber = this[PaymentMethods.accountNumber],
    holderName = this[PaymentMethods.holderName],
    isPrimary = this[PaymentMethods.isPrimary],
    createdAt = this[PaymentMethods.createdAt].toTimestamp(),
    updatedAt = this[PaymentMethods.updatedAt].toTimestamp(),
)
