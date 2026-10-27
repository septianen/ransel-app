package app.ransel.data.local

import androidx.room3.DeleteColumn
import androidx.room3.migration.AutoMigrationSpec

/** The kitty is gone (owner, 27 Oct 2026): expenses lose paidFromKitty. */
@DeleteColumn(tableName = "expenses", columnName = "paidFromKitty")
class Migration9To10 : AutoMigrationSpec
