package com.example

/**
 * Defines the semantic scope of text to select.
 */
enum class SmartSelectLevel(val label: String) {
    WORD("Word"),
    SENTENCE("Sentence"),
    PARAGRAPH("Paragraph"),
    ALL("All Text")
}

/**
 * Represents the computed boundaries and text for a smart selection.
 */
data class SmartSelectionBounds(
    val fullText: String,
    val startIndex: Int,
    val endIndex: Int,
    val selectedText: String,
    val level: SmartSelectLevel
)

/**
 * Snapshot for undoing a Gemini auto-polish operation.
 */
data class UndoPolishData(
    val originalText: String,
    val replacedText: String,
    val isSelection: Boolean,
    val selectionStart: Int,
    val selectionEnd: Int,
    val timestamp: Long
)
