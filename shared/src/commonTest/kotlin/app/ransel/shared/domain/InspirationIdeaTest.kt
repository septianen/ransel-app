package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class InspirationIdeaTest {
    @Test
    fun theRegionIsCutFromTheTitleOnlyWhenItIsTheRegion() {
        val region = "Taman Nasional Komodo, Nusa Tenggara Timur"
        assertEquals("Marine wildlife photography", ideaActivityTitle("Marine wildlife photography · Taman Nasional Komodo, Nusa Tenggara Timur", region))
        assertEquals("Marine wildlife photography", ideaActivityTitle("Marine wildlife photography · Taman Nasional Komodo", region))
        assertEquals("Marine wildlife photography", ideaActivityTitle("  Marine wildlife photography ", region))
        assertEquals("Snorkeling - pemula", ideaActivityTitle("Snorkeling - pemula", region))
    }
}
