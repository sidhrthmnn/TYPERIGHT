package com.example

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user custom dictionary entries and shortcut expansions.
 * Supports reactive Flow queries for settings screens and fast lookups for predictive text typing.
 */
@Dao
interface CustomDictionaryDao {

    @Query("SELECT * FROM custom_dictionary_entries ORDER BY addedTimestamp DESC")
    fun getAllEntriesFlow(): Flow<List<CustomDictionaryEntry>>

    @Query("SELECT * FROM custom_dictionary_entries ORDER BY frequency DESC, addedTimestamp DESC")
    suspend fun getAllEntries(): List<CustomDictionaryEntry>

    @Query("SELECT * FROM custom_dictionary_entries WHERE word = :word LIMIT 1")
    suspend fun getEntryByWord(word: String): CustomDictionaryEntry?

    @Query("SELECT * FROM custom_dictionary_entries WHERE shortcut = :shortcut LIMIT 1")
    suspend fun getEntryByShortcut(shortcut: String): CustomDictionaryEntry?

    @Query("SELECT * FROM custom_dictionary_entries WHERE word LIKE :prefix || '%' OR shortcut LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun searchPrefix(prefix: String, limit: Int = 10): List<CustomDictionaryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CustomDictionaryEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<CustomDictionaryEntry>)

    @Update
    suspend fun updateEntry(entry: CustomDictionaryEntry)

    @Delete
    suspend fun deleteEntry(entry: CustomDictionaryEntry)

    @Query("DELETE FROM custom_dictionary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM custom_dictionary_entries WHERE word = :word")
    suspend fun deleteByWord(word: String)

    @Query("DELETE FROM custom_dictionary_entries")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM custom_dictionary_entries")
    suspend fun getEntryCount(): Int
}
