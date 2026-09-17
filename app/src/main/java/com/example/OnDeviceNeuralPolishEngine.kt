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
     * Performs instantaneous on-device proofreading.
     */
    fun quickProofread(input: String): String {
        if (input.isBlank()) return input
        val neuralFixed = neuralEngine.correctText(input)
        return grammarPredictor.polishSentenceLocally(neuralFixed)
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
            .replace(Regex("(?i)\\bHello\\b"), "Hey")
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
}
