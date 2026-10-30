package app.ransel.shared.domain

import app.ransel.shared.dto.ErrorTextDto
import app.ransel.shared.dto.InterestChipDto
import app.ransel.shared.model.CategoryKind
import app.ransel.shared.model.InterestChipKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteContentTest {
    @Test fun aStringKeepsThePlaceholdersOfItsBundledText() {
        assertTrue(placeholdersMatch("Halo, %1\$s", "Hai %1\$s!"))
        assertEquals(mapOf("id" to "PLACEHOLDER_MISMATCH"), validateContentString("Halo, %1\$s", "Hi, %1\$s", "Hai!", null))
        assertEquals(mapOf("en" to "PLACEHOLDER_MISMATCH"), validateContentString("%1\$d kredit", "%1\$d credits", null, "%2\$d credits"))
        assertEquals(emptyMap(), validateContentString("Belum ada trip", "No trips yet", "Ransel masih kosong", null))
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
}
