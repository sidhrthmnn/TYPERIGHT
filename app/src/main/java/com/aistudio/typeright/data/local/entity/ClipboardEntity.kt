package com.aistudio.typeright.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for clipboard history
 */
@Entity(tableName = "clipboard_history")
data class ClipboardEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val category: String = "text"
)
