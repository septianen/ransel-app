package app.ransel.data.local

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO // Dispatchers.IO is an extension outside the JVM

@Database(
    entities = [
        TripEntity::class,
        ItineraryItemEntity::class,
        PackingItemEntity::class,
        PersonalItemEntity::class,
        ExpenseEntity::class,
        PreferenceEntity::class,
    ],
    version = 10,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // expenses.splitsJson
        AutoMigration(from = 2, to = 3), // trips.myRole
        AutoMigration(from = 3, to = 4), // expenses.payersJson
        AutoMigration(from = 4, to = 5, spec = Migration4To5::class), // paidByUserId -> paidByMemberId, member ids in the JSON
        AutoMigration(from = 5, to = 6), // expenses.paidFromKitty, isEstimate, itemsJson
        AutoMigration(from = 6, to = 7), // trips.destPlaceId, destLat, destLng, destViewportJson
        AutoMigration(from = 7, to = 8), // trips.countryCode, localCurrency, timeZone; expenses.fxSource, fxDate
        AutoMigration(from = 8, to = 9), // packing_items.assigneeMemberId, personal_items
        AutoMigration(from = 9, to = 10, spec = Migration9To10::class), // drop expenses.paidFromKitty (kitty removed)
    ],
)
@ConstructedBy(RanselDatabaseConstructor::class)
abstract class RanselDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun itineraryDao(): ItineraryDao
    abstract fun packingDao(): PackingDao
    abstract fun personalItemDao(): PersonalItemDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun syncDao(): SyncDao
}

// Room generates the actuals per target (KSP).
@Suppress("KotlinNoActualForExpect")
expect object RanselDatabaseConstructor : RoomDatabaseConstructor<RanselDatabase> {
    override fun initialize(): RanselDatabase
}

const val DATABASE_FILE = "ransel.db"

/** Same driver and context on Android and iOS; the platform only supplies the builder (path). */
fun RoomDatabase.Builder<RanselDatabase>.configureAndBuild(): RanselDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
