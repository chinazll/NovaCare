package com.novacare.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Insert
import androidx.room.Query

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMs: Long,
    val kind: String,
    val freedBytes: Long,
    val itemCount: Int,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: ScanHistoryEntity)

    /** 最近一次「清理」动作的时间 —— F7 智能建议引擎的输入 */
    @Query("SELECT MAX(epochMs) FROM scan_history WHERE kind = 'CLEAN'")
    suspend fun lastCleanEpochMs(): Long?

    @Query("SELECT * FROM scan_history ORDER BY epochMs DESC LIMIT 20")
    suspend fun recent(): List<ScanHistoryEntity>
}
