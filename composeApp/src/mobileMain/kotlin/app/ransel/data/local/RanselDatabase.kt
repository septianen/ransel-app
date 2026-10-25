package app.ransel.data.local

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        TripEntity::class,
        ItineraryItemEntity::class,
        PackingItemEntity::class,
        ExpenseEntity::class,
        PreferenceEntity::class,
    ],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // expenses.splitsJson
        AutoMigration(from = 2, to = 3), // trips.myRole
        AutoMigration(from = 3, to = 4), // expenses.payersJson
        AutoMigration(from = 4, to = 5, spec = Migration4To5::class), // paidByUserId -> paidByMemberId, member ids in the JSON
    ],
)
@ConstructedBy(RanselDatabaseConstructor::class)
abstract class RanselDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun itineraryDao(): ItineraryDao
    abstract fun packingDao(): PackingDao
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
