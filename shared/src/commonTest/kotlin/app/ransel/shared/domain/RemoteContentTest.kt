package app.ransel.shared.domain

import app.ransel.shared.dto.ErrorTextDto
import app.ransel.shared.dto.InterestChipDto
import app.ransel.shared.model.CategoryKind
import app.ransel.shared.model.InterestChipKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteContentTest {
    @Test fun anErrorResponseCarriesTheCopyInItsLanguageWithTheDetailsFilledIn() {
        val text = ErrorTextDto(
            titleId = "Kredit belum cukup", titleEn = "Not enough credits",
            messageId = "Butuh {cost} kredit, kamu punya {available}.", illustration = "ic_confuse",
        )
        val id = text.forLanguage("id", mapOf("cost" to "3", "available" to "1"))!!
        assertEquals("Kredit belum cukup", id.title)
        assertEquals("Butuh 3 kredit, kamu punya 1.", id.message)
        assertEquals("ic_confuse", id.illustration)
        val en = text.forLanguage("en", null)!!
        assertEquals("Not enough credits", en.title)
        assertNull(en.message)
        assertNull(ErrorTextDto(titleId = "Hanya Indonesia").forLanguage("en", null))
        assertEquals("Butuh kredit.", fillErrorDetails("Butuh {cost} kredit.", emptyMap()))
    }

    @Test fun errorMessagesUseOnlyTheDetailsTheServerSends() {
        val ok = ErrorTextDto(messageId = "Butuh {cost} kredit, kamu punya {available}.", illustration = "ic_confuse")
        assertEquals(emptyMap(), validateErrorText(ok, listOf("ic_confuse")))
        val bad = ErrorTextDto(messageEn = "Hello {name}", illustration = "ic_nope")
        assertEquals(mapOf("messageEn" to "PLACEHOLDER_MISMATCH", "illustration" to "UNKNOWN"), validateErrorText(bad, listOf("ic_confuse")))
    }

    @Test fun curatedChipsComeFirstAndHiddenOnesNeverShow() {
        val chips = listOf(
            InterestChipDto("Pura dan upacara", InterestChipKind.PINNED),
            InterestChipDto("Melukis batik", InterestChipKind.MANUAL),
            InterestChipDto("Wisata malam", InterestChipKind.HIDDEN),
        )
        val ai = listOf("Pantai selatan", "wisata malam", "Pura dan upacara", "Snorkeling", "Sawah", "Beach club")
        assertEquals(listOf("Pura dan upacara", "Melukis batik", "Pantai selatan", "Snorkeling", "Sawah"), curatedInterests(ai, chips))
    }

    @Test fun categoryDefaultsFollowTheApp() {
        assertEquals("expense_category_food", categoryLabelKey(CategoryKind.EXPENSE, "FOOD"))
        assertEquals("local_activity" to "blue", defaultCategoryStyle(CategoryKind.EXPENSE, "ATTRACTION"))
        assertEquals(6, categoryConstants(CategoryKind.PACKING).size)
    }

    @Test fun aBuiltinTemplateNeedsANameAndSensibleItems() {
        val ok = listOf(app.ransel.shared.dto.TemplateItem(app.ransel.shared.model.PackingCategory.CLOTHING, "Kaos", 3))
        assertEquals(emptyMap(), validateBuiltinTemplate("Liburan pantai", ok))
        assertEquals(mapOf("name" to "REQUIRED", "items" to "MUST_NOT_BE_EMPTY"), validateBuiltinTemplate(" ", emptyList()))
        assertEquals(mapOf("name" to "TOO_LONG"), validateBuiltinTemplate("x".repeat(MAX_TEMPLATE_NAME + 1), ok))
        val bad = listOf(
            app.ransel.shared.dto.TemplateItem(app.ransel.shared.model.PackingCategory.OTHER, " "),
            app.ransel.shared.dto.TemplateItem(app.ransel.shared.model.PackingCategory.OTHER, "Botol", 0),
        )
        assertEquals(mapOf("items.0" to "NAME_REQUIRED", "items.1" to "QUANTITY_OUT_OF_RANGE"), validateBuiltinTemplate("Gunung", bad))
    }
}
