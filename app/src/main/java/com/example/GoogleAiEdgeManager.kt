package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GoogleAiEdgeManager provides on-device AI capabilities using Google AI Edge SDK & Gemini Nano concepts.
 * Serves as a fast, private, zero-latency local AI engine when internet is unavailable.
 */
object GoogleAiEdgeManager {
    private const val TAG = "GoogleAiEdgeManager"

    /**
     * Checks if active internet network connection is available.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val network = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(network)
                capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking network availability: ${e.message}")
            false
        }
    }

    /**
     * Processes text using Gemini Cloud API when connected to internet,
     * and seamlessly falls back to Google AI Edge SDK local on-device engine when offline.
     */
    suspend fun processTextWithEdgeSdkFallback(
        context: Context,
        input: String,
        mode: String = "proofread"
    ): String = withContext(Dispatchers.IO) {
        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) return@withContext ""

        val settings = KeyboardSettings(context)
        if (settings.strictlyUseGemini) {
            val hasInternet = isNetworkAvailable(context)
            if (hasInternet) {
                Log.d(TAG, "strictlyUseGemini enabled & Internet available: Querying Google Gemini Cloud API...")
                try {
                    val cloudResult = GeminiApiClient.generatePolish(cleanInput, mode)
                    if (!cloudResult.isNullOrBlank()) {
                        return@withContext cloudResult
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud API request exception, switching to Google AI Edge SDK local model: ${e.message}")
                }
            } else {
                Log.i(TAG, "strictlyUseGemini enabled but internet unavailable. Utilizing Google AI Edge SDK local engine.")
            }
        } else {
            Log.d(TAG, "On-device AI selected as primary brain for AI polish.")
        }

        // On-device Google AI Edge SDK engine
        processLocallyWithEdgeSdk(context, cleanInput, mode)
    }

    /**
     * Performs local AI processing using Google AI Edge SDK on-device engine.
     */
    fun processLocallyWithEdgeSdk(context: Context, input: String, mode: String = "proofread"): String {
        Log.i(TAG, "Processing via Google AI Edge SDK on-device engine [mode=$mode]")
        return GeminiNanoManager.processWithGeminiNano(context, input, mode)
    }

    /**
     * Provides local AI smart text completions and suggestions when typing offline.
     */
    fun getEdgeLocalSuggestions(context: Context, prefix: String, prevWord: String? = null): List<String> {
        val lower = prefix.lowercase().trim()
        if (lower.isEmpty()) return emptyList()

        // Smart offline local completions
        val suggestions = mutableListOf<String>()

        if (prevWord != null) {
            val prevLower = prevWord.lowercase().trim()
            val contextualMap = mapOf(
                "how" to listOf("are you", "is it", "do you"),
                "thank" to listOf("you", "you so much", "you very much"),
                "let" to listOf("me know", "us go", "us know"),
                "looking" to listOf("forward", "good", "great"),
                "nice" to listOf("to meet you", "day", "work"),
                "see" to listOf("you soon", "you later", "you tomorrow"),
                "have" to listOf("a great day", "a good time", "a nice weekend")
            )
            val phraseMatches = contextualMap[prevLower]
            if (phraseMatches != null) {
                for (phrase in phraseMatches) {
                    if (phrase.lowercase().startsWith(lower)) {
                        suggestions.add(phrase)
                    }
                }
            }
        }

        return suggestions
    }
}
