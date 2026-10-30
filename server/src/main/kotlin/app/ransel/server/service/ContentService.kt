package app.ransel.server.service

import app.ransel.server.ai.ExploreSuggestionService
import app.ransel.server.ai.normalizeDestination
import app.ransel.server.db.ContentCategories
import app.ransel.server.db.ContentErrors
import app.ransel.server.db.ContentMeta
import app.ransel.server.db.ContentStrings
import app.ransel.server.db.DestinationInterests
import app.ransel.server.db.ErrorCounts
import app.ransel.server.db.ExploreSuggestionsCache
import app.ransel.server.db.Users
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.CATEGORY_COLORS
import app.ransel.shared.domain.CATEGORY_ICONS
import app.ransel.shared.domain.MAX_CATEGORY_LABEL
import app.ransel.shared.domain.MAX_INTEREST_CHIPS
import app.ransel.shared.domain.MAX_INTEREST_LABEL
import app.ransel.shared.domain.categoryConstants
import app.ransel.shared.domain.categoryLabelKey
import app.ransel.shared.domain.defaultCategoryStyle
import app.ransel.shared.domain.validateContentString
import app.ransel.shared.domain.validateErrorText
import app.ransel.shared.dto.AdminContentCategoriesDto
import app.ransel.shared.dto.AdminContentCategoryDto
import app.ransel.shared.dto.AdminContentErrorDto
import app.ransel.shared.dto.AdminContentErrorsDto
import app.ransel.shared.dto.AdminContentStringDto
import app.ransel.shared.dto.AdminContentStringsDto
import app.ransel.shared.dto.AdminInterestCurationDto
import app.ransel.shared.dto.AdminInterestSummaryDto
import app.ransel.shared.dto.ContentCategoryDto
import app.ransel.shared.dto.ContentDto
import app.ransel.shared.dto.ContentErrorDto
import app.ransel.shared.dto.ContentPublishResultDto
import app.ransel.shared.dto.ContentTextDto
import app.ransel.shared.dto.ErrorTextDto
import app.ransel.shared.dto.InterestChipDto
import app.ransel.shared.dto.UpdateContentCategoriesRequest
import app.ransel.shared.dto.UpdateContentErrorRequest
import app.ransel.shared.dto.UpdateContentStringRequest
import app.ransel.shared.model.AdminAction
import app.ransel.shared.model.CategoryKind
import app.ransel.shared.model.ContentStatus
import app.ransel.shared.model.InterestChipKind
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.time.toKotlinInstant

/**
 * Remote content (admin-spec stage D, B9.9). Strings and error messages are saved as drafts and go live together
 * with [publish]; categories and destination interests go live when saved. [content] is what `GET /content` sends,
 * rebuilt after every change that is live.
 */
class ContentService(
    private val suggestions: ExploreSuggestionService? = null,
    private val catalog: ContentCatalog = ContentCatalog.bundled,
) {
    private val wib = ZoneId.of("Asia/Jakarta")

    @Volatile private var cached: ContentDto? = null

    // ── strings ─────────────────────────────────────────────────────────────

    fun strings(): AdminContentStringsDto = transaction {
        val rows = ContentStrings.selectAll().toList()
        val emails = emails(rows.mapNotNull { it[ContentStrings.updatedBy] })
        val byKey = rows.groupBy { it[ContentStrings.key] }
        AdminContentStringsDto(
            version = version(),
            drafts = draftCount(),
            strings = catalog.strings.map { entry ->
                val published = byKey[entry.key]?.firstOrNull { it[ContentStrings.status] == PUBLISHED }
                val draft = byKey[entry.key]?.firstOrNull { it[ContentStrings.status] == DRAFT }
                val latest = draft ?: published
                AdminContentStringDto(
                    key = entry.key,
                    area = entry.area,
                    defaults = ContentTextDto(entry.id, entry.en),
                    published = published?.text(),
                    draft = draft?.text(),
                    status = when {
                        draft != null -> ContentStatus.DRAFT
                        published != null -> ContentStatus.PUBLISHED
                        else -> ContentStatus.DEFAULT
                    },
                    updatedByEmail = latest?.get(ContentStrings.updatedBy)?.let { emails[it] },
                    updatedAt = latest?.get(ContentStrings.updatedAt)?.toKotlinInstant(),
                )
            },
        )
    }

    fun saveString(admin: AdminPrincipal, body: UpdateContentStringRequest): AdminContentStringDto {
        val entry = catalog.string(body.key) ?: throw validationFailed(mapOf("key" to "UNKNOWN"))
        val text = body.text.cleaned()
        if (!body.discard) {
            val problems = validateContentString(entry.id, entry.en, text.id, text.en)
            if (problems.isNotEmpty()) throw validationFailed(problems)
        }
        transaction {
            if (body.discard) {
                ContentStrings.deleteWhere { (ContentStrings.key eq body.key) and (ContentStrings.status eq DRAFT) }
            } else {
                ContentStrings.upsert {
                    it[key] = body.key
                    it[status] = DRAFT
                    it[valueId] = text.id
                    it[valueEn] = text.en
                    it[updatedBy] = admin.id
                    it[updatedAt] = Instant.now()
                }
            }
        }
        return strings().strings.first { it.key == body.key }
    }

    // ── error messages ──────────────────────────────────────────────────────

    fun errors(now: Instant = Instant.now()): AdminContentErrorsDto = transaction {
        val rows = ContentErrors.selectAll().toList()
        val emails = emails(rows.mapNotNull { it[ContentErrors.updatedBy] })
        val byCode = rows.groupBy { it[ContentErrors.code] }
        val today = LocalDate.ofInstant(now, wib).atStartOfDay(wib).toInstant()
        val total = ErrorCounts.count.sum()
        val counts = ErrorCounts.select(ErrorCounts.code, total).where { ErrorCounts.hour greaterEq today }
            .groupBy(ErrorCounts.code).associate { it[ErrorCounts.code] to (it[total] ?: 0L).toInt() }
        AdminContentErrorsDto(
            version = version(),
            drafts = draftCount(),
            errors = ErrorCode.ALL.map { code ->
                val published = byCode[code]?.firstOrNull { it[ContentErrors.status] == PUBLISHED }
                val draft = byCode[code]?.firstOrNull { it[ContentErrors.status] == DRAFT }
                val latest = draft ?: published
                AdminContentErrorDto(
                    code = code,
                    published = published?.errorText(),
                    draft = draft?.errorText(),
                    status = when {
                        draft != null -> ContentStatus.DRAFT
                        published != null -> ContentStatus.PUBLISHED
                        else -> ContentStatus.DEFAULT
                    },
                    countToday = counts[code] ?: 0,
                    updatedByEmail = latest?.get(ContentErrors.updatedBy)?.let { emails[it] },
                    updatedAt = latest?.get(ContentErrors.updatedAt)?.toKotlinInstant(),
                )
            },
            illustrations = catalog.illustrations,
        )
    }

    fun saveError(admin: AdminPrincipal, body: UpdateContentErrorRequest): AdminContentErrorDto {
        if (body.code !in ErrorCode.ALL) throw validationFailed(mapOf("code" to "UNKNOWN"))
        val text = body.text.cleaned()
        if (!body.discard) {
            val problems = validateErrorText(text, catalog.illustrations)
            if (problems.isNotEmpty()) throw validationFailed(problems)
        }
        transaction {
            if (body.discard) {
                ContentErrors.deleteWhere { (ContentErrors.code eq body.code) and (ContentErrors.status eq DRAFT) }
            } else {
                ContentErrors.upsert {
                    it[code] = body.code
                    it[status] = DRAFT
                    it[titleId] = text.titleId
                    it[titleEn] = text.titleEn
                    it[messageId] = text.messageId
                    it[messageEn] = text.messageEn
                    it[buttonId] = text.buttonId
                    it[buttonEn] = text.buttonEn
                    it[illustration] = text.illustration
                    it[display] = text.display
                    it[updatedBy] = admin.id
                    it[updatedAt] = Instant.now()
                }
            }
        }
        return errors().errors.first { it.code == body.code }
    }

    // ── publish ─────────────────────────────────────────────────────────────

    /** "Terbitkan": every string and error draft goes live at once; a draft with no text removes the override. */
    fun publish(admin: AdminPrincipal): ContentPublishResultDto {
        val result = transaction {
            val strings = ContentStrings.selectAll().where { ContentStrings.status eq DRAFT }.toList()
            val errors = ContentErrors.selectAll().where { ContentErrors.status eq DRAFT }.toList()
            // the catalog may have changed since a draft was saved: check them all again
            val problems = buildMap {
                strings.forEach { row ->
                    val entry = catalog.string(row[ContentStrings.key])
                    val bad = entry == null || validateContentString(entry.id, entry.en, row[ContentStrings.valueId], row[ContentStrings.valueEn]).isNotEmpty()
                    if (bad) put(row[ContentStrings.key], "PLACEHOLDER_MISMATCH")
                }
                errors.forEach { row ->
                    if (validateErrorText(row.errorText(), catalog.illustrations).isNotEmpty()) put(row[ContentErrors.code], "PLACEHOLDER_MISMATCH")
                }
            }
            if (problems.isNotEmpty()) throw validationFailed(problems)
            val count = strings.size + errors.size
            if (count == 0) return@transaction ContentPublishResultDto(version(), 0)

            strings.forEach { row ->
                val key = row[ContentStrings.key]
                ContentStrings.deleteWhere { (ContentStrings.key eq key) and (ContentStrings.status eq PUBLISHED) }
                if (row[ContentStrings.valueId] != null || row[ContentStrings.valueEn] != null) {
                    ContentStrings.insert {
                        it[ContentStrings.key] = key
                        it[status] = PUBLISHED
                        it[valueId] = row[ContentStrings.valueId]
                        it[valueEn] = row[ContentStrings.valueEn]
                        it[updatedBy] = row[ContentStrings.updatedBy]
                        it[updatedAt] = Instant.now()
                    }
                }
            }
            ContentStrings.deleteWhere { ContentStrings.status eq DRAFT }
            errors.forEach { row ->
                val code = row[ContentErrors.code]
                ContentErrors.deleteWhere { (ContentErrors.code eq code) and (ContentErrors.status eq PUBLISHED) }
                val text = row.errorText()
                if (!text.isEmpty) {
                    ContentErrors.insert {
                        it[ContentErrors.code] = code
                        it[status] = PUBLISHED
                        it[titleId] = text.titleId
                        it[titleEn] = text.titleEn
                        it[messageId] = text.messageId
                        it[messageEn] = text.messageEn
                        it[buttonId] = text.buttonId
                        it[buttonEn] = text.buttonEn
                        it[illustration] = text.illustration
                        it[display] = text.display
                        it[updatedBy] = row[ContentErrors.updatedBy]
                        it[updatedAt] = Instant.now()
                    }
                }
            }
            ContentErrors.deleteWhere { ContentErrors.status eq DRAFT }
            val version = bump(admin)
            writeAudit(
                admin, AdminAction.CONTENT_PUBLISH, "CONTENT", "v$version", null,
                buildJsonObject {
                    put("version", version)
                    put("strings", strings.size)
                    put("errors", errors.size)
                },
            )
            ContentPublishResultDto(version, count)
        }
        cached = null
        return result
    }

    // ── categories ──────────────────────────────────────────────────────────

    fun categories(): AdminContentCategoriesDto = transaction {
        val rows = ContentCategories.selectAll().associateBy { it[ContentCategories.kind] to it[ContentCategories.constant] }
        AdminContentCategoriesDto(
            version = version(),
            categories = CategoryKind.entries.flatMap { kind ->
                categoryConstants(kind).mapIndexed { index, constant ->
                    val (icon, color) = defaultCategoryStyle(kind, constant)
                    val labelKey = categoryLabelKey(kind, constant)
                    AdminContentCategoryDto(
                        category = rows[kind to constant]?.category() ?: ContentCategoryDto(kind, constant, sortOrder = index),
                        defaultLabelId = catalog.string(labelKey)?.id ?: constant,
                        defaultLabelEn = catalog.string(labelKey)?.en ?: constant,
                        defaultIcon = icon,
                        defaultColor = color,
                    )
                }.sortedBy { it.category.sortOrder }
            },
            icons = CATEGORY_ICONS,
            colors = CATEGORY_COLORS,
        )
    }

    /** "Simpan dan terbitkan": every category of one list, in order; live at once. */
    fun saveCategories(admin: AdminPrincipal, body: UpdateContentCategoriesRequest): AdminContentCategoriesDto {
        val constants = categoryConstants(body.kind)
        val problems = buildMap {
            if (body.categories.map { it.constant }.sorted() != constants.sorted() || body.categories.any { it.kind != body.kind }) {
                put("categories", "MUST_LIST_ALL")
            }
            body.categories.forEach { c ->
                if (c.icon != null && c.icon !in CATEGORY_ICONS) put("icon", "UNKNOWN")
                if (c.color != null && c.color !in CATEGORY_COLORS) put("color", "UNKNOWN")
                if ((c.labelId?.length ?: 0) > MAX_CATEGORY_LABEL || (c.labelEn?.length ?: 0) > MAX_CATEGORY_LABEL) put("label", "TOO_LONG")
            }
        }
        if (problems.isNotEmpty()) throw validationFailed(problems)
        transaction {
            val before = ContentCategories.selectAll().where { ContentCategories.kind eq body.kind }.map { it.category() }
            body.categories.forEachIndexed { index, c ->
                ContentCategories.upsert {
                    it[kind] = body.kind
                    it[constant] = c.constant
                    it[labelId] = c.labelId?.trim()?.ifEmpty { null }
                    it[labelEn] = c.labelEn?.trim()?.ifEmpty { null }
                    it[icon] = c.icon
                    it[color] = c.color
                    it[sortOrder] = index
                    it[hidden] = c.hidden
                    it[updatedBy] = admin.id
                    it[updatedAt] = Instant.now()
                }
            }
            val version = bump(admin)
            writeAudit(
                admin, AdminAction.CONTENT_PUBLISH, "CONTENT", "categories:${body.kind.name}",
                Json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(ContentCategoryDto.serializer()), before),
                buildJsonObject { put("version", version) },
            )
        }
        cached = null
        return categories()
    }

    // ── destination interests ───────────────────────────────────────────────

    /** Every destination and month of the year the AI answered for, or that was curated; [query] filters by name. */
    fun interests(query: String?, now: Instant = Instant.now()): List<AdminInterestSummaryDto> = transaction {
        val q = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        data class Key(val destination: String, val month: Int, val language: String)
        val ai = ExploreSuggestionsCache.selectAll().toList()
            .groupBy { Key(it[ExploreSuggestionsCache.destination], it[ExploreSuggestionsCache.month].substringAfter('-').toInt(), it[ExploreSuggestionsCache.language]) }
            .mapValues { (_, rows) -> rows.maxBy { it[ExploreSuggestionsCache.createdAt] } }
        val curated = DestinationInterests.selectAll().associateBy {
            Key(it[DestinationInterests.destination], it[DestinationInterests.month].toInt(), it[DestinationInterests.language])
        }
        (ai.keys + curated.keys).filter { q == null || q in it.destination }.map { key ->
            val aiRow = ai[key]
            val chips = curated[key]?.get(DestinationInterests.chips)
            AdminInterestSummaryDto(
                destination = key.destination,
                month = key.month,
                language = key.language,
                chips = chips?.count { it.kind != InterestChipKind.HIDDEN } ?: aiRow?.get(ExploreSuggestionsCache.interests)?.size ?: 0,
                curated = chips != null,
                aiCreatedAt = aiRow?.get(ExploreSuggestionsCache.createdAt)?.toKotlinInstant(),
                aiExpiresAt = aiRow?.get(ExploreSuggestionsCache.expiresAt)?.toKotlinInstant(),
            )
        }.sortedWith(compareByDescending<AdminInterestSummaryDto> { it.aiCreatedAt ?: kotlin.time.Instant.DISTANT_PAST }.thenBy { it.destination })
    }

    /** The saved curation with the newest AI chips that are not in it yet. */
    fun interest(destination: String, month: Int, language: String): AdminInterestCurationDto = transaction {
        val (dest, lang) = interestKey(destination, month, language)
        val aiRow = ExploreSuggestionsCache.selectAll()
            .where { (ExploreSuggestionsCache.destination eq dest) and (ExploreSuggestionsCache.language eq lang) }
            .filter { it[ExploreSuggestionsCache.month].substringAfter('-').toInt() == month }
            .maxByOrNull { it[ExploreSuggestionsCache.createdAt] }
        val saved = DestinationInterests.selectAll().where {
            (DestinationInterests.destination eq dest) and (DestinationInterests.month eq month.toShort()) and (DestinationInterests.language eq lang)
        }.singleOrNull()
        val chips = saved?.get(DestinationInterests.chips).orEmpty()
        val known = chips.map { it.label.trim().lowercase() }.toSet()
        val fresh = aiRow?.get(ExploreSuggestionsCache.interests).orEmpty().filter { it.trim().lowercase() !in known }
            .map { InterestChipDto(it, InterestChipKind.AI) }
        AdminInterestCurationDto(
            destination = dest,
            month = month,
            language = lang,
            chips = chips + fresh,
            aiCreatedAt = aiRow?.get(ExploreSuggestionsCache.createdAt)?.toKotlinInstant(),
            aiExpiresAt = aiRow?.get(ExploreSuggestionsCache.expiresAt)?.toKotlinInstant(),
            curatedByEmail = saved?.get(DestinationInterests.curatedBy)?.let { emails(listOf(it))[it] },
            curatedAt = saved?.get(DestinationInterests.curatedAt)?.toKotlinInstant(),
        )
    }

    /** "Simpan kurasi": live for `GET /explore/suggestions` at once. */
    fun saveInterest(admin: AdminPrincipal, body: AdminInterestCurationDto): AdminInterestCurationDto {
        val (dest, lang) = interestKey(body.destination, body.month, body.language)
        val chips = body.chips.map { it.copy(label = it.label.trim()) }.filter { it.label.isNotEmpty() }.distinctBy { it.label.lowercase() }
        val problems = buildMap {
            if (chips.size > MAX_INTEREST_CHIPS) put("chips", "TOO_MANY")
            if (chips.any { it.label.length > MAX_INTEREST_LABEL }) put("chips", "TOO_LONG")
        }
        if (problems.isNotEmpty()) throw validationFailed(problems)
        transaction {
            val before = DestinationInterests.selectAll().where {
                (DestinationInterests.destination eq dest) and (DestinationInterests.month eq body.month.toShort()) and (DestinationInterests.language eq lang)
            }.singleOrNull()?.get(DestinationInterests.chips)
            DestinationInterests.upsert {
                it[destination] = dest
                it[month] = body.month.toShort()
                it[language] = lang
                it[DestinationInterests.chips] = chips
                it[curatedBy] = admin.id
                it[curatedAt] = Instant.now()
            }
            val list = kotlinx.serialization.builtins.ListSerializer(InterestChipDto.serializer())
            writeAudit(
                admin, AdminAction.CONTENT_PUBLISH, "CONTENT", "interests:$dest:${body.month}:$lang",
                before?.let { Json.encodeToJsonElement(list, it) }, Json.encodeToJsonElement(list, chips),
            )
        }
        return interest(dest, body.month, lang)
    }

    /** "Buat ulang dengan AI": a fresh AI answer for the coming such month; the curation is kept. */
    suspend fun regenerateInterest(destination: String, month: Int, language: String, today: LocalDate = LocalDate.now(wib)): AdminInterestCurationDto {
        val (dest, lang) = interestKey(destination, month, language)
        val year = if (month >= today.monthValue) today.year else today.year + 1
        val service = suggestions ?: throw aiUnavailable()
        service.regenerate(dest, "%04d-%02d".format(year, month), lang) ?: throw aiUnavailable()
        return interest(dest, month, lang)
    }

    // ── for the apps ────────────────────────────────────────────────────────

    /** `GET /content`: what is live. Null = the app already has [known]. */
    fun content(known: Int?): ContentDto? {
        val now = cached ?: build().also { cached = it }
        return if (known != null && known == now.version) null else now
    }

    private fun build(): ContentDto = transaction {
        val strings = ContentStrings.selectAll().where { ContentStrings.status eq PUBLISHED }.toList()
        ContentDto(
            version = version(),
            strings = mapOf(
                "id" to strings.mapNotNull { r -> r[ContentStrings.valueId]?.let { r[ContentStrings.key] to it } }.toMap(),
                "en" to strings.mapNotNull { r -> r[ContentStrings.valueEn]?.let { r[ContentStrings.key] to it } }.toMap(),
            ).filterValues { it.isNotEmpty() },
            errors = ContentErrors.selectAll().where { ContentErrors.status eq PUBLISHED }
                .map { ContentErrorDto(it[ContentErrors.code], it.errorText()) },
            categories = ContentCategories.selectAll().map { it.category() }.sortedWith(compareBy({ it.kind }, { it.sortOrder })),
        )
    }

    // ── plumbing ────────────────────────────────────────────────────────────

    private fun interestKey(destination: String, month: Int, language: String): Pair<String, String> {
        val dest = normalizeDestination(destination)
        if (dest.isEmpty() || dest.length > 200) throw validationFailed(mapOf("destination" to "REQUIRED"))
        if (month !in 1..12) throw validationFailed(mapOf("month" to "INVALID"))
        return dest to (if (language == "en") "en" else "id")
    }

    private fun aiUnavailable() = ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.AI_UNAVAILABLE, "Interest suggestions are unavailable right now")

    private fun JdbcTransaction.version(): Int = ContentMeta.selectAll().single()[ContentMeta.version]

    private fun JdbcTransaction.bump(admin: AdminPrincipal): Int {
        val next = version() + 1
        ContentMeta.update({ ContentMeta.id eq 1 }) {
            it[version] = next
            it[publishedAt] = Instant.now()
            it[publishedBy] = admin.id
        }
        return next
    }

    private fun JdbcTransaction.draftCount(): Int =
        (ContentStrings.selectAll().where { ContentStrings.status eq DRAFT }.count() +
            ContentErrors.selectAll().where { ContentErrors.status eq DRAFT }.count()).toInt()

    private fun JdbcTransaction.emails(ids: Collection<UUID>): Map<UUID, String> =
        if (ids.isEmpty()) emptyMap() else Users.select(Users.id, Users.email).where { Users.id inList ids.distinct() }
            .mapNotNull { r -> r[Users.email]?.let { r[Users.id] to it } }.toMap()

    private fun ResultRow.text() = ContentTextDto(this[ContentStrings.valueId], this[ContentStrings.valueEn])

    private fun ResultRow.errorText() = ErrorTextDto(
        titleId = this[ContentErrors.titleId], titleEn = this[ContentErrors.titleEn],
        messageId = this[ContentErrors.messageId], messageEn = this[ContentErrors.messageEn],
        buttonId = this[ContentErrors.buttonId], buttonEn = this[ContentErrors.buttonEn],
        illustration = this[ContentErrors.illustration], display = this[ContentErrors.display],
    )

    private fun ResultRow.category() = ContentCategoryDto(
        kind = this[ContentCategories.kind], constant = this[ContentCategories.constant],
        labelId = this[ContentCategories.labelId], labelEn = this[ContentCategories.labelEn],
        icon = this[ContentCategories.icon], color = this[ContentCategories.color],
        sortOrder = this[ContentCategories.sortOrder], hidden = this[ContentCategories.hidden],
    )

    /** Blank fields mean "the app's own". */
    private fun ContentTextDto.cleaned() = ContentTextDto(id?.takeIf { it.isNotBlank() }, en?.takeIf { it.isNotBlank() })

    private fun ErrorTextDto.cleaned() = copy(
        titleId = titleId?.trim()?.ifEmpty { null }, titleEn = titleEn?.trim()?.ifEmpty { null },
        messageId = messageId?.trim()?.ifEmpty { null }, messageEn = messageEn?.trim()?.ifEmpty { null },
        buttonId = buttonId?.trim()?.ifEmpty { null }, buttonEn = buttonEn?.trim()?.ifEmpty { null },
        illustration = illustration?.ifEmpty { null },
    )

    private companion object {
        const val DRAFT = "DRAFT"
        const val PUBLISHED = "PUBLISHED"
    }
}

/**
 * What Karung may edit: the app's string keys with their bundled text, and the illustrations an error screen may
 * show. Made from composeApp by `scripts/export-content-catalog.py` into `content/catalog.json`.
 */
class ContentCatalog(val strings: List<Entry>, val illustrations: List<String>) {
    @Serializable
    data class Entry(val key: String, val area: String, val id: String, val en: String)

    @Serializable
    private data class File(val strings: List<Entry>, val illustrations: List<String>)

    private val byKey = strings.associateBy { it.key }

    fun string(key: String): Entry? = byKey[key]

    companion object {
        val bundled: ContentCatalog by lazy {
            val text = ContentCatalog::class.java.getResourceAsStream("/content/catalog.json")?.bufferedReader()?.readText()
                ?: return@lazy ContentCatalog(emptyList(), emptyList())
            val file = Json { ignoreUnknownKeys = true }.decodeFromString(File.serializer(), text)
            ContentCatalog(file.strings, file.illustrations)
        }
    }
}
