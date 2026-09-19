package com.novacare.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RuleEntity::class, ScanHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun historyDao(): HistoryDao
}
