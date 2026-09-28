package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing words/suggestions explicitly removed and blocked by the user.
 * When a user long-presses a suggestion in the candidate strip and removes it via the trash bin,
 * the word is permanently recorded here and blocked from future suggestion generation across all
 * predictive engines (Gboard, N-gram, SymSpell, and dictionary trie).
 */
@Entity(
    tableName = "blocked_suggestions",
    indices = [
        Index(value = ["word"], unique = true),
        Index(value = ["blockedTimestamp"])
    ]
)
data class BlockedSuggestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String, // lowercase normalized key
    val originalWord: String = word, // original case as displayed
    val blockedTimestamp: Long = System.currentTimeMillis()
)
