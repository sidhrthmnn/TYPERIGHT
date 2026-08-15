package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grammar_rules")
data class GrammarRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // e.g. "Spelling", "Punctuation", "Contraction", "Homophone", "Grammar"
    val pattern: String,  // Regex string or target phrase
    val replacement: String, // Correction string
    val description: String = "",
    val isEnabled: Boolean = true,
    val isUserCustom: Boolean = false,
    val priority: Int = 1
)
