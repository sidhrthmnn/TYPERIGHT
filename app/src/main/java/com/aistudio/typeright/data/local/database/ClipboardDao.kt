package com.aistudio.typeright.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aistudio.typeright.data.local.entity.ClipboardEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for clipboard operations
 */
@Dao
interface ClipboardDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ClipboardEntity)
    
    @Update
    suspend fun update(entity: ClipboardEntity)
    
    @Delete
    suspend fun delete(entity: ClipboardEntity)
    
    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getHistory(limit: Int): List<ClipboardEntity>
    
    @Query("SELECT * FROM clipboard_history WHERE isPinned = 1 ORDER BY timestamp DESC")
    suspend fun getPinnedItems(): List<ClipboardEntity>
    
    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<ClipboardEntity>>
    
    @Query("DELETE FROM clipboard_history")
    suspend fun clearHistory()
    
    @Query("DELETE FROM clipboard_history WHERE timestamp < :timestamp")
    suspend fun clearOldItems(timestamp: Long)
}
