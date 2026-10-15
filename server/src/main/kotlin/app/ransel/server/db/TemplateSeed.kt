package app.ransel.server.db

import app.ransel.shared.dto.TemplateItem
import app.ransel.shared.model.PackingCategory
import app.ransel.shared.model.PackingCategory.CLOTHING
import app.ransel.shared.model.PackingCategory.DOCUMENTS
import app.ransel.shared.model.PackingCategory.ELECTRONICS
import app.ransel.shared.model.PackingCategory.HEALTH
import app.ransel.shared.model.PackingCategory.OTHER
import app.ransel.shared.model.PackingCategory.TOILETRIES
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/**
 * Built-in packing templates (`owner_id` NULL): beach, city trip, mountain hike. Repeatable: each template has
 * a fixed id derived from its key, so running this on every start inserts what is missing and refreshes the
 * content of what exists. Names and items are user-facing copy, hence Indonesian (the app's default language).
 */
object TemplateSeed {
    private class Builtin(val key: String, val name: String, val items: List<TemplateItem>)

    private fun item(category: PackingCategory, name: String, quantity: Int = 1) = TemplateItem(category, name, quantity)

    private val builtins = listOf(
        Builtin(
            "beach", "Liburan pantai",
            listOf(
                item(CLOTHING, "Baju renang", 2), item(CLOTHING, "Kaos", 4), item(CLOTHING, "Celana pendek", 3),
                item(CLOTHING, "Sandal jepit"), item(CLOTHING, "Topi"), item(CLOTHING, "Pakaian dalam", 5),
                item(TOILETRIES, "Sunblock"), item(TOILETRIES, "Sabun mandi"), item(TOILETRIES, "Sikat gigi"),
                item(TOILETRIES, "Pasta gigi"), item(TOILETRIES, "Sampo"), item(TOILETRIES, "Handuk pantai"),
                item(ELECTRONICS, "Charger HP"), item(ELECTRONICS, "Power bank"), item(ELECTRONICS, "Pelindung HP tahan air"),
                item(DOCUMENTS, "KTP"), item(DOCUMENTS, "Tiket / booking"),
                item(HEALTH, "Obat pribadi"), item(HEALTH, "Lotion setelah berjemur"), item(HEALTH, "Obat anti mabuk"),
                item(OTHER, "Kacamata hitam"), item(OTHER, "Tas kedap air"),
            ),
        ),
        Builtin(
            "city", "Wisata kota",
            listOf(
                item(CLOTHING, "Kaos", 4), item(CLOTHING, "Celana panjang", 2), item(CLOTHING, "Sepatu nyaman"),
                item(CLOTHING, "Jaket tipis"), item(CLOTHING, "Pakaian dalam", 5),
                item(TOILETRIES, "Sikat gigi"), item(TOILETRIES, "Pasta gigi"), item(TOILETRIES, "Sabun mandi"),
                item(TOILETRIES, "Deodoran"),
                item(ELECTRONICS, "Charger HP"), item(ELECTRONICS, "Power bank"), item(ELECTRONICS, "Earphone"),
                item(DOCUMENTS, "KTP"), item(DOCUMENTS, "Tiket / booking"), item(DOCUMENTS, "Kartu ATM"),
                item(HEALTH, "Obat pribadi"), item(HEALTH, "Masker"),
                item(OTHER, "Tas kecil"), item(OTHER, "Botol minum"),
            ),
        ),
        Builtin(
            "mountain", "Mendaki gunung",
            listOf(
                item(CLOTHING, "Jaket gunung"), item(CLOTHING, "Kaos cepat kering", 3), item(CLOTHING, "Celana panjang", 2),
                item(CLOTHING, "Kaos kaki", 4), item(CLOTHING, "Sepatu gunung"), item(CLOTHING, "Sarung tangan"),
                item(CLOTHING, "Kupluk"),
                item(TOILETRIES, "Tisu basah"), item(TOILETRIES, "Sabun mandi"), item(TOILETRIES, "Sikat gigi"),
                item(TOILETRIES, "Pasta gigi"),
                item(ELECTRONICS, "Headlamp"), item(ELECTRONICS, "Power bank"), item(ELECTRONICS, "Charger HP"),
                item(DOCUMENTS, "KTP"), item(DOCUMENTS, "Surat izin pendakian"),
                item(HEALTH, "P3K"), item(HEALTH, "Obat pribadi"), item(HEALTH, "Minyak angin"),
                item(OTHER, "Jas hujan"), item(OTHER, "Botol minum"), item(OTHER, "Kantong sampah"), item(OTHER, "Makanan ringan"),
            ),
        ),
    )

    fun idOf(key: String): UUID = UUID.nameUUIDFromBytes("ransel:packing-template:$key".toByteArray())

    fun run() = transaction {
        builtins.forEach { template ->
            val id = idOf(template.key)
            if (PackingTemplates.selectAll().where { PackingTemplates.id eq id }.empty()) {
                PackingTemplates.insert {
                    it[PackingTemplates.id] = id
                    it[name] = template.name
                    it[items] = template.items
                }
            } else {
                PackingTemplates.update({ PackingTemplates.id eq id }) {
                    it[name] = template.name
                    it[items] = template.items
                    it[deletedAt] = null
                }
            }
        }
    }
}
