package com.example

import android.content.Context

/**
 * OnDeviceNeuralPolishEngine provides on-device neural polish, styling,
 * tone transformation, and quick proofreading without cloud latency.
 */
class OnDeviceNeuralPolishEngine private constructor(private val context: Context) {

    private val grammarPredictor by lazy { LocalGrammarSpellPredictor(context) }
    private val neuralEngine by lazy { NeuralCorrectionEngine.getInstance(context) }

    data class NeuralPolishResult(
        val polishedText: String,
        val tone: String,
        val toneSummary: String = tone,
        val latencyMs: Long = 12L,
        val appliedEdits: List<Edit> = emptyList()
    )

    companion object {
        @Volatile
        private var instance: OnDeviceNeuralPolishEngine? = null

        fun getInstance(context: Context): OnDeviceNeuralPolishEngine {
            return instance ?: synchronized(this) {
                instance ?: OnDeviceNeuralPolishEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Performs instantaneous on-device proofreading utilizing Google on-device spell check
     * and local grammar heuristics across ALL sentences and paragraphs in the input.
     */
    fun quickProofread(input: String): String {
        if (input.isBlank()) return input
        return input.lines().joinToString("\n") { line ->
            if (line.isBlank()) {
                line
            } else {
                val spellChecked = GoogleDeviceSpellChecker.getInstance(context).proofreadSentenceFast(line)
                val neuralFixed = neuralEngine.correctText(spellChecked)
                grammarPredictor.polishSentenceLocally(neuralFixed)
            }
        }
    }

    /**
     * Transforms input text to a target tone or style locally.
     */
    fun polish(input: String, tone: String): NeuralPolishResult {
        val startTime = System.currentTimeMillis()
        val baseClean = quickProofread(input)
        val styled = when (tone.lowercase()) {
            "formal", "professional" -> applyFormalStyle(baseClean)
            "casual", "friendly" -> applyCasualStyle(baseClean)
            "concise", "short" -> applyConciseStyle(baseClean)
            "eloquent" -> applyEloquentStyle(baseClean)
            "voice" -> applyVoiceCleanupStyle(baseClean)
            else -> baseClean
        }
        val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        val edits = if (input != styled) {
            listOf(Edit(original = input.take(30), replacement = styled.take(30)))
        } else {
            emptyList()
        }
        return NeuralPolishResult(
            polishedText = styled,
            tone = tone,
            toneSummary = tone,
            latencyMs = latency,
            appliedEdits = edits
        )
    }

    /**
     * Generates 3 distinct stylistic variants (Professional, Casual, Concise).
     */
    fun generateStyleVariants(input: String): List<String> {
        val baseClean = quickProofread(input)
        val formal = applyFormalStyle(baseClean)
        val casual = applyCasualStyle(baseClean)
        val concise = applyConciseStyle(baseClean)

        return listOf(formal, casual, concise).distinct()
    }

    private fun applyFormalStyle(text: String): String {
        var result = text
            .replace(Regex("(?i)\\bgonna\\b"), "going to")
            .replace(Regex("(?i)\\bwanna\\b"), "want to")
            .replace(Regex("(?i)\\bgotta\\b"), "need to")
            .replace(Regex("(?i)\\bcan't\\b"), "cannot")
            .replace(Regex("(?i)\\bdon't\\b"), "do not")
            .replace(Regex("(?i)\\bwon't\\b"), "will not")
            .replace(Regex("(?i)\\bhey\\b"), "Hello")
            .replace(Regex("(?i)\\byeah\\b"), "yes")
            .replace(Regex("(?i)\\byep\\b"), "yes")
            .replace(Regex("(?i)\\bpls\\b|\\bplz\\b"), "please")
            .replace(Regex("(?i)\\basap\\b"), "as soon as possible")
            .replace(Regex("(?i)\\blet you know\\b"), "inform you")
            .replace(Regex("(?i)\\bget in touch\\b"), "contact you")
            .replace(Regex("(?i)\\bmake sure\\b"), "ensure")
            .replace(Regex("(?i)\\btalk about\\b"), "discuss")
            .replace(Regex("(?i)\\bdeal with\\b"), "manage")
            .replace(Regex("(?i)\\babout to\\b"), "intending to")
            .replace(Regex("(?i)\\blook into\\b"), "examine")
            .replace(Regex("(?i)\\bfind out\\b"), "determine")
            .replace(Regex("(?i)\\bgive up\\b"), "relinquish")
            .replace(Regex("(?i)\\bput off\\b"), "postpone")
            .replace(Regex("(?i)\\bcome up with\\b"), "develop")
            .replace(Regex("(?i)\\bcall off\\b"), "cancel")
            .replace(Regex("(?i)\\bshow up\\b"), "arrive")
            .replace(Regex("(?i)\\btell\\b"), "inform")
            .replace(Regex("(?i)\\bbuy\\b"), "purchase")
            .replace(Regex("(?i)\\bneed to\\b"), "must")
            .replace(Regex("(?i)\\bI want\\b"), "I would like")
            .replace(Regex("(?i)\\bthanks\\b"), "thank you")
        if (result.isNotEmpty() && result[0].isLowerCase()) {
            result = result.replaceFirstChar { it.uppercase() }
        }
        if (result.isNotEmpty() && !result.endsWith(".") && !result.endsWith("?") && !result.endsWith("!")) {
            result += "."
        }
        return result
    }

    private fun applyCasualStyle(text: String): String {
        return text
            .replace(Regex("(?i)\\bdo not\\b"), "don't")
            .replace(Regex("(?i)\\bcannot\\b"), "can't")
            .replace(Regex("(?i)\\bwill not\\b"), "won't")
            .replace(Regex("(?i)\\bI am\\b"), "I'm")
            .replace(Regex("(?i)\\bIt is\\b"), "It's")
            .replace(Regex("(?i)\\bWe are\\b"), "We're")
            .replace(Regex("(?i)\\bThey are\\b"), "They're")
            .replace(Regex("(?i)\\bI have\\b"), "I've")
            .replace(Regex("(?i)\\bHello\\b"), "Hey")
            .replace(Regex("(?i)\\bThank you\\b"), "Thanks!")
            .replace(Regex("(?i)\\bI would like to\\b"), "I'd love to")
            .replace(Regex("(?i)\\bPlease inform me\\b"), "Let me know")
            .replace(Regex("(?i)\\bAt your earliest convenience\\b"), "Whenever you can")
            .replace(Regex("(?i)\\bGood morning\\b"), "Morning!")
            .replace(Regex("(?i)\\bGood evening\\b"), "Evening!")
            .replace(Regex("(?i)\\bI apologize\\b"), "Sorry about that")
            .replace(Regex("(?i)\\bFurthermore\\b"), "Also")
            .replace(Regex("(?i)\\bAdditionally\\b"), "Plus")
            .replace(Regex("(?i)\\bHowever\\b"), "Though")
            .replace(Regex("(?i)\\bRegarding\\b"), "About")
    }

    private fun applyConciseStyle(text: String): String {
        var result = text
            .replace(Regex("(?i)\\bin order to\\b"), "to")
            .replace(Regex("(?i)\\bdue to the fact that\\b"), "because")
            .replace(Regex("(?i)\\bat this point in time\\b"), "now")
            .replace(Regex("(?i)\\bas a matter of fact\\b"), "in fact")
            .replace(Regex("(?i)\\bfor the purpose of\\b"), "for")
            .replace(Regex("(?i)\\bI am writing to\\b"), "")
            .replace(Regex("(?i)\\bjust wanted to\\b"), "")
            .replace(Regex("(?i)\\bfeel free to\\b"), "please")
            .replace(Regex("(?i)\\btake into consideration\\b"), "consider")
            .replace(Regex("(?i)\\bin the event that\\b"), "if")
            .replace(Regex("(?i)\\bwith regard to\\b"), "regarding")
            .replace(Regex("(?i)\\bin reference to\\b"), "regarding")
            .replace(Regex("(?i)\\bat the present moment\\b"), "currently")
            .replace(Regex("(?i)\\bdespite the fact that\\b"), "although")
            .replace(Regex("(?i)\\bin spite of the fact that\\b"), "despite")
            .replace(Regex("(?i)\\ba large number of\\b"), "many")
            .replace(Regex("(?i)\\ba majority of\\b"), "most")
            .trim()
        if (result.isNotEmpty() && result[0].isLowerCase()) {
            result = result.replaceFirstChar { it.uppercase() }
        }
        return result
    }

    private fun applyEloquentStyle(text: String): String {
        var result = text
            .replace(Regex("(?i)\\bvery good\\b"), "exceptional")
            .replace(Regex("(?i)\\bvery bad\\b"), "detrimental")
            .replace(Regex("(?i)\\bvery happy\\b"), "delighted")
            .replace(Regex("(?i)\\bvery important\\b"), "paramount")
            .replace(Regex("(?i)\\bshow\\b"), "demonstrate")
            .replace(Regex("(?i)\\bhelp\\b"), "assist")
            .replace(Regex("(?i)\\bstart\\b"), "commence")
            .replace(Regex("(?i)\\bend\\b"), "conclude")
            .replace(Regex("(?i)\\buse\\b"), "utilize")
            .replace(Regex("(?i)\\bthink\\b"), "contemplate")
            .replace(Regex("(?i)\\bgive\\b"), "provide")
            .replace(Regex("(?i)\\bask\\b"), "inquire")
            .replace(Regex("(?i)\\bmake\\b"), "create")
            .replace(Regex("(?i)\\bchange\\b"), "transform")
            .replace(Regex("(?i)\\bbright\\b"), "luminous")
            .replace(Regex("(?i)\\bhard\\b"), "arduous")
        if (result.isNotEmpty() && result[0].isLowerCase()) {
            result = result.replaceFirstChar { it.uppercase() }
        }
        if (result.isNotEmpty() && !result.endsWith(".") && !result.endsWith("?") && !result.endsWith("!")) {
            result += "."
        }
        return result
    }

    private fun applyVoiceCleanupStyle(text: String): String {
        return text
            .replace(Regex("(?i)\\b(um|uh|er|ah|like|you know|sort of|kind of)\\b,?\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Contextual Auto Format: identifies what kind of text it is (email, list, instructions, notes, address, prose)
     * and formats it based on that text, auto-correcting spelling, typos, and grammar.
     */
    fun autoFormatAndCorrect(input: String): String {
        if (input.isBlank()) return input
        val corrected = quickProofread(input)
        val lower = corrected.lowercase()

        // 1. Email or formal letter detection
        val isEmailCue = lower.startsWith("hi ") || lower.startsWith("hello ") || lower.startsWith("dear ") ||
                lower.startsWith("hey ") || lower.startsWith("good morning") || lower.startsWith("good afternoon") ||
                lower.contains("regards") || lower.contains("thanks,") || lower.contains("thank you,") ||
                lower.contains("best,") || lower.contains("sincerely,") || lower.contains("cheers,")

        // 2. Step-by-step instructions or recipe detection
        val isNumberedCue = Regex("(?i)\\b(first|step 1|1\\.|item 1)\\b").containsMatchIn(corrected) &&
                Regex("(?i)\\b(second|then|after that|next|step 2|2\\.)\\b").containsMatchIn(corrected)

        // 3. To-Do / Checklist detection
        val isChecklistCue = lower.startsWith("todo:") || lower.startsWith("tasks:") || lower.startsWith("checklist:") ||
                lower.startsWith("to-do:") || lower.contains("[ ]") || lower.contains("need to buy") ||
                lower.contains("groceries:") || lower.contains("shopping list:")

        // 4. Bulleted list detection
        val hasListCues = corrected.lines().size > 1 && corrected.lines().any { it.trim().startsWith("-") || it.trim().startsWith("•") || it.trim().startsWith("*") } ||
                lower.contains("buy ") && lower.contains(",") ||
                Regex("(?i)\\b(items|notes|agenda|meeting notes):").containsMatchIn(corrected)

        val formatted = when {
            isEmailCue -> {
                VoiceTranscriptionFormatter.formatTranscription(corrected, TranscriptionFormatStyle.EMAIL)
            }
            isNumberedCue -> {
                VoiceTranscriptionFormatter.formatTranscription(corrected, TranscriptionFormatStyle.NUMBERED)
            }
            isChecklistCue -> {
                VoiceTranscriptionFormatter.formatTranscription(corrected, TranscriptionFormatStyle.CHECKLIST)
            }
            hasListCues -> {
                VoiceTranscriptionFormatter.formatTranscription(corrected, TranscriptionFormatStyle.BULLETS)
            }
            else -> {
                VoiceTranscriptionFormatter.formatTranscription(corrected, TranscriptionFormatStyle.SMART_CLEAN)
            }
        }
        return if (formatted.isNotBlank()) formatted else corrected
    }
}
