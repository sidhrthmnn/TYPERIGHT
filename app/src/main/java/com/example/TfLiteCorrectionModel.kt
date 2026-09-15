package com.example

import android.content.Context

/**
 * Legacy interface retained for backward compatibility.
 * Delegates directly to the modern [NeuralCorrectionEngine] alternative,
 * completely removing dependency on TensorFlow Lite binaries.
 */
class TfLiteCorrectionModel private constructor(private val context: Context) {

    private val engine = NeuralCorrectionEngine.getInstance(context)

    companion object {
        @Volatile
        private var instance: TfLiteCorrectionModel? = null

        fun getInstance(context: Context): TfLiteCorrectionModel {
            return instance ?: synchronized(this) {
                instance ?: TfLiteCorrectionModel(context.applicationContext).also { instance = it }
            }
        }
    }

    fun isModelReady(): Boolean = engine.isModelReady()

    fun correctText(input: String): String = engine.correctText(input)

    fun scoreTransition(w1: String, w2: String): Float = engine.scoreTransition(w1, w2)

    fun close() = engine.close()
}
