package app.ransel.server.db

import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PackingItemDto
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

fun ResultRow.toExpenseDto(splits: List<ExpenseSplitDto> = emptyList()) = ExpenseDto(
    id = this[Expenses.id].toString(),
    tripId = this[Expenses.tripId].toString(),
    amountMinor = this[Expenses.amountMinor],
    currency = this[Expenses.currency],
    fxRateToBase = this[Expenses.fxRateToBase]?.stripTrailingZeros()?.toPlainString(),
    category = this[Expenses.category],
    paymentMethod = this[Expenses.paymentMethod],
    note = this[Expenses.note],
    spentAt = this[Expenses.spentAt].toTimestamp(),
    paidByUserId = this[Expenses.paidByUserId].toString(),
    splits = splits,
    createdAt = this[Expenses.createdAt].toTimestamp(),
    updatedAt = this[Expenses.updatedAt].toTimestamp(),
    deletedAt = this[Expenses.deletedAt]?.toTimestamp(),
)
