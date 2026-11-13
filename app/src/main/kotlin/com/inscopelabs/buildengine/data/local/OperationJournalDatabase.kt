package com.inscopelabs.buildengine.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Encrypted-field Room database for privileged operation journaling (ADR 0009, T3-202).
 *
 * Schema JSON is exported to `app/schemas/` by KSP on build so schema changes are tracked.
 */
@Database(
    entities = [OperationJournalEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(OperationJournalConverters::class)
abstract class OperationJournalDatabase : RoomDatabase() {

    abstract fun operationJournalDao(): OperationJournalDao

    companion object {
        const val DATABASE_NAME: String = "operation_journal.db"
    }
}
