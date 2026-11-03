package app.ransel.data.local

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
    // Squashed to 1 on 3 Nov 2026 before the first store release (owner); earlier builds must be reinstalled.
    // From here on, raise the version with an AutoMigration for every schema change.
    version = 1,
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
        // A database from a newer schema (a restored backup from before the reset to version 1, or a downgrade) starts
        // empty instead of crashing; trips come back with the next sync. Upgrades still need a migration.
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
