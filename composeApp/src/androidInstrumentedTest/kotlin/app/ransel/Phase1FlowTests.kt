package app.ransel

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.center
import app.ransel.data.fake.InMemoryExpenseRepository
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPackingRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.feature.expenses.ExpensesTab
import app.ransel.feature.expenses.ExpensesViewModel
import app.ransel.feature.itinerary.ActivityFormScreen
import app.ransel.feature.itinerary.ActivityFormViewModel
import app.ransel.feature.itinerary.ItineraryTab
import app.ransel.feature.itinerary.ItineraryViewModel
import app.ransel.feature.packing.PackingTab
import app.ransel.feature.packing.PackingViewModel
import app.ransel.feature.tripform.TripFormScreen
import app.ransel.feature.tripform.TripFormViewModel
import app.ransel.platform.applyAppLanguage
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.PackingCategory
import app.ransel.ui.theme.IrisTheme
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 1 main flows (F1.8): create trip, add activity, reorder, tick packing, record expense.
 * Screens run against the in-memory repositories; the UI under test is the real Compose code.
 */
class Phase1FlowTests {
    @get:Rule val rule = createComposeRule()

    private val store = InMemoryStore()
    private val trips = InMemoryTripRepository(store)
    private val itinerary = InMemoryItineraryRepository(store)
    private val packing = InMemoryPackingRepository(store)
    private val expenses = InMemoryExpenseRepository(store)
    private val prefs = InMemoryPreferencesRepository()

    @Before fun english() = applyAppLanguage("en")

    /** Forms load their data asynchronously; clicking Save while still loading would be ignored. */
    private fun waitUntilEnabled(text: String) = rule.waitUntil(5_000) {
        rule.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }

    private fun seedTrip(): TripDto = runBlocking {
        trips.create(
            CreateTripRequest(
                id = "trip-1", title = "Bali 4 hari", destination = "Bali",
                startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23),
                baseCurrency = "IDR", budgetMinor = 5_000_000,
            ),
        )
        store.trips.value.single { it.id == "trip-1" }
    }

    /** Phase 1 runs signed out: no session, so the expenses tab never calls the API it is given. */
    private fun guestSession() = SessionManager(
        object : TokenStore {
            override suspend fun read(): StoredSession? = null
            override suspend fun write(session: StoredSession) = Unit
            override suspend fun clear() = Unit
        },
    ) { error("not used signed out") }

    private val unusedApi = RanselApi(createApiHttpClient(), origin = "http://localhost")

    @Test fun createTrip() {
        var savedId: String? = null
        val vm = TripFormViewModel(null, trips, prefs)
        rule.setContent {
            IrisTheme {
                TripFormScreen(
                    tripId = null, onClose = {}, onSaved = { savedId = it }, onDeleted = {},
                    viewModel = vm,
                )
            }
        }
        // a new trip starts without dates (F21.6); the range picker itself is M3's
        vm.onDatesChange(kotlinx.datetime.LocalDate(2027, 1, 10), kotlinx.datetime.LocalDate(2027, 1, 13))
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Bali 4 hari")
        waitUntilEnabled("Create trip")
        rule.onAllNodesWithText("Create trip").onLast().performClick()
        rule.waitUntil(5_000) { savedId != null }

        assertNotNull(savedId)
        assertEquals(listOf("Bali 4 hari"), store.trips.value.map { it.title })
    }

    @Test fun addActivity() {
        val trip = seedTrip()
        var closed = false
        rule.setContent {
            IrisTheme {
                ActivityFormScreen(
                    tripId = trip.id, itemId = null, initialDay = "2026-11-20", onClose = { closed = true },
                    viewModel = ActivityFormViewModel(trip.id, null, "2026-11-20", trips, itinerary),
                )
            }
        }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Pantai Melasti")
        waitUntilEnabled("Save activity")
        rule.onNodeWithText("Save activity").performClick()
        rule.waitUntil(5_000) { store.items.value.isNotEmpty() && closed }

        val saved = store.items.value.single()
        assertEquals("Pantai Melasti", saved.title)
        assertEquals(LocalDate(2026, 11, 20), saved.dayDate)
        assertTrue(closed)
    }

    @Test fun reorderActivitiesPacksTheDay() {
        val trip = seedTrip()
        val day = LocalDate(2026, 11, 20)
        runBlocking {
            fun item(id: String, start: String, end: String, order: Double) = ItineraryItemInput(
                id = id, dayDate = day, startTime = LocalTime.parse(start), endTime = LocalTime.parse(end),
                title = id, category = Category.ATTRACTION, sortOrder = order,
            )
            itinerary.save(trip.id, item("Melasti", "09:00", "10:30", 1.0))
            itinerary.save(trip.id, item("Uluwatu", "10:30", "12:30", 2.0))
            itinerary.save(trip.id, item("Lunch", "12:30", "13:30", 3.0))
        }
        rule.setContent {
            IrisTheme {
                ItineraryTab(
                    trip = trip, onAddActivity = {}, onEditActivity = {},
                    viewModel = ItineraryViewModel(trip, itinerary, prefs),
                )
            }
        }
        val first = store.items.value.first { it.title == "Melasti" }
        try {
            rule.waitUntil(5_000) {
                rule.onAllNodesWithText("Melasti").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            rule.onRoot().printToLog("DBGTREE")
            throw e
        }
        // no handle: a long press starts the drag
        rule.onNodeWithText("Melasti").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(8) { moveBy(Offset(0f, 60f)) } // 480 px: past the centers of "Uluwatu" and "Lunch" (rows are ~190 px apart)
            up()
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Change order and times?").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Change schedule").performClick()
        rule.waitUntil(5_000) { store.items.value.any { it.title == "Melasti" && it.startTime == LocalTime(12, 0) } }

        val ordered = store.items.value.sortedBy { it.sortOrder }.map { it.title to it.startTime.toString() }
        assertEquals(listOf("Uluwatu" to "09:00", "Lunch" to "11:00", "Melasti" to "12:00"), ordered)
    }

    @Test fun tickPackingItem() {
        val trip = seedTrip()
        runBlocking {
            packing.add(trip.id, CreatePackingItemRequest("p1", PackingCategory.CLOTHING, "Kaos", 3, 1.0))
        }
        rule.setContent {
            IrisTheme { PackingTab(trip = trip, viewModel = PackingViewModel(trip.id, packing)) }
        }
        rule.onNodeWithText("Kaos").performClick()
        rule.waitForIdle()

        assertTrue(store.packing.value.single().isPacked)
        rule.onNodeWithText("1 of 1 items").assertExists()
    }

    @Test fun recordExpense() {
        val trip = seedTrip()
        rule.setContent {
            IrisTheme { ExpensesTab(trip = trip, viewModel = ExpensesViewModel(trip, expenses, unusedApi, guestSession())) }
        }
        rule.onNodeWithContentDescription("Record expense").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("85000")
        waitUntilEnabled("Save")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5_000) { store.expenses.value.isNotEmpty() }

        val saved = store.expenses.value.single()
        assertEquals(85_000L, saved.amountMinor)
        assertEquals("IDR", saved.currency)
    }
}
