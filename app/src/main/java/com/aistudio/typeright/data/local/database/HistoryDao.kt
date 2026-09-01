package com.aistudio.typeright.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aistudio.typeright.data.local.entity.HistoryEntity

/**
 * DAO for typing history operations
 */
@Dao
interface HistoryDao {
    
    @Insert
    suspend fun insert(entity: HistoryEntity)
    
    @Query("SELECT * FROM typing_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getHistory(limit: Int): List<HistoryEntity>
    
    @Query("DELETE FROM typing_history WHERE timestamp < :timestamp")
    suspend fun clearOldHistory(timestamp: Long)
}
