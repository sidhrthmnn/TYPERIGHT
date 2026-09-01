package com.example

/**
 * Supported AI Polish & Transformation Modes.
 */
enum class PolishMode {
    PROOFREAD,      // Minimum changes: spelling, grammar, punctuation, capitalization
    POLISH,         // Natural flow and clarity while strictly preserving meaning
    PROFESSIONAL,   // Crisp, respectful, polished business tone
    CASUAL,         // Warm, friendly, conversational tone
    SHORTEN,        // Condense and summarize while retaining all vital context
    EXPAND,         // Elaborate naturally, polite phrasing, complete incomplete ideas
    REPHRASE,       // Articulate smoothly into expressive English
    VOICE_CLEANUP,  // Aggressively remove fillers, stutters, repeated speech, spoken corrections
    RAMBLE;         // Intent-based dictation: resolve self-corrections, strip fillers, apply trailing voice instructions, output final polished text

    companion object {
        fun fromString(mode: String?): PolishMode {
            if (mode.isNullOrBlank()) return PROOFREAD
            return when (mode.trim().lowercase()) {
                "proofread", "grammar", "spellcheck", "correct" -> PROOFREAD
                "polish", "improve", "flow" -> POLISH
                "professional", "formal", "formalize", "business" -> PROFESSIONAL
                "casual", "friendly", "informal" -> CASUAL
                "shorten", "concise", "brief" -> SHORTEN
                "expand", "elaborate", "detailed" -> EXPAND
                "rephrase", "rewrite", "paraphrase" -> REPHRASE
                "voice_cleanup", "voice", "speech", "dictation", "transcribe" -> VOICE_CLEANUP
                "ramble", "ramble_mode", "voice_intent", "dictate_ai", "intent_voice" -> RAMBLE
                else -> PROOFREAD
            }
        }
    }
}

/**
 * Origin of the AI text result.
 */
enum class AiSource {
    LOCAL_RULES,    // Fast deterministic rule-based engine / Room grammar rules
    LOCAL_MODEL,    // On-device TFLite / local statistical NLP engine
    CLOUD,          // Cloud Gemini API (Flash Lite)
    ORIGINAL        // Text was already correct or returned unchanged as fallback
}

/**
 * Represents a single text modification (diff).
 */
data class Edit(
    val original: String,
    val replacement: String,
    val start: Int = 0,
    val end: Int = 0,
    val description: String = ""
)

/**
 * Standardized AI Polish & Inference Result.
 */
data class AiResult(
    val text: String,
    val confidence: Float,
    val changed: Boolean,
    val source: AiSource,
    val changes: List<Edit> = emptyList()
)

/**
 * Contextual representation of the text surrounding the cursor and current selection.
 */
data class TextContext(
    val textBeforeCursor: String = "",
    val textAfterCursor: String = "",
    val currentSentence: String = "",
    val previousSentence: String? = null,
    val selectedText: String? = null,
    val currentWord: String? = null,
    val mode: PolishMode = PolishMode.PROOFREAD
)
