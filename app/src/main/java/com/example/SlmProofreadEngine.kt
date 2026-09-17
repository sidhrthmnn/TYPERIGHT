package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small Language Model (SLM) on-device proofread engine.
 * Performs fast syntactic proofreading and edit tracking.
 */
class SlmProofreadEngine private constructor(private val context: Context) {

    data class SlmCorrection(
        val original: String,
        val replacement: String
    )

    data class SlmProofreadResult(
        val proofreadText: String,
        val corrections: List<SlmCorrection> = emptyList(),
        val summaryMessage: String = "Syntactic grammar & spelling check completed"
    )

    companion object {
        @Volatile
        private var instance: SlmProofreadEngine? = null

        fun getInstance(context: Context): SlmProofreadEngine {
            return instance ?: synchronized(this) {
                instance ?: SlmProofreadEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun proofread(text: String, tone: String = "Proofread"): SlmProofreadResult = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            return@withContext SlmProofreadResult(proofreadText = text)
        }
        val aiCoreResult = DeviceAiCoreEngine.getInstance(context).proofread(text, tone)
        val corrected = aiCoreResult.correctedText
        val corrections = if (corrected != text) {
            listOf(SlmCorrection(original = text.take(35), replacement = corrected.take(35)))
        } else {
            emptyList()
        }
        SlmProofreadResult(
            proofreadText = corrected,
            corrections = corrections,
            summaryMessage = if (corrections.isNotEmpty()) "Found ${corrections.size} improvements" else "Text is grammatically clean"
        )
    }
}
