package app.ransel.data.local

import app.ransel.data.remote.ApiJson
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpensePayerDto
import app.ransel.shared.dto.ExpenseSplitDto
import kotlinx.serialization.builtins.ListSerializer
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.PackingCategory
import app.ransel.shared.model.PaymentMethod
import app.ransel.shared.model.Role
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

private fun Long.toInstant() = Instant.fromEpochMilliseconds(this)
private fun Instant.millis() = toEpochMilliseconds()

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

fun TripEntity.toDto() = TripDto(
    id = id, ownerId = ownerId, title = title, destination = destination,
    startDate = LocalDate.parse(startDate), endDate = LocalDate.parse(endDate),
    baseCurrency = baseCurrency, budgetMinor = budgetMinor, coverUrl = coverUrl, myRole = enumOr(myRole, Role.OWNER),
    createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(), deletedAt = deletedAt?.toInstant(),
)

fun ItineraryItemEntity.toDto() = ItineraryItemDto(
    id = id, tripId = tripId, dayDate = dayDate?.let(LocalDate::parse),
    startTime = startTime?.let(LocalTime::parse), endTime = endTime?.let(LocalTime::parse),
    title = title, category = enumOr(category, Category.OTHER), placeName = placeName, placeId = placeId,
    lat = lat, lng = lng, durationMin = durationMin, notes = notes, bookingUrl = bookingUrl,
    source = enumOr(source, ItemSource.MANUAL), sortOrder = sortOrder,
    createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(), deletedAt = deletedAt?.toInstant(),
)

fun PackingItemEntity.toDto() = PackingItemDto(
    id = id, tripId = tripId, category = enumOr(category, PackingCategory.OTHER), name = name,
    quantity = quantity, isPacked = isPacked, sortOrder = sortOrder,
    createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(), deletedAt = deletedAt?.toInstant(),
)

fun ExpenseEntity.toDto() = ExpenseDto(
    id = id, tripId = tripId, amountMinor = amountMinor, currency = currency, fxRateToBase = fxRateToBase,
    category = enumOr(category, Category.OTHER), paymentMethod = enumOr(paymentMethod, PaymentMethod.CASH),
    note = note, spentAt = spentAt.toInstant(), paidByUserId = paidByUserId,
    payers = payersJson.toPayers(),
    splits = splitsJson.toSplits(),
    createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(), deletedAt = deletedAt?.toInstant(),
)

fun Instant.toMillis(): Long = millis()

// DTO -> entity, used when applying a sync pull. Rows that came from the server are SYNCED.
fun TripDto.toEntity() = TripEntity(
    id = id, ownerId = ownerId, title = title, destination = destination,
    startDate = startDate.toString(), endDate = endDate.toString(), baseCurrency = baseCurrency,
    budgetMinor = budgetMinor, coverUrl = coverUrl,
    createdAt = createdAt.toMillis(), updatedAt = updatedAt.toMillis(), deletedAt = deletedAt?.toMillis(),
    syncState = SyncState.SYNCED, myRole = myRole.name,
)

fun ItineraryItemDto.toEntity() = ItineraryItemEntity(
    id = id, tripId = tripId, dayDate = dayDate?.toString(), startTime = startTime?.toString(),
    endTime = endTime?.toString(), title = title, category = category.name, placeName = placeName,
    placeId = placeId, lat = lat, lng = lng, durationMin = durationMin, notes = notes, bookingUrl = bookingUrl,
    source = source.name, sortOrder = sortOrder,
    createdAt = createdAt.toMillis(), updatedAt = updatedAt.toMillis(), deletedAt = deletedAt?.toMillis(),
    syncState = SyncState.SYNCED,
)

fun PackingItemDto.toEntity() = PackingItemEntity(
    id = id, tripId = tripId, category = category.name, name = name, quantity = quantity,
    isPacked = isPacked, sortOrder = sortOrder,
    createdAt = createdAt.toMillis(), updatedAt = updatedAt.toMillis(), deletedAt = deletedAt?.toMillis(),
    syncState = SyncState.SYNCED,
)

fun ExpenseDto.toEntity() = ExpenseEntity(
    id = id, tripId = tripId, amountMinor = amountMinor, currency = currency, fxRateToBase = fxRateToBase,
    category = category.name, paymentMethod = paymentMethod.name, note = note, spentAt = spentAt.toMillis(),
    paidByUserId = paidByUserId, splitsJson = splits.toSplitsJson(), payersJson = payers.toPayersJson(),
    createdAt = createdAt.toMillis(), updatedAt = updatedAt.toMillis(), deletedAt = deletedAt?.toMillis(),
    syncState = SyncState.SYNCED,
)

private val splitsSerializer = ListSerializer(ExpenseSplitDto.serializer())
private val payersSerializer = ListSerializer(ExpensePayerDto.serializer())

/** Payers are one JSON column too, written and read with their expense; null = one payer. */
fun List<ExpensePayerDto>.toPayersJson(): String? =
    takeIf { it.isNotEmpty() }?.let { ApiJson.encodeToString(payersSerializer, it) }

fun String?.toPayers(): List<ExpensePayerDto> =
    if (isNullOrEmpty()) emptyList() else runCatching { ApiJson.decodeFromString(payersSerializer, this) }.getOrDefault(emptyList())

/** Splits are stored as one JSON column: they are only ever read and written with their expense. */
fun List<ExpenseSplitDto>.toSplitsJson(): String? =
    takeIf { it.isNotEmpty() }?.let { ApiJson.encodeToString(splitsSerializer, it) }

fun String?.toSplits(): List<ExpenseSplitDto> =
    if (isNullOrEmpty()) emptyList() else runCatching { ApiJson.decodeFromString(splitsSerializer, this) }.getOrDefault(emptyList())
