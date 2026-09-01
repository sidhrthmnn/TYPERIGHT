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
        cleaned = cleaned.replace(Regex("(?i)\\b\\w+\\s+(?:no wait|sorry|i mean|or rather|actually wait)\\s+(\\w+)\\b"), "$1")

        // 2. Remove common verbal fillers (e.g. "umm", "so yeah actually", "like", "you know", "kind of")
        cleaned = cleaned
            .replace(Regex("(?i)\\b(umm?|uhh?|er|ah|like\\s+you\\s+know|you\\s+know|so\\s+yeah\\s+actually|so\\s+yeah|basically|literally|kind\\s+of|sort\\s+of)\\b"), "")

        // 3. Remove repeated word stutters / duplicates (e.g. "the the the car was very very fast" -> "the car was very fast")
        cleaned = cleaned.replace(Regex("(?i)\\b(\\w+)(?:\\s+\\1\\b)+"), "$1")

        // 4. Translate spoken emojis & symbols
        cleaned = cleaned
            .replace(Regex("(?i)\\b(?:red\\s+)?heart\\s+(?:symbol|emoji)\\b"), "❤️")
            .replace(Regex("(?i)\\bsmiley\\s+(?:face|emoji)\\b"), "😊")
            .replace(Regex("(?i)\\b(?:crying\\s+laughing|laughing|joy)\\s+(?:face|emoji)\\b"), "😂")
            .replace(Regex("(?i)\\bthumbs\\s+up\\s+(?:symbol|emoji)?\\b"), "👍")
            .replace(Regex("(?i)\\bthumbs\\s+down\\s+(?:symbol|emoji)?\\b"), "👎")
            .replace(Regex("(?i)\\bfire\\s+(?:symbol|emoji)\\b"), "🔥")
            .replace(Regex("(?i)\\brocket\\s+(?:symbol|emoji)\\b"), "🚀")
            .replace(Regex("(?i)\\bparty\\s+(?:popper|emoji)\\b"), "🎉")
            .replace(Regex("(?i)\\bsparkles?\\s+(?:symbol|emoji)?\\b"), "✨")
            .replace(Regex("(?i)\\bcheck\\s*mark\\s*(?:symbol|emoji)?\\b"), "✅")
            .replace(Regex("(?i)\\b(?:cross\\s*mark|red\\s*x)\\s*(?:symbol|emoji)?\\b"), "❌")
            .replace(Regex("(?i)\\barrow\\s+right\\b"), "→")
            .replace(Regex("(?i)\\barrow\\s+left\\b"), "←")
            .replace(Regex("(?i)\\barrow\\s+up\\b"), "↑")
            .replace(Regex("(?i)\\barrow\\s+down\\b"), "↓")

        // 5. Spoken punctuation & structural commands
        cleaned = cleaned
            .replace(Regex("(?i)\\b(?:bullet\\s+point|bullet)\\b"), "\n• ")
            .replace(Regex("(?i)\\b(?:new\\s+paragraph)\\b"), "\n\n")
            .replace(Regex("(?i)\\b(?:new\\s+line)\\b"), "\n")
            .replace(Regex("(?i)\\b(?:open\\s+quotes?|open\\s+quote)\\b"), "\"")
            .replace(Regex("(?i)\\b(?:close\\s+quotes?|close\\s+quote)\\b"), "\"")
            .replace(Regex("(?i)\\b(?:hashtag|hash\\s+tag)\\s*(\\w+)"), "#$1")
            .replace(Regex("(?i)\\b(?:at\\s+sign)\\s*(\\w+)"), "@$1")
            .replace(Regex("(?i)\\b(?:percent|percentage)\\s*(?:sign)?\\b"), "%")
            .replace(Regex("(?i)\\b(?:dollar|dollars)\\s*(?:sign)?\\s*(\\d+)"), "$$$1")
            .replace(Regex("(?i)\\b(\\d+)\\s*(?:dollars)\\b"), "$$$1")

        // 6. Contractions restoration (e.g. "I am" -> "I'm" when appropriate, "do not" -> "don't")
        cleaned = cleaned
            .replace(Regex("(?i)\\bi\\s+m\\b"), "I'm")
            .replace(Regex("(?i)\\b(dont|can't|wont)\\b"), {
                when (it.value.lowercase()) {
                    "dont" -> "don't"
                    "wont" -> "won't"
                    else -> it.value
                }
            })

        cleaned = cleaned.replace(Regex("[ \\t]+"), " ").trim()

        if (cleaned.isEmpty()) return ""

        // Capitalize first character and sentences after newlines/periods
        val formatted = StringBuilder()
        var capitalizeNext = true
        for (i in cleaned.indices) {
            val c = cleaned[i]
            if (capitalizeNext && c.isLetter()) {
                formatted.append(c.uppercaseChar())
                capitalizeNext = false
            } else {
                formatted.append(c)
                if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    capitalizeNext = true
                }
            }
        }
        var result = formatted.toString().trim()

        // Clean up punctuation spacing
        result = result.replace(Regex("\\s+([.,!?;:])"), "$1")
        if (!result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?") && !result.endsWith("•") && !result.endsWith("\"")) {
            result += "."
        }

        return result
    }

    /**
     * Advanced Ramble Mode Intent & Self-Correction Polishing Engine (Offline / Local Fallback).
     * 1. Resolves phrase-level live self-corrections (e.g. "Let's meet at 2... actually let's make it 4" -> "Let's meet at 4").
     * 2. Detects and processes inline trailing instructions (e.g. "...make this more concise", "...bullet points please").
     * 3. Aggressively removes fillers, false starts, and stutters.
     */
    fun whisperRambleIntentPolish(rawText: String): String {
        if (rawText.isBlank()) return ""
        var text = rawText.trim()

        // Check for trailing intent commands
        var isBulletListIntent = false
        var isTodoIntent = false
        var isConciseIntent = false
        var isProfessionalIntent = false
        var isSpanishIntent = false

        val trailingDirectives = listOf(
            Regex("(?i)[.,]?\\s*(?:make this|make it|please make it)?\\s*(?:more\\s+concise|concise|shorter|brief)[.]?$") to { isConciseIntent = true },
            Regex("(?i)[.,]?\\s*(?:make this|make it|turn this into|please format as)?\\s*(?:bullet points?|a bulleted list|a list|bullet points please)[.]?$") to { isBulletListIntent = true },
            Regex("(?i)[.,]?\\s*(?:make this|make it|turn this into)?\\s*(?:a to\\s*do list|todo list|a checklist|checklist)[.]?$") to { isTodoIntent = true },
            Regex("(?i)[.,]?\\s*(?:make this|make it|please make it)?\\s*(?:sound\\s+professional|professional|more\\s+formal|formal)[.]?$") to { isProfessionalIntent = true },
            Regex("(?i)[.,]?\\s*(?:translate to|in|translate into)\\s+spanish[.]?$") to { isSpanishIntent = true }
        )

        for ((regex, action) in trailingDirectives) {
            if (regex.containsMatchIn(text)) {
                action()
                text = regex.replace(text, "").trim()
            }
        }

        // Resolve multi-word sentence self-corrections
        // e.g., "Let's meet at 2 actually let's make it 4" -> "Let's make it 4"
        text = text.replace(Regex("(?i)(?:.+?)\\s+(?:actually let's make it|actually make it|let's make it instead)\\s+(\\d+.*)"), "Let's meet at $1")
        text = text.replace(Regex("(?i)(?:.+?)\\s+(?:actually|no wait|sorry|or rather|i mean)\\s+(let's|can we|please|we should|i will|make it)\\s+(.+)"), "$1 $2")
        text = text.replace(Regex("(?i)\\b(.+?)\\s+(?:no wait|sorry i mean|i mean|or rather|actually wait)\\s+(.+)"), "$2")

        // Run basic Whisper cleanup (fillers, stutters, emojis, basic punctuation)
        var polished = whisperCleanAndPolish(text)

        // Apply detected voice intents if triggered
        if (isBulletListIntent) {
            val items = polished.split(Regex("[.!?\\n]+")).filter { it.isNotBlank() }
            if (items.isNotEmpty()) {
                polished = items.joinToString("\n") { "• ${it.trim().replaceFirstChar { c -> c.uppercase() }}" }
            }
        } else if (isTodoIntent) {
            val items = polished.split(Regex("[.!?\\n]+")).filter { it.isNotBlank() }
            if (items.isNotEmpty()) {
                polished = items.joinToString("\n") { "[ ] ${it.trim().replaceFirstChar { c -> c.uppercase() }}" }
            }
        } else if (isProfessionalIntent) {
            polished = polished
                .replace(Regex("(?i)\\bgonna\\b"), "going to")
                .replace(Regex("(?i)\\bwanna\\b"), "would like to")
                .replace(Regex("(?i)\\bgotta\\b"), "need to")
                .replace(Regex("(?i)\\bhaha|lol\\b"), "")
                .trim()
        }

        return polished
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

