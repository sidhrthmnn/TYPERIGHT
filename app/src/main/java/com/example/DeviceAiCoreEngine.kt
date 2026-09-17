package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-Device AICore Engine.
 * Provides instant zero-latency proofreading and grammar checking using local NLP models.
 */
class DeviceAiCoreEngine private constructor(private val context: Context) {

    private val grammarPredictor by lazy { LocalGrammarSpellPredictor(context) }
    private val neuralEngine by lazy { NeuralCorrectionEngine.getInstance(context) }

    data class AiCoreResult(
        val correctedText: String,
        val isSuccess: Boolean = true,
        val confidence: Float = 0.95f
    )

    companion object {
        @Volatile
        private var instance: DeviceAiCoreEngine? = null

        fun getInstance(context: Context): DeviceAiCoreEngine {
            return instance ?: synchronized(this) {
                instance ?: DeviceAiCoreEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Executes proofreading using on-device models.
     */
    suspend fun proofread(text: String, mode: String = "Proofread"): AiCoreResult = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext AiCoreResult(text)

        // 1. Neural typo correction
        val neuralCorrected = neuralEngine.correctText(text)

        // 2. Grammar agreement and sentence-level polish
        val polished = grammarPredictor.polishSentenceLocally(neuralCorrected)

        AiCoreResult(
            correctedText = polished,
            isSuccess = true,
            confidence = 0.96f
        )
    }
}
