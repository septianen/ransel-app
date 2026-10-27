package app.ransel.feature

import app.ransel.data.remote.ApiFailure
import app.ransel.feature.group.GroupUiState
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** "Ringkasan" leaves out voting, money and members only when the server says the user is alone in the trip. */
class OverviewTabTest {
    private val joined = Instant.parse("2026-10-01T00:00:00Z")
    private fun member(id: String) = TripMemberDto("t", id, id, id, role = Role.EDITOR, joinedAt = joined)

    @Test
    fun aTripOfOneIsSolo() {
        assertTrue(GroupUiState(loading = false, members = listOf(member("me"))).solo)
    }

    @Test
    fun anyoneElseMakesItAGroupTrip() {
        assertFalse(GroupUiState(loading = false, members = listOf(member("me"), member("dinda"))).solo)
    }

    @Test
    fun unknownIsNotSolo() {
        assertFalse(GroupUiState(loading = true).solo, "still loading: keep the group parts")
        assertFalse(GroupUiState(loading = false, failure = ApiFailure("NETWORK", "offline")).solo, "offline: we cannot tell")
    }
}
