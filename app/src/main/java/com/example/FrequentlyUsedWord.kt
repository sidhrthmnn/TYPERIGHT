package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity tracking words frequently typed or accepted by the user.
 * Supports frequency-weighted predictive completion, Katz backoff unigram scoring,
 * and user personalization.
 */
@Entity(
    tableName = "frequently_used_words",
    indices = [
        Index(value = ["word"], unique = true),
        Index(value = ["frequency"]),
        Index(value = ["lastUsedTimestamp"])
    ]
)
data class FrequentlyUsedWord(
    @PrimaryKey val word: String,
    val frequency: Int = 1,
    val count: Int = 1,
    val lastUsedTimestamp: Long = System.currentTimeMillis(),
    val locale: String = "en"
)
