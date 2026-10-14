package app.ransel.shared.serialization

import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A point in time, always UTC on the wire: "2026-10-13T05:30:00Z".
 *
 * Uses kotlin.time.Instant (stable since Kotlin 2.3). Our own serializer keeps the
 * JSON format fixed no matter which kotlinx-serialization / kotlinx-datetime
 * version the modules end up on.
 *
 * Dates use kotlinx.datetime.LocalDate ("2026-11-20") and clock times use
 * kotlinx.datetime.LocalTime ("09:00"); both serialize as ISO strings by default.
 */
typealias Timestamp = @Serializable(with = InstantIsoSerializer::class) Instant

object InstantIsoSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.ransel.Timestamp", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
