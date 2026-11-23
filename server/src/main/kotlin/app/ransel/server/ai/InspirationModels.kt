package app.ransel.server.ai

import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.InspirationQuestionDto
import app.ransel.shared.dto.InspirationResultDto
import app.ransel.shared.model.InspirationChipKey
import app.ransel.shared.model.InspirationDistance
import app.ransel.shared.model.InspirationRequirement
import app.ransel.shared.model.InspirationSlot
import app.ransel.shared.model.Interest
import kotlinx.serialization.Serializable

/*
 * Explore Inspirasi on the server: the interview state kept in `inspiration_sessions.state` and the shapes the LLM is
 * asked for. None of this is on the wire to the app; the app sees `shared/dto/InspirationDtos.kt` only.
 */

/** One answered question. [answer] is the option title or the "Lainnya" text, as the user saw or typed it. */
@Serializable
data class InspirationAnswer(
    val slot: InspirationSlot,
    val requirement: InspirationRequirement? = null,
    val question: String,
    val answer: String,
    val optionId: String? = null,
    /** The "Kota mana?" follow-up of "Dalam kota". */
    val city: Boolean = false,
)

/** Why a question is on screen: the interview itself, "Tanya lagi", or a mandatory follow-up of a chip edit. */
@Serializable
enum class InspirationMode { INTERVIEW, ASK_AGAIN, CHIP_EDIT }

@Serializable
data class InspirationState(
    val interests: List<Interest>,
    val text: String? = null,
    val answers: List<InspirationAnswer> = emptyList(),
    /** Slots answered or skipped. */
    val done: Set<InspirationSlot> = emptySet(),
    /** Slots skipped because the hero text (or an earlier answer) already said it; the progress bar marks them done. */
    val skipped: List<InspirationSlot> = emptyList(),
    val answeredRequirements: Set<InspirationRequirement> = emptySet(),
    val skillActivity: Boolean = false,
    val distance: InspirationDistance? = null,
    val city: String? = null,
    val cityAsked: Boolean = false,
    /** The hero text was read by the AI once (it may answer DISTANCE, whose question has fixed options). */
    val textRead: Boolean = false,
    /**
     * The place the hero text names ("Bangkok", "Bali"), read with the facts before DISTANCE (plan
     * inspiration-hero-destination v2): offered as the first DISTANCE option "<place> dan sekitarnya". Null = none or the
     * read failed. [heroInIndonesia] null = not known.
     */
    val heroDestination: String? = null,
    val heroInIndonesia: Boolean? = null,
    /** The user picked "<place> dan sekitarnya": the cards stay in and around it. Any other DISTANCE option leaves it null. */
    val destination: String? = null,
    /** "Belum punya buddy": the results offer "Cari buddy". */
    val noBuddy: Boolean = false,
    /** Questions shown so far (the 1-based number of the question on screen once one is shown). */
    val stepsAsked: Int = 0,
    val current: InspirationQuestionDto? = null,
    val mode: InspirationMode = InspirationMode.INTERVIEW,
    /** Profile chips the user changed on the results header. */
    val overrides: Map<InspirationChipKey, String> = emptyMap(),
    /**
     * The state before each interview answer (newest last, without their own history), so "Back" puts the previous
     * question on screen again exactly as it was: no AI call and no new wording.
     */
    val history: List<InspirationState> = emptyList(),
)

/** A card plus the place whose photo it shows; the photo link is signed again whenever the card is sent. */
@Serializable
data class StoredInspirationCard(val card: InspirationCardDto, val photoPlaceId: String? = null)

/** What `inspiration_sessions.result` holds. [result] carries cards without photo links. */
@Serializable
data class StoredInspirationResult(
    val result: InspirationResultDto,
    val cards: List<StoredInspirationCard>,
    val surprise: StoredInspirationCard? = null,
)

// ── LLM ─────────────────────────────────────────────────────────────────────

/** Asks the LLM for the question of [slot] (or of [requirement] when that mandatory question is due). */
data class InspirationQuestionInput(
    val language: String,
    val interests: List<Interest>,
    val text: String?,
    val answers: List<InspirationAnswer>,
    val slot: InspirationSlot,
    val requirement: InspirationRequirement? = null,
    val distance: InspirationDistance? = null,
    val city: String? = null,
    val skillActivity: Boolean = false,
    /** "Tanya lagi": one more question that should change the next cards. */
    val askAgain: Boolean = false,
)

@Serializable
data class LlmInspirationOption(
    val id: String = "",
    val title: String = "",
    val subtitle: String? = null,
    /** Slot 2: the interests (constant names) this combination uses. */
    val interests: List<String> = emptyList(),
)

/** The question plus what the model read from every answer and the hero text so far. */
@Serializable
data class LlmInspirationQuestion(
    val question: String = "",
    val options: List<LlmInspirationOption> = emptyList(),
    /** Diving, freediving, climbing, technical hiking: level and buddy become mandatory. */
    val skillActivity: Boolean = false,
    /** SAME_CITY / DOMESTIC / ABROAD / ANY when the user already said it. */
    val distance: String? = null,
    val city: String? = null,
    /** Slots (constant names) the user already answered through the text or earlier answers. */
    val answeredSlots: List<String> = emptyList(),
    /** Requirements (LEVEL, BUDGET) the user already answered through the text. */
    val answeredRequirements: List<String> = emptyList(),
    /** The place the user wants to go when the hero text names one ("Bangkok"); null = no place. */
    val destination: String? = null,
    /** Whether [destination] is in Indonesia. */
    val inIndonesia: Boolean? = null,
)

/** Asks the LLM for cards. [refresh] set = rewrite that one card with current information (the "Perbarui" button). */
data class InspirationCardsInput(
    val language: String,
    val interests: List<Interest>,
    val text: String?,
    val answers: List<InspirationAnswer>,
    val distance: InspirationDistance?,
    val city: String?,
    val skillActivity: Boolean,
    val needsVisaNote: Boolean,
    /** Main cards wanted (3–5); "Tanya lagi" asks for 3 more. */
    val count: Int,
    /** Regions already shown in this session: new cards and the surprise must not repeat them. */
    val avoidRegions: List<String> = emptyList(),
    val overrides: Map<InspirationChipKey, String> = emptyMap(),
    val refresh: InspirationCardDto? = null,
    /** "<place> dan sekitarnya" was picked: every region, the surprise too, is in or near this place. */
    val destination: String? = null,
)

@Serializable
data class LlmInspirationChip(val key: String = "", val value: String = "")

@Serializable
data class LlmInspirationCard(
    val activity: String = "",
    val region: String = "",
    val countryCode: String? = null,
    val why: String = "",
    val access: String? = null,
    val bestSeason: String? = null,
    val level: String? = null,
    val requirements: List<String> = emptyList(),
    /** Per person, in rupiah. */
    val costMinIdr: Long? = null,
    val costMaxIdr: Long? = null,
    val visaNote: String? = null,
    val durationDays: Int? = null,
)

@Serializable
data class LlmInspirationCards(
    val profile: List<LlmInspirationChip> = emptyList(),
    val cards: List<LlmInspirationCard> = emptyList(),
    val surprise: LlmInspirationCard? = null,
)
