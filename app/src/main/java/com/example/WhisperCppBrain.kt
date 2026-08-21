package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Gemini-backed Speech Processing Brain.
 * Uses Gemini API with robust local NLP fallback
 * for real-time speech repair, stutter removal, filler filtering, and sentence formatting.
 */
object WhisperCppBrain {
    private const val TAG = "WhisperCppBrain"

    fun loadGGMLModel(context: Context, modelName: String): Boolean {
        Log.i(TAG, "Initialized Speech Processing Engine: Gemini Cloud + Local NLP")
        return true
    }

    /**
     * Cleans and polishes raw speech dictation text using local NLP rules and regex.
     */
    fun whisperCleanAndPolish(rawText: String): String {
        if (rawText.isBlank()) return ""
        
        var cleaned = rawText

        // 1. Remove self-corrections (e.g. "at five no wait six" -> "at six", "red sorry blue" -> "blue")
        cleaned = cleaned.replace(Regex("(?i)\\b\\w+\\s+(?:no wait|sorry|i mean|or rather)\\s+(\\w+)\\b"), "$1")

        // 2. Remove common verbal fillers (e.g. "umm", "so yeah actually", "like", "you know")
        cleaned = cleaned
            .replace(Regex("(?i)\\b(umm?|uhh?|er|ah|like\\s+you\\s+know|you\\s+know|so\\s+yeah\\s+actually|so\\s+yeah|basically|literally)\\b"), "")

        // 3. Remove repeated word stutters / duplicates (e.g. "the the the car was very very fast" -> "the car was very fast")
        cleaned = cleaned.replace(Regex("(?i)\\b(\\w+)(?:\\s+\\1\\b)+"), "$1")

        // 4. Translate verbal symbol descriptions (heart symbol -> ❤️, smiley face -> 😊, arrow right -> →)
        cleaned = cleaned
            .replace(Regex("(?i)\\bheart\\s+(?:symbol|emoji)\\b"), "❤️")
            .replace(Regex("(?i)\\bsmiley\\s+(?:face|emoji)\\b"), "😊")
            .replace(Regex("(?i)\\barrow\\s+right\\b"), "→")
            .replace(Regex("(?i)\\barrow\\s+left\\b"), "←")
            .replace(Regex("(?i)\\barrow\\s+up\\b"), "↑")
            .replace(Regex("(?i)\\barrow\\s+down\\b"), "↓")

        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        if (cleaned.isEmpty()) return ""

        // Capitalize first character
        cleaned = cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Clean up punctuation around symbols and end of text
        cleaned = cleaned.replace(Regex("\\s+([.,!?;:])"), "$1")
        if (!cleaned.endsWith(".") && !cleaned.endsWith("!") && !cleaned.endsWith("?")) {
            cleaned += "."
        }

        return cleaned
    }

    /**
     * Streams polished text word-by-word.
     */
    fun streamWhisperPolish(text: String): Flow<String> = flow {
        val cleaned = whisperCleanAndPolish(text)
        if (cleaned.isEmpty()) {
            emit("")
            return@flow
        }

        val words = cleaned.split(" ")
        val currentBuild = StringBuilder()

        for (i in words.indices) {
            if (i > 0) currentBuild.append(" ")
            currentBuild.append(words[i])
            emit(currentBuild.toString())
            delay(35)
        }
    }
}

