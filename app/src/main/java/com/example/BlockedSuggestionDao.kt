package com.example

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedSuggestionDao {
    @Query("SELECT * FROM blocked_suggestions ORDER BY blockedTimestamp DESC")
    fun getAllBlockedFlow(): Flow<List<BlockedSuggestion>>

    @Query("SELECT * FROM blocked_suggestions ORDER BY blockedTimestamp DESC")
    suspend fun getAllBlocked(): List<BlockedSuggestion>

    @Query("SELECT word FROM blocked_suggestions")
    suspend fun getAllBlockedWords(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocked(entry: BlockedSuggestion): Long

    @Query("DELETE FROM blocked_suggestions WHERE word = :word")
    suspend fun deleteByWord(word: String): Int

    @Delete
    suspend fun delete(entry: BlockedSuggestion)

    @Query("DELETE FROM blocked_suggestions")
    suspend fun clearAll()

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_suggestions WHERE word = :word)")
    suspend fun isBlocked(word: String): Boolean
}
