package app.ransel.shared.dto

import app.ransel.shared.model.CategoryKind
import app.ransel.shared.model.ContentStatus
import app.ransel.shared.model.ErrorDisplay
import app.ransel.shared.model.HolidayType
import app.ransel.shared.model.InterestChipKind
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// Remote content (admin-spec stage D). Karung edits it. Since 3 Nov 2026 the apps only read categories from
// GET /content; error copy comes with each error response (ApiError.text), and screen texts are the apps' own.
// Rules: domain/RemoteContent.kt.

// ── For the apps ───────────────────────────────────────────────────────────

/**
 * GET /content: [categories] that were restyled. [version] goes up with every change. [strings] and [errors] are
 * always empty since 3 Nov 2026 (kept so older builds still parse it): screen texts are the apps' own, error copy
 * comes in `ApiError.text`. [holidays] overrides the apps' bundled list (`domain/Holidays.kt`) when not empty: it is
 * the full list of the years it covers, so the apps replace the bundled days of those years and keep the others.
 */
@Serializable
data class ContentDto(
    val version: Int,
    val strings: Map<String, Map<String, String>> = emptyMap(),
    val errors: List<ContentErrorDto> = emptyList(),
    val categories: List<ContentCategoryDto> = emptyList(),
    val holidays: List<HolidayDto> = emptyList(),
)

/** One day off (SKB 3 Menteri): [name] is the official Indonesian name, shown in the date picker. */
@Serializable
data class HolidayDto(
    val date: LocalDate,
    val name: String,
    val type: HolidayType,
)

/** Title, message and button for an error code; null fields fall back to the app's own copy. */
@Serializable
data class ErrorTextDto(
    val titleId: String? = null,
    val titleEn: String? = null,
    val messageId: String? = null,
    val messageEn: String? = null,
    val buttonId: String? = null,
    val buttonEn: String? = null,
    /** A drawable of the app (`ic_confuse`, ...), from [AdminContentErrorsDto.illustrations]; null = the screen's own. */
    val illustration: String? = null,
    val display: ErrorDisplay? = null,
) {
    val isEmpty: Boolean
        get() = listOf(titleId, titleEn, messageId, messageEn, buttonId, buttonEn, illustration).all { it.isNullOrBlank() } && display == null
}

@Serializable
data class ContentErrorDto(val code: String, val text: ErrorTextDto)

/**
 * How one category looks. [constant] is a `Category` name (ITINERARY, EXPENSE) or a `PackingCategory` name
 * (PACKING); categories cannot be added, only relabelled, restyled, reordered or hidden. Null fields = the app's own.
 */
@Serializable
data class ContentCategoryDto(
    val kind: CategoryKind,
    val constant: String,
    val labelId: String? = null,
    val labelEn: String? = null,
    /** A Material Symbols name from [AdminContentCategoriesDto.icons]. */
    val icon: String? = null,
    /** An IRIS accent name from [AdminContentCategoriesDto.colors]. */
    val color: String? = null,
    val sortOrder: Int,
    val hidden: Boolean = false,
)

// ── Karung: error messages ─────────────────────────────────────────────────

@Serializable
data class AdminContentErrorDto(
    val code: String,
    val published: ErrorTextDto? = null,
    val draft: ErrorTextDto? = null,
    val status: ContentStatus,
    /** How often the server answered with this code today (WIB), from the error counts. */
    val countToday: Int = 0,
    val updatedByEmail: String? = null,
    val updatedAt: Timestamp? = null,
)

/** Every `ErrorCode` (`ErrorCode.ALL`); [illustrations] = what an error screen may show. */
@Serializable
data class AdminContentErrorsDto(
    val version: Int,
    val drafts: Int,
    val errors: List<AdminContentErrorDto>,
    val illustrations: List<String>,
)

/**
 * PUT /admin/content/errors: save a draft for [code]; an empty [text] = back to the app's copy; [discard] = drop the
 * draft. 422: `code` UNKNOWN, a text field PLACEHOLDER_MISMATCH (only {cost}, {available}, {resetsAt}, {maxBytes},
 * {usedBytes}, {limitBytes}) or TOO_LONG, `illustration` UNKNOWN.
 */
@Serializable
data class UpdateContentErrorRequest(
    val code: String,
    val text: ErrorTextDto = ErrorTextDto(),
    val discard: Boolean = false,
)

// ── Karung: categories ─────────────────────────────────────────────────────

/** A category with the app's own look next to it. */
@Serializable
data class AdminContentCategoryDto(
    val category: ContentCategoryDto,
    val defaultLabelId: String,
    val defaultLabelEn: String,
    val defaultIcon: String,
    val defaultColor: String,
)

@Serializable
data class AdminContentCategoriesDto(
    val version: Int,
    val categories: List<AdminContentCategoryDto>,
    val icons: List<String>,
    val colors: List<String>,
)

/**
 * PUT /admin/content/categories: every category of [kind], in order. Published at once ("Simpan dan terbitkan"),
 * version + 1, audited. 422: `categories` MUST_LIST_ALL, `icon`/`color` UNKNOWN, `label` TOO_LONG.
 */
@Serializable
data class UpdateContentCategoriesRequest(
    val kind: CategoryKind,
    val categories: List<ContentCategoryDto>,
)

// ── Karung: destination interests ──────────────────────────────────────────

/** A row of "Minat khas": one destination, month of the year and language, from the AI cache and/or a curation. */
@Serializable
data class AdminInterestSummaryDto(
    /** As stored: trimmed, lower case. */
    val destination: String,
    /** 1–12. */
    val month: Int,
    val language: String,
    val chips: Int,
    val curated: Boolean,
    /** The newest AI answer for this month, if any, and when it runs out. */
    val aiCreatedAt: Timestamp? = null,
    val aiExpiresAt: Timestamp? = null,
)

@Serializable
data class InterestChipDto(
    val label: String,
    val kind: InterestChipKind,
)

/**
 * The curation of one destination, month and language. GET merges the AI chips with the saved curation; PUT saves
 * [chips] (labels at most 40 characters, at most 12) and `GET /explore/suggestions` uses them at once: pinned and
 * manual chips first, then AI chips that are not hidden.
 */
@Serializable
data class AdminInterestCurationDto(
    val destination: String,
    val month: Int,
    val language: String,
    val chips: List<InterestChipDto>,
    val aiCreatedAt: Timestamp? = null,
    val aiExpiresAt: Timestamp? = null,
    val curatedByEmail: String? = null,
    val curatedAt: Timestamp? = null,
)

/** POST /admin/content/publish. */
@Serializable
data class ContentPublishResultDto(
    val version: Int,
    val published: Int,
)
