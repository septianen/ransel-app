package app.ransel.feature

import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.feature.itinerary.ActivityDetailViewModel
import app.ransel.feature.itinerary.ActivityFormViewModel
import app.ransel.feature.itinerary.LinkDraft
import app.ransel.feature.itinerary.LinkProblem
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.Role
import app.ransel.stopTestMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val at = Instant.parse("2026-11-13T10:00:00Z")
private const val TRIP_ID = "t1"

private fun linkTrip(role: Role = Role.OWNER) = TripDto(
    id = TRIP_ID, ownerId = "u1", title = "Bali", destination = "Bali",
    startDate = LocalDate(2026, 12, 1), endDate = LocalDate(2026, 12, 4), baseCurrency = "IDR",
    myRole = role, createdAt = at, updatedAt = at,
)

private fun linkItem(id: String = "i1", links: List<String>? = null, bookingUrl: String? = null) = ItineraryItemDto(
    id = id, tripId = TRIP_ID, dayDate = LocalDate(2026, 12, 1), title = "Hotel", category = Category.LODGING,
    links = links, bookingUrl = bookingUrl, sortOrder = 1.0, createdAt = at, updatedAt = at,
)

/** The rules of the "Link" rows of the activity form (spec "Banyak link di aktivitas"). */
class LinkDraftTest {
    private fun draft(vararg texts: String) = LinkDraft.of(texts.toList())

    @Test
    fun startsWithOneEmptyRowAndAddingNeedsAFilledLastRow() {
        var d = LinkDraft()
        assertEquals(1, d.rows.size)
        assertFalse(d.canAdd)
        d = d.change(d.rows[0].key, "booking.com/x")
        assertTrue(d.canAdd)
        d = d.add()
        assertEquals(2, d.rows.size)
        assertFalse(d.canAdd)
    }

    @Test
    fun stopsAtFiveRows() {
        var d = LinkDraft()
        repeat(5) {
            d = d.change(d.rows.last().key, "https://a.example/$it")
            d = d.add()
        }
        assertEquals(5, d.rows.size)
        assertTrue(d.atMax)
        assertFalse(d.canAdd)
        assertEquals(d, d.add())
    }

    @Test
    fun theXRemovesARowButEmptiesTheLastRemainingOne() {
        var d = draft("https://a.example", "https://b.example")
        d = d.remove(d.rows[0].key)
        assertEquals(listOf("https://b.example"), d.rows.map { it.text })
        d = d.remove(d.rows[0].key)
        assertEquals(listOf(""), d.rows.map { it.text })
    }

    @Test
    fun anAddressWithoutASchemeIsFineAndFreeTextBlocksSaving() {
        assertTrue(draft("booking.com/hotel").canSave)
        assertEquals(listOf("https://booking.com/hotel"), draft("  booking.com/hotel ").links())
        val bad = draft("https://ok.example", "javascript:alert(1)", "halo teman")
        assertFalse(bad.canSave)
        assertEquals(LinkProblem.INVALID, bad.problems[bad.rows[1].key])
        assertEquals(LinkProblem.INVALID, bad.problems[bad.rows[2].key])
    }

    @Test
    fun aDuplicateIsMarkedOnTheSecondRowButDoesNotBlockSaving() {
        val d = draft("https://www.a.example/x", "www.a.example/x", "https://b.example")
        // the second row has no scheme, so the shared normalizer makes it the same address as the first
        assertEquals(LinkProblem.DUPLICATE, d.problems[d.rows[1].key])
        assertNull(d.problems[d.rows[0].key])
        assertTrue(d.canSave)
        assertEquals(listOf("https://www.a.example/x", "https://b.example"), d.links())
    }

    @Test
    fun emptyRowsAreDroppedOnSaveAndTheLabelComesFromTheDomain() {
        val d = draft("https://www.instagram.com/hotelkuta", "", "tiket.com/x")
        assertEquals(listOf("https://www.instagram.com/hotelkuta", "https://tiket.com/x"), d.links())
        assertEquals("Instagram", d.labelOf(d.rows[0]))
        assertNull(d.labelOf(d.rows[1]))
        assertEquals("tiket.com", d.labelOf(d.rows[2]))
    }

    @Test
    fun anOldFreeTextLinkStaysVisibleAsAnInvalidRow() {
        val d = LinkDraft.of(emptyList(), legacyText = "tanya resepsionis")
        assertEquals(listOf("tanya resepsionis"), d.rows.map { it.text })
        assertFalse(d.canSave)
        assertEquals(listOf(""), LinkDraft.of(emptyList(), legacyText = "  ").rows.map { it.text })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityLinksTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val store = InMemoryStore().apply { trips.value = listOf(linkTrip()) }
    private val itinerary = InMemoryItineraryRepository(store)

    private fun form(itemId: String? = null) = ActivityFormViewModel(TRIP_ID, itemId, "2026-12-01", InMemoryTripRepository(store), itinerary)

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    @Test
    fun theFormSavesTheListAndKeepsTheFirstLinkInBookingUrl() = runTest {
        val vm = form()
        eventually { !vm.state.value.loading }
        vm.onTitle("Hotel")
        vm.onLinkChange(vm.state.value.links.rows[0].key, "booking.com/hotel/x")
        vm.onLinkAdd()
        vm.onLinkChange(vm.state.value.links.rows[1].key, "https://www.instagram.com/hotelkuta")
        vm.save()
        eventually { store.items.value.isNotEmpty() }
        val saved = store.items.value.single()
        assertEquals(listOf("https://booking.com/hotel/x", "https://www.instagram.com/hotelkuta"), saved.links)
        assertEquals("https://booking.com/hotel/x", saved.bookingUrl)
    }

    @Test
    fun aRowThatIsNotAWebAddressBlocksSaving() = runTest {
        val vm = form()
        eventually { !vm.state.value.loading }
        vm.onTitle("Hotel")
        vm.onLinkChange(vm.state.value.links.rows[0].key, "file:///etc/passwd")
        vm.save()
        assertTrue(store.items.value.isEmpty())
        assertFalse(vm.state.value.links.canSave)
    }

    @Test
    fun editingAnItemFromBeforeTheListStartsFromItsBookingLinkAndClearingSavesAnEmptyList() = runTest {
        store.items.value = listOf(linkItem(links = null, bookingUrl = "https://old.example/booking"))
        val vm = form("i1")
        eventually { !vm.state.value.loading }
        assertEquals(listOf("https://old.example/booking"), vm.state.value.links.rows.map { it.text })
        vm.onLinkRemove(vm.state.value.links.rows[0].key)
        vm.save()
        eventually { store.items.value.single().links != null }
        val saved = store.items.value.single()
        assertEquals(emptyList(), saved.links)
        assertNull(saved.bookingUrl)
    }

    @Test
    fun theDetailListsTheLinksWithLabelAndFullDomainAndOnlyEditorsSeeEdit() = runTest {
        store.items.value = listOf(linkItem(links = listOf("https://www.booking.com/hotel/x", "https://tiket.com/a")))
        val detail = ActivityDetailViewModel(TRIP_ID, "i1", InMemoryTripRepository(store), itinerary)
        eventually { !detail.state.value.loading }
        val s = detail.state.value
        assertEquals(listOf("Booking.com" to "booking.com", "tiket.com" to "tiket.com"), s.links.map { it.label to it.host })
        assertTrue(s.canEdit)

        store.trips.value = listOf(linkTrip(Role.VIEWER))
        eventually { !detail.state.value.canEdit }
        // a viewer still reads and opens the links
        assertEquals(2, detail.state.value.links.size)
    }

    @Test
    fun aDeletedActivityMakesTheDetailGone() = runTest {
        store.items.value = listOf(linkItem(links = listOf("https://a.example")))
        val detail = ActivityDetailViewModel(TRIP_ID, "i1", InMemoryTripRepository(store), itinerary)
        eventually { !detail.state.value.loading && detail.state.value.item != null }
        store.items.value = emptyList()
        eventually { detail.state.value.gone }
        assertTrue(detail.state.value.gone)
    }

    @Test
    fun anItemFromBeforeTheListShowsItsBookingLinkInTheDetail() = runTest {
        store.items.value = listOf(linkItem(links = null, bookingUrl = "https://old.example/booking"))
        val detail = ActivityDetailViewModel(TRIP_ID, "i1", InMemoryTripRepository(store), itinerary)
        eventually { detail.state.value.item != null }
        assertEquals(listOf("old.example"), detail.state.value.links.map { it.host })
    }
}
