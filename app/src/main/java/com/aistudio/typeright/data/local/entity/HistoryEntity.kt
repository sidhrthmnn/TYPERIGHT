package com.aistudio.typeright.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for typing history
 */
@Entity(tableName = "typing_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalText: String,
    val correctedText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val language: String = "en"
)
