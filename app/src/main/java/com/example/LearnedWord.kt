package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_words")
data class LearnedWord(
    @PrimaryKey val word: String,
    val frequency: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
