package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * High-performance cloud client for NVIDIA Nemotron AI.
 * Uses NVIDIA's cloud-hosted NIM endpoint at integrate.api.nvidia.com/v1/chat/completions.
 * Powered by NVIDIA-Nemotron-3.5-Lightning-30B-A3B-NVFP4 with low-latency inference.
 */
object NvidiaNemotronClient {
    private const val TAG = "NvidiaNemotronClient"
    private const val API_ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
    const val MODEL_DISPLAY_NAME = "NVIDIA-Nemotron-3.5-Lightning-30B-A3B-NVFP4"
    const val DEFAULT_MODEL = "nvidia/nemotron-3.5-lightning-30b-a3b"

    // Default fallback API key provided by the user
    private const val FALLBACK_API_KEY = "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"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Resolves the active NVIDIA API key from BuildConfig or fallback.
     */
    fun getApiKey(): String {
        return try {
            val buildKey = BuildConfig::class.java.getField("NVIDIA_API_KEY").get(null) as? String
            if (!buildKey.isNullOrBlank() && buildKey != "MY_NVIDIA_API_KEY") {
                buildKey.trim()
            } else {
                FALLBACK_API_KEY
            }
        } catch (_: Throwable) {
            FALLBACK_API_KEY
        }
    }

    /**
     * Resolves the active NVIDIA model name from BuildConfig or default.
     */
    fun getActiveModel(): String {
        return try {
            val buildModel = BuildConfig::class.java.getField("NVIDIA_MODEL").get(null) as? String
            if (!buildModel.isNullOrBlank() && buildModel != "MY_NVIDIA_MODEL") {
                buildModel.trim()
            } else {
                MODEL_DISPLAY_NAME
            }
        } catch (_: Throwable) {
            MODEL_DISPLAY_NAME
        }
    }

    /**
     * Normalizes the model string for the NVIDIA NIM endpoint.
     */
    fun resolveEndpointModel(configuredModel: String? = null): String {
        val model = configuredModel?.takeIf { it.isNotBlank() } ?: getActiveModel()
        return if (model.isBlank() ||
            model.contains("nemotron-3.5-lightning", ignoreCase = true) ||
            model.contains("nemotron-3-ultra", ignoreCase = true) ||
            model.equals(MODEL_DISPLAY_NAME, ignoreCase = true) ||
            model == "MY_NVIDIA_MODEL"
        ) {
            DEFAULT_MODEL
        } else {
            model
        }
    }

    val retrofitClient: NvidiaNemotronRetrofitClient
        get() = NvidiaNemotronRetrofitClient.instance

    /**
     * Proofread text: fixes spelling, punctuation, typos, and grammar using Retrofit client.
     */
    suspend fun proofread(text: String): Result<String> = retrofitClient.proofread(text)

    /**
     * Polishes text according to a designated tone or format style using Retrofit client.
     */
    suspend fun polish(text: String, tone: String): Result<String> = retrofitClient.polish(text, tone)

    /**
     * Generates distinct rephrased options (Professional, Casual, Concise) using Retrofit client.
     */
    suspend fun rephrase(text: String, count: Int = 3): Result<List<String>> = retrofitClient.rephrase(text, count)

    /**
     * Cleans raw voice dictation transcripts using Retrofit client.
     */
    suspend fun cleanVoiceTranscript(text: String): Result<String> = retrofitClient.cleanVoiceTranscript(text)

    suspend fun cleanupVoiceTranscript(text: String): Result<String> = cleanVoiceTranscript(text)

    /**
     * Transforms rambling stream-of-consciousness thoughts using Retrofit client.
     */
    suspend fun processRamble(text: String): Result<String> = retrofitClient.processRamble(text)

    suspend fun rambleModeSynthesis(text: String): Result<String> = processRamble(text)

    /**
     * Tests connectivity to NVIDIA Nemotron NIM and measures response latency in ms.
     */
    suspend fun testConnection(): Result<Long> = retrofitClient.testConnection()

    /**
     * Core request execution against integrate.api.nvidia.com/v1/chat/completions.
     */
    private fun executeChatCompletion(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 256,
        temperature: Double = 0.2
    ): Result<String> {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("NVIDIA API Key is missing. Please configure it in AI Studio Secrets."))
        }

        return try {
            val jsonPayload = JSONObject().apply {
                put("model", resolveEndpointModel())
                put("max_tokens", maxTokens)
                put("temperature", temperature)
                // Disable thinking reasoning tokens to get clean direct output immediately
                put("chat_template_kwargs", JSONObject().apply {
                    put("thinking", false)
                })

                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                }
                put("messages", messages)
            }

            val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(API_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "NVIDIA Nemotron error code ${response.code}: $body")
                    return Result.failure(Exception("NVIDIA API Error (${response.code}): $body"))
                }

                val responseJson = JSONObject(body)
                val choices = responseJson.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.optJSONObject("message")
                    val content = message?.optString("content").orEmpty().trim()

                    // Sanitize potential surrounding quotes
                    val cleanText = sanitizeOutput(content)
                    Result.success(cleanText)
                } else {
                    Result.failure(Exception("Empty choices in response: $body"))
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to call NVIDIA Nemotron API", e)
            Result.failure(e)
        }
    }

    private fun sanitizeOutput(output: String): String {
        var clean = output.trim()
        clean = clean.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        clean = clean.replace(Regex("</?think>"), "").trim()
        // Strip conversational prefix like "Here is the corrected text:" if model leaks it
        val prefixes = listOf(
            "Here's the corrected text:",
            "Here is the corrected text:",
            "Corrected text:",
            "Here is the polished text:",
            "Polished text:"
        )
        for (prefix in prefixes) {
            if (clean.startsWith(prefix, ignoreCase = true)) {
                clean = clean.substring(prefix.length).trim()
            }
        }
        // If wrapped in single or double quotes, unwrap
        if ((clean.startsWith("\"") && clean.endsWith("\"")) ||
            (clean.startsWith("“") && clean.endsWith("”")) ||
            (clean.startsWith("'") && clean.endsWith("'"))
        ) {
            clean = clean.substring(1, clean.length - 1).trim()
        }
        return clean
    }
}
