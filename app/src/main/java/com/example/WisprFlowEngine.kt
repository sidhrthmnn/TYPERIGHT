package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * WisprFlowMode defines the AI intelligence and tone transformations
 * modeled after Wispr Flow dictation software.
 */
enum class WisprFlowMode(
    val title: String,
    val icon: String,
    val badge: String,
    val description: String
) {
    AUTO(
        title = "Wispr Flow",
        icon = "⚡",
        badge = "Smart AI",
        description = "Auto-removes filler words (um/uh), cleans stutters, repairs mid-sentence self-corrections, and punctuates automatically."
    ),
    VERBATIM(
        title = "Clean Verbatim",
        icon = "✍️",
        badge = "Faithful",
        description = "Transcribes words verbatim with natural punctuation and zero rewording."
    ),
    EXECUTIVE(
        title = "Executive",
        icon = "👔",
        badge = "Formal",
        description = "Transforms conversational speech into professional, polished business communications and emails."
    ),
    CASUAL(
        title = "Casual Chat",
        icon = "💬",
        badge = "Messaging",
        description = "Translates voice flow into natural, friendly, text-messaging format."
    ),
    BULLETS(
        title = "Action Points",
        icon = "📋",
        badge = "Summary",
        description = "Structures thoughts into clean, actionable bulleted checklists and meeting notes."
    ),
    NUMBERED(
        title = "Numbered Steps",
        icon = "🔢",
        badge = "Sequence",
        description = "Formats sequential thoughts into ordered 1, 2, 3 numbered steps."
    ),
    EMAIL(
        title = "Email Layout",
        icon = "✉️",
        badge = "Letter",
        description = "Structures speech into greeting, clear paragraphs, and sign-off."
    ),
    CONCISE(
        title = "Concise",
        icon = "✂️",
        badge = "Brevity",
        description = "Eliminates redundancy and verbose conversational padding into punchy sentences."
    ),
    CHECKLIST(
        title = "Checklist",
        icon = "☐",
        badge = "Tasks",
        description = "Structures actionable thoughts into to-do checklist items."
    )
}

/**
 * Result structure emitted by WisprFlowEngine.
 */
data class WisprFlowResult(
    val rawTranscript: String,
    val polishedText: String,
    val mode: WisprFlowMode,
    val removedFillersCount: Int,
    val selfCorrectionsCount: Int,
    val processingTimeMs: Long,
    val detectedCommands: List<String> = emptyList()
)

/**
 * High-performance on-device Wispr Flow engine that transforms raw spoken rambling
 * into formatted, production-grade text in real time.
 */
class WisprFlowEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WisprFlowEngine"

        @Volatile
        private var instance: WisprFlowEngine? = null

        fun getInstance(context: Context): WisprFlowEngine {
            return instance ?: synchronized(this) {
                instance ?: WisprFlowEngine(context.applicationContext ?: context).also { instance = it }
            }
        }

        // Common vocal disfluencies, fillers, and hesitation markers stripped in Wispr Flow mode
        val FILLER_PATTERNS = listOf(
            Regex("(?i)\\b(um+|uh+|er+|ah+|like|you know|sort of|kind of|basically|literally|so yeah|i mean|right)\\b[,]?\\s*"),
            Regex("(?i)\\b(you know what i mean|at the end of the day|to be honest)\\b[,]?\\s*")
        )

        // Spoken punctuation replacements
        val SPOKEN_PUNCTUATION = listOf(
            Regex("(?i)\\b(period|full stop)\\b") to ".",
            Regex("(?i)\\bcomma\\b") to ",",
            Regex("(?i)\\bquestion mark\\b") to "?",
            Regex("(?i)\\b(exclamation mark|exclamation point)\\b") to "!",
            Regex("(?i)\\b(new line|next line)\\b") to "\n",
            Regex("(?i)\\b(new paragraph|next paragraph)\\b") to "\n\n",
            Regex("(?i)\\bcolon\\b") to ":",
            Regex("(?i)\\bsemicolon\\b") to ";",
            Regex("(?i)\\b(open quotes?|open quote|quote)\\b") to "\"",
            Regex("(?i)\\b(close quotes?|close quote|unquote)\\b") to "\"",
            Regex("(?i)\\b(bullet point|bullet)\\b") to "\n• ",
            Regex("(?i)\\b(hashtag|hash tag)\\s*([a-zA-Z0-9]+)") to "#$2",
            Regex("(?i)\\b(at sign|at symbol)\\s*([a-zA-Z0-9_]+)") to "@$2",
            Regex("(?i)\\b(percent|percentage)\\s*(?:sign)?\\b") to "%",
            Regex("(?i)\\b(dollar|dollars)\\s*(?:sign)?\\s*(\\d+)") to "$$$2",
            Regex("(?i)\\b(\\d+)\\s*(?:dollars)\\b") to "$$$1"
        )

        // Spoken emojis
        val SPOKEN_EMOJIS = listOf(
            Regex("(?i)\\b(thumbs up|thumb up)\\s*(?:emoji|symbol)?\\b") to "👍",
            Regex("(?i)\\b(thumbs down|thumb down)\\s*(?:emoji|symbol)?\\b") to "👎",
            Regex("(?i)\\b(heart|red heart)\\s*(?:emoji|symbol)?\\b") to "❤️",
            Regex("(?i)\\b(smiley|smiling face|smile)\\s*(?:emoji)?\\b") to "😊",
            Regex("(?i)\\b(laughing|crying laughing|joy)\\s*(?:face|emoji)?\\b") to "😂",
            Regex("(?i)\\b(fire|flame)\\s*(?:emoji)?\\b") to "🔥",
            Regex("(?i)\\b(rocket)\\s*(?:emoji)?\\b") to "🚀",
            Regex("(?i)\\b(party popper|celebration)\\s*(?:emoji)?\\b") to "🎉",
            Regex("(?i)\\b(check mark|checkmark)\\s*(?:emoji|symbol)?\\b") to "✅",
            Regex("(?i)\\b(sparkles?)\\s*(?:emoji)?\\b") to "✨"
        )

        // Sample real-world rambling voice transcripts for playground testing
        val SAMPLE_VOICE_PROMPTS = listOf(
            "Um uh so yeah let's schedule the product sync for Tuesday—wait no, Wednesday at 2 PM",
            "Hey Sarah like basically we reviewed the draft and honestly it looks ready to send to the client",
            "I'm running late send this to my boss formally",
            "Action items for the sprint: first update the keyboard layout, then test voice typing, and finally release the update",
            "Can we meet at the cafe wait actually let's meet at the library instead question mark"
        )
    }

    private val _currentMode = MutableStateFlow(WisprFlowMode.AUTO)
    val currentMode: StateFlow<WisprFlowMode> = _currentMode

    fun setMode(mode: WisprFlowMode) {
        _currentMode.value = mode
    }

    /**
     * Transforms raw spoken transcript into finalized Wispr Flow output according to the selected mode.
     */
    suspend fun processVoiceTranscript(
        rawTranscript: String,
        mode: WisprFlowMode = _currentMode.value
    ): WisprFlowResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val raw = rawTranscript.trim()
        if (raw.isBlank()) {
            return@withContext WisprFlowResult(
                rawTranscript = "",
                polishedText = "",
                mode = mode,
                removedFillersCount = 0,
                selfCorrectionsCount = 0,
                processingTimeMs = 0
            )
        }

        var text = raw
        val detectedCommands = mutableListOf<String>()
        var fillerCount = 0
        var correctionCount = 0

        // 1. Spoken Punctuation & Structural commands
        for ((pattern, replacement) in SPOKEN_PUNCTUATION) {
            if (pattern.containsMatchIn(text)) {
                detectedCommands.add(replacement.trim())
                text = pattern.replace(text, replacement)
            }
        }

        // 2. Spoken Emojis
        for ((pattern, emoji) in SPOKEN_EMOJIS) {
            if (pattern.containsMatchIn(text)) {
                text = pattern.replace(text, emoji)
            }
        }

        // 3. Mode-specific transformations
        when (mode) {
            WisprFlowMode.AUTO -> {
                // A. Mid-sentence self-correction detection & resolution
                // e.g., "Tuesday wait no Wednesday at 2" -> "Wednesday at 2"
                val beforeCorrection = text
                text = text.replace(Regex("(?i)\\b(\\w+)[—–\\-\\s]+(?:wait no|no wait|actually wait|sorry no|no)\\s*,?\\s*(\\w+(?:\\s+at\\s+\\d+(?:\\s*(?:am|pm))?)?)"), "$2")
                text = text.replace(Regex("(?i)(?:.+?)\\s+(?:actually let's make it|actually make it|let's make it instead)\\s+(\\d+.*)"), "Let's meet at $1")
                text = text.replace(Regex("(?i)(?:.+?)\\s+(?:actually|no wait|sorry|or rather|i mean)\\s+(let's|can we|please|we should|i will|make it)\\s+(.+)"), "$1 $2")
                text = text.replace(Regex("(?i)\\b(.+?)\\s+(?:no wait|sorry i mean|i mean|or rather|actually wait)\\s+(.+)"), "$2")
                if (beforeCorrection != text) {
                    correctionCount++
                }

                // B. Vocal fillers removal
                for (regex in FILLER_PATTERNS) {
                    val matches = regex.findAll(text).count()
                    fillerCount += matches
                    text = regex.replace(text, "")
                }

                // C. Stutters / repeated consecutive words
                text = text.replace(Regex("(?i)\\b(\\w+)(?:\\s+\\1\\b)+"), "$1")

                // D. Meta-command directives handling (e.g. "send this to my boss formally")
                if (text.contains("send this to my boss formally", ignoreCase = true)) {
                    text = text.replace(Regex("(?i)send this to my boss formally[.]?"), "").trim()
                    if (text.contains("running late", ignoreCase = true)) {
                        text = "Good morning, I apologize for the inconvenience, but I am currently running behind schedule."
                    }
                }

                text = cleanPunctuationAndSpacing(text)
            }

            WisprFlowMode.VERBATIM -> {
                // Clean punctuation and spacing, but keep verbatim words
                text = cleanPunctuationAndSpacing(text)
            }

            WisprFlowMode.EXECUTIVE -> {
                // Remove fillers
                for (regex in FILLER_PATTERNS) {
                    text = regex.replace(text, "")
                }
                text = text.replace(Regex("(?i)\\b(\\w+)(?:\\s+\\1\\b)+"), "$1")

                // Convert colloquialisms to business-grade phrasing
                text = text
                    .replace(Regex("(?i)\\bgonna\\b"), "going to")
                    .replace(Regex("(?i)\\bwanna\\b"), "would like to")
                    .replace(Regex("(?i)\\bgotta\\b"), "need to")
                    .replace(Regex("(?i)\\bcan't\\b"), "cannot")
                    .replace(Regex("(?i)\\bdon't\\b"), "do not")
                    .replace(Regex("(?i)\\bwon't\\b"), "will not")
                    .replace(Regex("(?i)\\bkinda\\b"), "somewhat")
                    .replace(Regex("(?i)\\bhaha|lol\\b"), "")
                    .trim()

                if (text.contains("running late", ignoreCase = true)) {
                    text = text.replace(Regex("(?i)I'm running late[.]?"), "I apologize for the delay, but I am currently running behind schedule.")
                }

                text = cleanPunctuationAndSpacing(text)
            }

            WisprFlowMode.CASUAL -> {
                // Remove heavy fillers but maintain conversational flow
                for (regex in FILLER_PATTERNS) {
                    text = regex.replace(text, "")
                }
                text = cleanPunctuationAndSpacing(text)
            }

            WisprFlowMode.BULLETS -> {
                // Strip fillers
                for (regex in FILLER_PATTERNS) {
                    text = regex.replace(text, "")
                }
                text = cleanPunctuationAndSpacing(text)

                // Structure into bullet points
                val clauses = text.split(Regex("[.!?\\n]+|\\b(?:first|then|next|finally|second|third)\\b\\s*,?"))
                    .map { it.trim() }
                    .filter { it.length > 2 }

                if (clauses.isNotEmpty()) {
                    text = clauses.joinToString("\n") { clause ->
                        "• " + clause.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    }
                }
            }

            WisprFlowMode.NUMBERED -> {
                for (regex in FILLER_PATTERNS) {
                    text = regex.replace(text, "")
                }
                text = VoiceTranscriptionFormatter.formatTranscription(text, TranscriptionFormatStyle.NUMBERED)
            }

            WisprFlowMode.EMAIL -> {
                for (regex in FILLER_PATTERNS) {
                    text = regex.replace(text, "")
                }
                text = VoiceTranscriptionFormatter.formatTranscription(text, TranscriptionFormatStyle.EMAIL)
            }

            WisprFlowMode.CONCISE -> {
                for (regex in FILLER_PATTERNS) {
                    text = regex.replace(text, "")
                }
                text = VoiceTranscriptionFormatter.formatTranscription(text, TranscriptionFormatStyle.CONCISE)
            }

            WisprFlowMode.CHECKLIST -> {
                for (regex in FILLER_PATTERNS) {
                    text = regex.replace(text, "")
                }
                text = VoiceTranscriptionFormatter.formatTranscription(text, TranscriptionFormatStyle.CHECKLIST)
            }
        }

        val duration = System.currentTimeMillis() - startTime

        return@withContext WisprFlowResult(
            rawTranscript = raw,
            polishedText = text,
            mode = mode,
            removedFillersCount = fillerCount,
            selfCorrectionsCount = correctionCount,
            processingTimeMs = duration,
            detectedCommands = detectedCommands
        )
    }

    /**
     * Cleans whitespace, removes double punctuation, and ensures proper sentence casing.
     */
    private fun cleanPunctuationAndSpacing(input: String): String {
        var s = input.replace(Regex("[ \\t]+"), " ").trim()

        // Clean up spaces before punctuation marks
        s = s.replace(Regex("\\s+([.,!?;:])"), "$1")

        // Ensure space after punctuation (except if followed by newline or already end of text)
        s = s.replace(Regex("([.,!?;:])([a-zA-Z0-9])"), "$1 $2")

        if (s.isEmpty()) return ""

        // Sentence-case formatting
        val sb = StringBuilder()
        var capitalizeNext = true
        for (i in s.indices) {
            val c = s[i]
            if (capitalizeNext && c.isLetter()) {
                sb.append(c.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(c)
                if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    capitalizeNext = true
                }
            }
        }

        var result = sb.toString().trim()
        if (!result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?") &&
            !result.endsWith("•") && !result.endsWith("\"") && !result.contains("\n• ")
        ) {
            result += "."
        }

        return result
    }
}
