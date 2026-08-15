package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local AI Model Manager running on-device Google AI Edge SDK & Gemini Nano model with Gemini API primary pipeline.
 * Provides text polishing, voice dictation formatting, and AI assistant actions.
 */
object LocalAiModelManager {

    /**
     * Executes AI text processing. Tries Gemini Cloud API first, and falls back to Google AI Edge SDK on-device engine.
     */
    suspend fun processText(
        context: Context,
        prompt: String,
        mode: String
    ): String = withContext(Dispatchers.IO) {
        GoogleAiEdgeManager.processTextWithEdgeSdkFallback(context, prompt, mode)
    }
}
