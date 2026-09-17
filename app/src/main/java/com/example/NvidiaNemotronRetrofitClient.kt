package com.example

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit-based client for interacting with the NVIDIA Nemotron API.
 * Provides specialized processing for text proofreading, rephrasing,
 * tone polishing, voice transcription cleanup, and ramble synthesis.
 */
class NvidiaNemotronRetrofitClient(
    private val apiKeyProvider: () -> String = { NvidiaNemotronClient.getApiKey() },
    private val modelProvider: () -> String = { NvidiaNemotronClient.resolveEndpointModel() },
    baseUrl: String = BASE_URL,
    customOkHttpClient: OkHttpClient? = null
) {
    companion object {
        private const val TAG = "NemotronRetrofitClient"
        const val BASE_URL = "https://integrate.api.nvidia.com/v1/"
        const val DEFAULT_MODEL = "nvidia/nemotron-3.5-lightning-30b-a3b"

        /**
         * Singleton instance for convenient shared app-wide access.
         */
        val instance: NvidiaNemotronRetrofitClient by lazy {
            NvidiaNemotronRetrofitClient()
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = customOkHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(this.okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: NvidiaNemotronApiService = retrofit.create(NvidiaNemotronApiService::class.java)

    /**
     * Proofread text: fixes spelling, punctuation, typos, and grammar using NVIDIA Nemotron.
     */
    suspend fun proofread(text: String, model: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success("")

        val systemPrompt = "You are a professional grammar and writing assistant for a mobile keyboard. " +
                "Fix all spelling, typos, punctuation, and grammatical mistakes in the user's text. " +
                "Do NOT write any thoughts, reasoning, or thinking process. " +
                "Return ONLY the clean corrected text. Do NOT include quotes, explanations, preface, or commentary."

        executeChatCompletion(
            systemPrompt = systemPrompt,
            userMessage = trimmed,
            maxTokens = (trimmed.length * 2).coerceIn(64, 512),
            temperature = 0.1,
            modelOverride = model
        )
    }

    /**
     * Generates distinct rephrased alternatives (e.g. Professional, Casual, Concise) for the provided text.
     */
    suspend fun rephrase(text: String, count: Int = 3, model: String? = null): Result<List<String>> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(emptyList())

        val systemPrompt = "You are an intelligent keyboard rephrasing engine. Given the user's input, " +
                "provide exactly $count different rephrased alternatives (e.g. Professional, Casual, Concise). " +
                "Return the output as lines separated by newlines, with each line starting with [1], [2], etc. " +
                "Do NOT include any other text."

        val result = executeChatCompletion(
            systemPrompt = systemPrompt,
            userMessage = trimmed,
            maxTokens = 256,
            temperature = 0.4,
            modelOverride = model
        )

        result.map { rawOutput ->
            val lines = rawOutput.lines()
                .map { line ->
                    line.replace(Regex("^\\[?\\d+\\]?\\.?\\s*"), "").trim()
                }
                .filter { it.isNotBlank() && !it.startsWith("Here") }
                .distinct()

            if (lines.isNotEmpty()) {
                lines.take(count)
            } else {
                listOf(rawOutput.trim())
            }
        }
    }

    /**
     * Polishes text according to a designated tone or format style.
     */
    suspend fun polish(text: String, tone: String, model: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success("")

        val instruction = when (tone.lowercase()) {
            "professional", "formal" -> "Rewrite the text into a polished, professional, and courteous tone suitable for business communication."
            "casual", "friendly" -> "Rewrite the text into a warm, natural, and friendly conversational tone."
            "concise", "short" -> "Condense and simplify the text to be punchy, clear, and direct without unnecessary filler."
            "academic" -> "Rewrite the text with articulate, scholarly vocabulary and sophisticated sentence structure."
            "bullets", "bullet points" -> "Convert the thoughts and points into clean, structured bullet points starting with •."
            "checklist" -> "Convert the tasks into a clear action checklist starting with ☐."
            "email" -> "Format the text into a clean email message with appropriate greeting, paragraphs, and sign-off."
            else -> "Improve the clarity, phrasing, and flow of the text while preserving its exact intent."
        }

        val systemPrompt = "You are an expert writing assistant for a mobile keyboard. $instruction " +
                "Return ONLY the rewritten output. Do NOT include reasoning, greetings, quotes, or explanations."

        executeChatCompletion(
            systemPrompt = systemPrompt,
            userMessage = trimmed,
            maxTokens = (trimmed.length * 3).coerceIn(96, 768),
            temperature = 0.3,
            modelOverride = model
        )
    }

    /**
     * Cleans raw voice dictation transcripts by stripping filler words, resolving mid-sentence
     * self-corrections, and standardizing spoken numbers and punctuation.
     */
    suspend fun cleanVoiceTranscript(text: String, model: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success("")

        val systemPrompt = "You are an expert voice dictation editor. Clean this spoken transcript: " +
                "1. Remove vocal disfluencies, stuttering, and filler words (um, uh, like, you know, sort of). " +
                "2. Resolve mid-sentence self-corrections (e.g. 'let's meet Tuesday wait no Wednesday' -> 'let's meet Wednesday'). " +
                "3. Convert spoken punctuation ('comma', 'question mark', 'new line') to real punctuation. " +
                "4. Format spoken times, currencies, and numbers into standard written forms ($20, 3:30 PM). " +
                "Return ONLY the cleaned transcript. No explanations or quotes."

        executeChatCompletion(
            systemPrompt = systemPrompt,
            userMessage = trimmed,
            maxTokens = (trimmed.length * 2).coerceIn(64, 512),
            temperature = 0.1,
            modelOverride = model
        )
    }

    /**
     * Transforms rambling thoughts into coherent, structured notes or bullet points.
     */
    suspend fun processRamble(text: String, model: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success("")

        val systemPrompt = "You are an AI thought organizer for a mobile keyboard. The user spoke rambling thoughts. " +
                "Synthesize their rambling ideas into a clean, concise, coherent message or structured bullet points. " +
                "Preserve all key details and action items. Return ONLY the organized text."

        executeChatCompletion(
            systemPrompt = systemPrompt,
            userMessage = trimmed,
            maxTokens = (trimmed.length * 2).coerceIn(128, 768),
            temperature = 0.2,
            modelOverride = model
        )
    }

    /**
     * Measures latency to the NVIDIA Nemotron API in milliseconds.
     */
    suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val result = executeChatCompletion(
            systemPrompt = "Respond with one word: OK",
            userMessage = "ping",
            maxTokens = 10,
            temperature = 0.0
        )
        val latency = System.currentTimeMillis() - start
        if (result.isSuccess) {
            Result.success(latency)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    /**
     * Executes the chat completion call via Retrofit.
     */
    suspend fun executeChatCompletion(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 256,
        temperature: Double = 0.2,
        modelOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("NVIDIA API Key is missing. Please configure it in AI Studio Secrets."))
        }

        val resolvedModel = modelOverride?.takeIf { it.isNotBlank() } ?: modelProvider()

        val request = NemotronChatRequest(
            model = resolvedModel,
            messages = listOf(
                NemotronChatMessage(role = "system", content = systemPrompt),
                NemotronChatMessage(role = "user", content = userMessage)
            ),
            maxTokens = maxTokens,
            temperature = temperature,
            stream = false,
            chatTemplateKwargs = mapOf("thinking" to false)
        )

        try {
            val response = apiService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                Log.e(TAG, "NVIDIA Nemotron Retrofit error code ${response.code()}: $errorBody")
                return@withContext Result.failure(Exception("NVIDIA API Error (${response.code()}): $errorBody"))
            }

            val body = response.body()
            val content = body?.choices?.firstOrNull()?.message?.content.orEmpty().trim()
            if (content.isNotEmpty()) {
                val cleanText = sanitizeOutput(content)
                Result.success(cleanText)
            } else {
                Result.failure(Exception("Empty choices in Retrofit response: $body"))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to call NVIDIA Nemotron via Retrofit", e)
            Result.failure(e)
        }
    }

    private fun sanitizeOutput(output: String): String {
        var clean = output.trim()
        // Strip thinking/reasoning tags if model output contains <think>...</think> or stray </think>
        clean = clean.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        clean = clean.replace(Regex("</?think>"), "").trim()

        // Strip thinking process prefixes if model leaks conversational chain-of-thought
        if (clean.contains("thinking process", ignoreCase = true)) {
            val outputMarker = Regex("(?i)(\\*\\*Output:?\\*\\*|Output:|Result:|Final text:?|Corrected text:?)")
            val parts = clean.split(outputMarker)
            if (parts.size > 1) {
                clean = parts.last().trim()
            } else {
                val lines = clean.lines()
                val nonThinking = lines.filterNot { 
                    it.contains("thinking process", ignoreCase = true) || 
                    it.trim().startsWith("1.") || 
                    it.trim().startsWith("2.") ||
                    it.trim().startsWith("3.") ||
                    it.trim().startsWith("-") ||
                    it.trim().startsWith("*")
                }.filter { it.isNotBlank() }
                if (nonThinking.isNotEmpty()) {
                    clean = nonThinking.last().trim()
                }
            }
        }

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

        if ((clean.startsWith("\"") && clean.endsWith("\"")) ||
            (clean.startsWith("“") && clean.endsWith("”")) ||
            (clean.startsWith("'") && clean.endsWith("'"))
        ) {
            clean = clean.substring(1, clean.length - 1).trim()
        }
        return clean
    }
}
