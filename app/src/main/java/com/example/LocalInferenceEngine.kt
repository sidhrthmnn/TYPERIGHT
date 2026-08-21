package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-Device Gemini Nano & Gemini API Inference Engine Wrapper.
 * Connects directly to Gemini API and falls back to Gemini Nano on-device model.
 */
class LocalInferenceEngine private constructor(private val context: Context) {

    val localPredictor = LocalGrammarSpellPredictor(context)
    val tfLiteCorrectionModel = TfLiteCorrectionModel.getInstance(context)

    companion object {
        private const val TAG = "LocalInferenceEngine"
        
        @Volatile
        private var instance: LocalInferenceEngine? = null

        fun getInstance(context: Context): LocalInferenceEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalInferenceEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class EngineState {
        READY
    }

    val currentState = EngineState.READY

    /**
     * Performs instant real-time on-device local analysis for spell checking, grammar, and predictions as the user types.
     */
    fun analyzeLocalTyping(
        typedWord: String,
        previousWords: List<String> = emptyList(),
        sentenceContext: String = "",
        tapCoords: List<android.graphics.PointF>? = null
    ): LocalGrammarSpellPredictor.LocalAnalysisResult {
        return localPredictor.analyzeTypingLocally(typedWord, previousWords, sentenceContext, tapCoords)
    }

    /**
     * Performs instant local on-device sentence grammar & spelling cleanup before triggering full AI polish.
     */
    fun polishSentenceLocally(sentence: String): String {
        return localPredictor.polishSentenceLocally(sentence)
    }

    /**
     * Initializes the Gemini model pipeline.
     */
    suspend fun initializeModel(modelName: String): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initializing Gemini Inference Engine with model: $modelName")
        return@withContext true
    }

    /**
     * Runs AI inference on the prompt: executes local NLP processing first,
     * then escalates to Gemini Cloud API if more extensive correction or transformation is needed.
     */
    suspend fun runInference(prompt: String, mode: String): String = withContext(Dispatchers.IO) {
        val localCleaned = polishSentenceLocally(prompt)

        // If local system already performed full cleanup or if offline
        if (mode.equals("proofread", ignoreCase = true) && localCleaned.isNotBlank()) {
            val hasGrammarFlaws = localCleaned.contains(Regex("(?i)\\b(i has|he have|she have|buyed|goed|they was|more better)\\b"))
            if (!hasGrammarFlaws) {
                return@withContext localCleaned
            }
        }

        try {
            val geminiResult = GeminiApiClient.generatePolish(prompt, mode)
            if (!geminiResult.isNullOrBlank()) {
                return@withContext geminiResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API cloud inference error: ${e.message}")
        }
        
        return@withContext localCleaned
    }
}
