package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Frequently Used Words.
 * Exposes reactive Flow methods for UI observation and suspend methods for background typing execution.
 */
@Dao
interface FrequentlyUsedWordDao {

    @Query("SELECT * FROM frequently_used_words ORDER BY frequency DESC, lastUsedTimestamp DESC LIMIT :limit")
    fun getFrequentlyUsedWordsFlow(limit: Int = 100): Flow<List<FrequentlyUsedWord>>

    @Query("SELECT * FROM frequently_used_words ORDER BY frequency DESC, lastUsedTimestamp DESC")
    suspend fun getAllWords(): List<FrequentlyUsedWord>

    @Query("SELECT * FROM frequently_used_words ORDER BY frequency DESC, lastUsedTimestamp DESC LIMIT :limit")
    suspend fun getTopWords(limit: Int = 50): List<FrequentlyUsedWord>

    @Query("SELECT * FROM frequently_used_words WHERE word LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun getWordsMatchingPrefix(prefix: String, limit: Int = 10): List<FrequentlyUsedWord>

    @Query("SELECT * FROM frequently_used_words WHERE word = :word LIMIT 1")
    suspend fun getWord(word: String): FrequentlyUsedWord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(word: FrequentlyUsedWord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<FrequentlyUsedWord>)

    @Query("UPDATE frequently_used_words SET frequency = frequency + 1, count = count + 1, lastUsedTimestamp = :timestamp WHERE word = :word")
    suspend fun incrementFrequency(word: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM frequently_used_words WHERE word = :word")
    suspend fun deleteWord(word: String)

    @Query("DELETE FROM frequently_used_words")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM frequently_used_words")
    suspend fun getWordCount(): Int
}
