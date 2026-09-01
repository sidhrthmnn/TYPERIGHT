package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LearnedWordDao {
    @Query("SELECT * FROM learned_words ORDER BY frequency DESC, timestamp DESC")
    suspend fun getAllWords(): List<LearnedWord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: LearnedWord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<LearnedWord>)

    @Query("SELECT COUNT(*) FROM learned_words")
    suspend fun getWordCount(): Int

    @Query("SELECT * FROM learned_words ORDER BY frequency DESC, timestamp DESC LIMIT :limit")
    suspend fun getTopWords(limit: Int): List<LearnedWord>

    @Query("SELECT * FROM learned_words WHERE word = :word LIMIT 1")
    suspend fun getWord(word: String): LearnedWord?

    @Query("DELETE FROM learned_words WHERE word = :word")
    suspend fun deleteWord(word: String)

    @Query("DELETE FROM learned_words WHERE frequency <= :maxFrequency AND timestamp < :beforeTimestamp")
    suspend fun pruneStaleWords(maxFrequency: Int, beforeTimestamp: Long)
}
