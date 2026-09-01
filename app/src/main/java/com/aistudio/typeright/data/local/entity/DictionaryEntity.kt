package com.aistudio.typeright.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for dictionary entries
 */
@Entity(tableName = "dictionary")
data class DictionaryEntity(
    @PrimaryKey
    val word: String,
    val frequency: Int = 1,
    val language: String = "en",
    val isCustom: Boolean = false,
    val lastUsed: Long = System.currentTimeMillis()
)
