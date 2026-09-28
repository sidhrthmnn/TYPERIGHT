package com.example

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for querying and persisting N-gram frequencies in Room,
 * enabling offline predictive text suggestions without network dependencies.
 */
@Dao
interface NGramFrequencyDao {

    /**
     * Query next-word predictions given an N-gram order and context string,
     * ordered by highest frequency and recent usage.
     */
    @Query("""
        SELECT * FROM ngram_frequencies 
        WHERE ngramOrder = :ngramOrder AND context = :context 
        ORDER BY frequency DESC, lastUsedTimestamp DESC 
        LIMIT :limit
    """)
    suspend fun getPredictions(ngramOrder: Int, context: String, limit: Int = 5): List<NGramFrequency>

    /**
     * Reactive stream for next-word predictions.
     */
    @Query("""
        SELECT * FROM ngram_frequencies 
        WHERE ngramOrder = :ngramOrder AND context = :context 
        ORDER BY frequency DESC, lastUsedTimestamp DESC 
        LIMIT :limit
    """)
    fun getPredictionsFlow(ngramOrder: Int, context: String, limit: Int = 5): Flow<List<NGramFrequency>>

    /**
     * Query next-word predictions given context AND a partial prefix (as the user types).
     */
    @Query("""
        SELECT * FROM ngram_frequencies 
        WHERE ngramOrder = :ngramOrder AND context = :context AND nextWord LIKE :prefix || '%' 
        ORDER BY frequency DESC, lastUsedTimestamp DESC 
        LIMIT :limit
    """)
    suspend fun getPredictionsWithPrefix(ngramOrder: Int, context: String, prefix: String, limit: Int = 5): List<NGramFrequency>

    /**
     * Observes the top N-grams by frequency for a given order.
     */
    @Query("""
        SELECT * FROM ngram_frequencies 
        WHERE ngramOrder = :ngramOrder 
        ORDER BY frequency DESC, lastUsedTimestamp DESC 
        LIMIT :limit
    """)
    fun getTopNGramsFlow(ngramOrder: Int, limit: Int = 20): Flow<List<NGramFrequency>>

    @Query("""
        SELECT * FROM ngram_frequencies 
        WHERE ngramOrder = :ngramOrder 
        ORDER BY frequency DESC 
        LIMIT :limit
    """)
    suspend fun getTopNGrams(ngramOrder: Int, limit: Int = 20): List<NGramFrequency>

    /**
     * Returns total count of stored N-grams across all orders.
     */
    @Query("SELECT COUNT(*) FROM ngram_frequencies")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM ngram_frequencies")
    suspend fun getTotalCount(): Int

    /**
     * Returns count of stored N-grams for a specific order.
     */
    @Query("SELECT COUNT(*) FROM ngram_frequencies WHERE ngramOrder = :ngramOrder")
    suspend fun getCountByOrder(ngramOrder: Int): Int

    /**
     * Look up a specific N-gram record.
     */
    @Query("SELECT * FROM ngram_frequencies WHERE id = :id LIMIT 1")
    suspend fun getNGram(id: String): NGramFrequency?

    /**
     * Insert or replace an N-gram entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: NGramFrequency)

    /**
     * Batch insert of N-gram entries (e.g., seeding corpus or offline model dumps).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<NGramFrequency>)

    /**
     * Increments the frequency of an existing entry.
     */
    @Query("""
        UPDATE ngram_frequencies 
        SET frequency = frequency + :increment, lastUsedTimestamp = :timestamp 
        WHERE id = :id
    """)
    suspend fun incrementFrequency(
        id: String,
        increment: Int = 1,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /**
     * Records an observation of an N-gram. If it exists, increments frequency;
     * otherwise inserts a new entry.
     */
    @Transaction
    suspend fun recordObservation(
        ngramOrder: Int,
        context: String,
        nextWord: String,
        increment: Int = 1,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val cleanContext = context.trim().lowercase()
        val cleanWord = nextWord.trim().lowercase()
        val id = NGramFrequency.createId(ngramOrder, cleanContext, cleanWord)
        val updated = incrementFrequency(id, increment, timestamp)
        if (updated == 0) {
            insertOrUpdate(
                NGramFrequency(
                    id = id,
                    ngramOrder = ngramOrder,
                    context = cleanContext,
                    nextWord = cleanWord,
                    frequency = increment,
                    probability = 0f,
                    lastUsedTimestamp = timestamp
                )
            )
        }
    }

    /**
     * Deletes a specific N-gram entry.
     */
    @Delete
    suspend fun delete(entry: NGramFrequency)

    @Query("DELETE FROM ngram_frequencies WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Clears all N-grams for a specific order.
     */
    @Query("DELETE FROM ngram_frequencies WHERE ngramOrder = :ngramOrder")
    suspend fun clearByOrder(ngramOrder: Int)

    /**
     * Clears all stored N-grams.
     */
    @Query("DELETE FROM ngram_frequencies")
    suspend fun clearAll()

    /**
     * Deletes all N-gram occurrences involving the given word.
     */
    @Query("DELETE FROM ngram_frequencies WHERE nextWord = :word OR context = :word OR context LIKE '% ' || :word OR context LIKE :word || ' %' OR context LIKE '% ' || :word || ' %'")
    suspend fun deleteWordObservations(word: String)
}
