package app.ransel.shared.dto

import app.ransel.shared.model.InspirationChipKey
import app.ransel.shared.model.InspirationQuestionSource
import app.ransel.shared.model.InspirationRequirement
import app.ransel.shared.model.InspirationSlot
import app.ransel.shared.model.InspirationStatus
import app.ransel.shared.model.Interest
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/*
 * Explore Inspirasi: an AI interview for a user without a destination that ends in an activity type + region
 * (PRD v0.8, `docs/PRD.md` "Explore Inspirasi"). The server keeps the session (24 h); the app only shows what it is told.
 * Interests are the existing [Interest] enum: the chip labels changed, the constants did not.
 * Text the user typed ([StartInspirationRequest.text], [InspirationAnswerRequest.otherText]) is limited by
 * `InspirationLimits` and never trusted as an instruction. Texts the server writes follow Accept-Language (id or en).
 * Cost and price numbers are never in the app: they come from `CreditCostsDto.inspiration` and [InspirationSessionDto].
 */

/** POST /explore/inspiration/sessions. */
@Serializable
data class StartInspirationRequest(
    /** UUID made by the app. A repeat with the same id answers the same session (no second AI call, no second charge). */
    val id: String,
    /** 0–5 chips. 422 `interests` = TOO_MANY above 5. */
    val interests: List<Interest> = emptyList(),
    /** The hero field, max 120 characters (422 `text` = TOO_LONG). Details in it ("freediving pemula dekat Bali") skip those slots. */
    val text: String? = null,
)

/** POST .../answers. Exactly one of the two; 422 `answer` = REQUIRED / TOO_MANY, `otherText` = TOO_LONG (max 80). */
@Serializable
data class InspirationAnswerRequest(
    val optionId: String? = null,
    /** "Lainnya". */
    val otherText: String? = null,
)

/** PATCH .../profile: change one part of the profile header. [value] is an option id from the last question or free text. */
@Serializable
data class InspirationEditChipRequest(
    val key: InspirationChipKey,
    val value: String,
)

/** One option of a question: a title, an optional subtitle, no icon and no emoji. */
@Serializable
data class InspirationOptionDto(
    /** Stable within the question; not the label. */
    val id: String,
    val title: String,
    /** Slot 2: the interests a combination comes from ("foto + kuliner"). */
    val subtitle: String? = null,
    /** Slot 2: the chosen interests this option combines (the server drops options with fewer than two). */
    val interests: List<Interest> = emptyList(),
)

/** The question on screen. "Lainnya" is always allowed through [allowOther]. */
@Serializable
data class InspirationQuestionDto(
    val slot: InspirationSlot,
    val text: String,
    /** 3–5 options. */
    val options: List<InspirationOptionDto>,
    val allowOther: Boolean = true,
    /** "Sudah cukup, lihat inspirasi": from step 3, and only when no mandatory question is left. */
    val canFinishEarly: Boolean = false,
    val source: InspirationQuestionSource = InspirationQuestionSource.LLM,
    /** The mandatory rule this question answers, if any (LEVEL, BUDDY, BUDGET). */
    val requirement: InspirationRequirement? = null,
    /** Steps answered so far; the progress bar has 5 segments for slots 2–6. */
    val stepsDone: Int = 0,
    /** Slots the AI skipped because the user already answered them; those segments are marked done. */
    val skippedSlots: List<InspirationSlot> = emptyList(),
)

/** One part of the profile header; [value] is the label the user sees ("Pemula"). */
@Serializable
data class InspirationChipDto(
    val key: InspirationChipKey,
    val value: String,
)

/** Cost range per person in the minor unit of [currency]. Always shown as "perkiraan AI, cek lagi sebelum berangkat". */
@Serializable
data class InspirationCostRangeDto(
    val minMinor: Long,
    val maxMinor: Long,
    /** ISO 4217. */
    val currency: String,
)

/** An activity type + region. Never an operator, shop or restaurant name. */
@Serializable
data class InspirationCardDto(
    /** Unique within its session. */
    val id: String,
    /** "Memberi makan orang utan". */
    val activity: String,
    /** "Tanjung Puting, Kalteng". */
    val region: String,
    /** ISO 3166-1 alpha-2; the trip hand-off uses it for an overseas trip. */
    val countryCode: String? = null,
    /** One line: why it fits. */
    val why: String,
    /** Chips: Akses / Musim terbaik / Level. */
    val access: String? = null,
    val bestSeason: String? = null,
    val level: String? = null,
    /** The detail sheet: licence, certification, and so on. */
    val requirements: List<String> = emptyList(),
    val costRange: InspirationCostRangeDto? = null,
    /** Passport/visa line for an overseas card; an AI estimate. */
    val visaNote: String? = null,
    /** The fixed safety line of a skill activity; the app shows it as it is. */
    val safetyLine: String? = null,
    /** Fresh signed path like `RecommendationItem.photoUrl`; null = the fallback block (accent + explore icon + region). */
    val photoUrl: String? = null,
    /** "Foto: Google". */
    val photoAttribution: String? = null,
    /** A suggestion for the new trip's length; never stored on a trip. */
    val durationDays: Int? = null,
    /** The one unexpected region for the same interest. */
    val surprise: Boolean = false,
)

/** The cards of a session. Cards from "Tanya lagi" are appended, never replace the earlier ones. */
@Serializable
data class InspirationResultDto(
    val profile: List<InspirationChipDto> = emptyList(),
    /** 3–5 cards, then more after each "Tanya lagi". */
    val cards: List<InspirationCardDto> = emptyList(),
    val surprise: InspirationCardDto? = null,
    /** "Cari buddy": only when the user said they have no buddy. */
    val showFindBuddy: Boolean = false,
    val createdAt: Timestamp,
)

/** Every session call answers this: the question to show, or the result, plus what the user can still do for free. */
@Serializable
data class InspirationSessionDto(
    val id: String,
    val status: InspirationStatus,
    val question: InspirationQuestionDto? = null,
    val result: InspirationResultDto? = null,
    /** "Tanya lagi" left for free (1 per session). */
    val askAgainFreeLeft: Int = 0,
    /** Chip edits left for free (3 per session). */
    val chipEditsFreeLeft: Int = 0,
    /** What the next "Tanya lagi" or chip edit costs once the free ones are used (the next-batch price). */
    val extraCost: Int = 0,
    /** A session resumes until this moment (24 h after it started). */
    val expiresAt: Timestamp,
    /** Filled after a charge. */
    val credits: CreditsDto? = null,
)

/** POST /explore/inspiration/saved. */
@Serializable
data class SaveIdeaRequest(
    /** UUID made by the app. */
    val id: String,
    val sessionId: String,
    val cardId: String,
)

/** A card saved to the account ("Ide tersimpan"), with the profile it came from. Never shown in a trip list. */
@Serializable
data class SavedIdeaDto(
    val id: String,
    val card: InspirationCardDto,
    val profile: List<InspirationChipDto> = emptyList(),
    val savedAt: Timestamp,
    /** Last time the card was built or refreshed; older than 60 days = "Perkiraan AI · dibuat <bulan>" + "Perbarui". */
    val refreshedAt: Timestamp,
    /** Earliest moment "Perbarui" works again (once per card per 24 h); null = now. */
    val canRefreshAt: Timestamp? = null,
)

/** GET /explore/inspiration/saved. */
@Serializable
data class SavedIdeasResponse(
    val ideas: List<SavedIdeaDto>,
    /** 30. */
    val max: Int = 30,
)

/** [removedOldestId] is set when this save was the 31st: the oldest idea was removed ("Ide terlama dihapus · Urungkan"). */
@Serializable
data class SaveIdeaResponse(
    val idea: SavedIdeaDto,
    val removedOldestId: String? = null,
)

/** POST /explore/inspiration/buddy ("Kabari saya"). The server stores user + interest + consent time. */
@Serializable
data class BuddyInterestRequest(
    val sessionId: String? = null,
    val interest: Interest? = null,
)
