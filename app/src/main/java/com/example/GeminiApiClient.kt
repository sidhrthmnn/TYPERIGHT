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
 * GeminiApiClient handles cloud AI inference using fast Google Gemini Flash models.
 * Strictly adheres to mode-specific prompt constraints and privacy boundaries.
 */
object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private val CANDIDATE_MODELS = listOf(
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite-preview",
        "gemini-flash-latest"
    )
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * String-based backward compatible signature.
     */
    suspend fun generatePolish(input: String, mode: String): String? {
        val polishMode = PolishMode.fromString(mode)
        return generatePolish(input, polishMode)
    }

    /**
     * Sends the text and specific PolishMode to Gemini API.
     */
    suspend fun generatePolish(
        input: String,
        mode: PolishMode,
        context: TextContext? = null
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
        } catch (e: Exception) {
            null
        }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "Gemini API key not configured. Using on-device inference pipeline.")
            return@withContext null
        }

        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) return@withContext ""

        val systemInstructionText = when (mode) {
            PolishMode.PROOFREAD -> """
                You are a proofreading engine inside an Android keyboard.
                Make the minimum changes necessary to correct the text.
                Preserve meaning, tone, slang, names, numbers, URLs, technical terms and emojis.
                Do not add information.
                Do not remove information.
                Do not rewrite text that is already correct.
                Return ONLY the corrected text without any preamble, quotes or markdown.
            """.trimIndent()

            PolishMode.POLISH -> """
                You are an AI text polish engine inside an Android keyboard.
                Improve natural flow, sentence cadence, and clarity while strictly preserving the author's meaning, tone, names, URLs, numbers, and emojis.
                Return ONLY the polished text without any preamble, quotes or markdown.
            """.trimIndent()

            PolishMode.PROFESSIONAL -> """
                You are an AI text rewriting engine inside an Android keyboard.
                Rewrite the input into a crisp, polished, respectful, and professional business tone while preserving the core message, facts, numbers, and URLs.
                Return ONLY the rewritten text without preamble, quotes or markdown.
            """.trimIndent()

            PolishMode.CASUAL -> """
                You are an AI text rewriting engine inside an Android keyboard.
                Convert the text into a warm, natural, friendly, and conversational tone without changing the underlying meaning or details.
                Return ONLY the rewritten text without preamble, quotes or markdown.
            """.trimIndent()

            PolishMode.SHORTEN -> """
                You are an AI text editing engine inside an Android keyboard.
                Condense and summarize the input into a concise, direct text while retaining all vital context, facts, numbers, and URLs.
                Return ONLY the shortened text without preamble, quotes or markdown.
            """.trimIndent()

            PolishMode.EXPAND -> """
                You are an AI text editing engine inside an Android keyboard.
                Elaborate on the input text naturally, adding appropriate detail, polite phrasing, and completing incomplete ideas while strictly preserving context.
                Return ONLY the expanded text without preamble, quotes or markdown.
            """.trimIndent()

            PolishMode.REPHRASE -> """
                You are an AI text rephrasing engine inside an Android keyboard.
                Articulate the sentence smoothly, cleanly, and clearly into expressive English based on what the user intended to communicate.
                Return ONLY the rephrased text without preamble, quotes or markdown.
            """.trimIndent()

            PolishMode.VOICE_CLEANUP -> """
                You are a voice speech-to-text cleanup engine inside an Android keyboard.
                Clean up spoken transcripts by removing speech disfluencies, filler words (um, uh, like, you know, er), stutters, repeated words, and resolving spoken self-corrections (e.g. 'five no wait six' becomes '6').
                Format into clear, grammatically correct sentences.
                Preserve names, numbers, and meaning.
                Return ONLY the clean text without preamble, quotes or markdown.
            """.trimIndent()

            PolishMode.RAMBLE -> """
                You are an intent-based voice dictation engine ("Ramble Mode") inside an Android keyboard.
                The input is raw transcript text from a user speaking freely or rambling.
                
                Your transformation rules:
                1. Strip all vocal disfluencies, filler words ("um", "uh", "like", "you know", "kind of", "sort of", "er", "ah", "basically", "literally", "so yeah"), false starts, and repeated words.
                2. Resolve live mid-sentence self-corrections (e.g., "Let's meet at 2... actually let's make it 4" -> "Let's meet at 4", "send this to John sorry I mean Sarah" -> "Send this to Sarah").
                3. Process inline or trailing voice instructions appended to the thought (e.g., "...make this more concise" -> condense it, "...translate to Spanish" -> translate, "...bullet points please" -> format as bullet points, "...sound professional" -> format professionally, "...make it a todo list" -> format as checklist).
                4. Structure the finalized thoughts into clear, natural, well-punctuated, properly capitalized text.
                5. Output ONLY the finalized, transformed text. Do NOT include any conversational preamble, commentary, quotes, or markdown code fences.
            """.trimIndent()
        }

        // Build prompt payload with minimal context if available
        val promptText = if (context != null && context.selectedText.isNullOrEmpty() && context.previousSentence?.isNotBlank() == true) {
            "Context: ${context.previousSentence}\nInput: $cleanInput"
        } else {
            cleanInput
        }

        // Try candidate models in order of speed and capability
        for (model in CANDIDATE_MODELS) {
            try {
                val url = "$BASE_URL/$model:generateContent?key=$apiKey"

                val jsonBody = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", systemInstructionText)
                        }))
                    })
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", promptText)
                        }))
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.0)
                        put("topP", 0.95)
                    })
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string()

                if (response.isSuccessful && !responseString.isNullOrEmpty()) {
                    val jsonResponse = JSONObject(responseString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val rawTextResult = parts.getJSONObject(0).optString("text", "").trim()
                            val sanitized = AiOutputValidator.sanitize(rawTextResult, cleanInput)
                            if (sanitized.isNotEmpty()) {
                                return@withContext sanitized
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Gemini API ($model) response code: ${response.code}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Gemini API ($model) request failed: ${e.message}")
            }
        }

        return@withContext null
    }
}
