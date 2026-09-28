package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for user custom dictionary entries and shortcut expansions.
 * Enables user-defined custom words, names, abbreviations, and text shortcuts
 * (e.g. shortcut "omw" -> "On my way!") for predictive typing.
 */
@Entity(
    tableName = "custom_dictionary_entries",
    indices = [
        Index(value = ["word"], unique = true),
        Index(value = ["shortcut"]),
        Index(value = ["addedTimestamp"])
    ]
)
data class CustomDictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val shortcut: String? = null,
    val frequency: Int = 200, // Custom words are given high priority weighting
    val locale: String = "en",
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)
