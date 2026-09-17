package com.example

import android.content.Context

/**
 * OnDeviceProofreadEngine provides local, offline proofreading by combining
 * the neural correction engine and local grammar predictor.
 */
class OnDeviceProofreadEngine private constructor(private val context: Context) {

    private val grammarPredictor by lazy { LocalGrammarSpellPredictor(context) }
    private val neuralEngine by lazy { NeuralCorrectionEngine.getInstance(context) }

    companion object {
        @Volatile
        private var instance: OnDeviceProofreadEngine? = null

        fun getInstance(context: Context): OnDeviceProofreadEngine {
            return instance ?: synchronized(this) {
                instance ?: OnDeviceProofreadEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Proofreads input text using on-device models and rules.
     */
    fun proofread(text: String): String {
        if (text.isBlank()) return text
        val neuralFixed = neuralEngine.correctText(text)
        return grammarPredictor.polishSentenceLocally(neuralFixed)
    }
}
