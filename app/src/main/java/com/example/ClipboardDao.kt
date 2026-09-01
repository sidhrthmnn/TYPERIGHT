package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_items ORDER BY isPinned DESC, timestamp DESC")
    fun getAllItems(): Flow<List<ClipboardItem>>

    @Query("SELECT * FROM clipboard_items ORDER BY timestamp DESC LIMIT 100")
    suspend fun getAllClipsSync(): List<ClipboardItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ClipboardItem)

    @Query("UPDATE clipboard_items SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinned(id: Int, isPinned: Boolean)

    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)

    @Query("DELETE FROM clipboard_items WHERE isPinned = 0")
    suspend fun clearUnpinned()

    @Query("SELECT * FROM clipboard_items WHERE text = :text LIMIT 1")
    suspend fun getItemByText(text: String): ClipboardItem?
}
