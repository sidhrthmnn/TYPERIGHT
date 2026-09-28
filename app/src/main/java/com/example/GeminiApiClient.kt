package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    // Fast and reliable Gemini Flash models with low latency
    private val CANDIDATE_MODELS = listOf(
        "gemini-3.1-flash-lite-preview",
        "gemini-3.5-flash"
    )
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(4000, TimeUnit.MILLISECONDS)
        .writeTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private fun resolveModel(modelName: String?): String {
        if (modelName.isNullOrBlank()) return "gemini-3.1-flash-lite-preview"
        val lower = modelName.lowercase().trim()
        if (lower.contains("3.5-flash-lite") || lower.contains("2.5-flash-lite") || lower.contains("flash-lite")) {
            return "gemini-3.1-flash-lite-preview"
        }
        if (lower.contains("3.5-flash") || lower == "gemini-flash-latest") {
            return "gemini-3.5-flash"
        }
        if (lower.contains("pro")) {
            return "gemini-3.1-pro-preview"
        }
        return "gemini-3.1-flash-lite-preview"
    }

    // Fast in-memory cache to return previous polish results with zero latency
    private val polishCache = java.util.concurrent.ConcurrentHashMap<String, String>()

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
        context: TextContext? = null,
        preferredModel: String? = null
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

        val cacheKey = "${mode.name}:${cleanInput.hashCode()}:$cleanInput"
        polishCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Zero-latency cache hit for: $cleanInput")
            return@withContext cached
        }

        val systemInstructionText = getSystemInstructionText(mode)

        // Build prompt payload with minimal context if available
        val promptText = if (context != null && context.selectedText.isNullOrEmpty() && context.previousSentence?.isNotBlank() == true) {
            "Context: ${context.previousSentence}\nInput: $cleanInput"
        } else {
            cleanInput
        }

        val resolved = resolveModel(preferredModel)
        val modelsToTry = listOf(resolved) + (CANDIDATE_MODELS - resolved)

        for (model in modelsToTry) {
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
                                polishCache[cacheKey] = sanitized
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

    /**
     * Streams polished words in real-time chunk-by-chunk using SSE streamGenerateContent.
     */
    fun streamPolish(
        input: String,
        mode: PolishMode,
        context: TextContext? = null,
        preferredModel: String? = null
    ): Flow<String> = flow {
        val apiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
        } catch (e: Exception) {
            null
        }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@flow
        }

        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) {
            emit("")
            return@flow
        }

        val cacheKey = "${mode.name}:${cleanInput.hashCode()}:$cleanInput"
        polishCache[cacheKey]?.let { cached ->
            emit(cached)
            return@flow
        }

        val systemInstructionText = getSystemInstructionText(mode)
        val promptText = if (context != null && context.selectedText.isNullOrEmpty() && context.previousSentence?.isNotBlank() == true) {
            "Context: ${context.previousSentence}\nInput: $cleanInput"
        } else {
            cleanInput
        }

        val resolved = resolveModel(preferredModel)
        val modelsToTry = listOf(resolved) + (CANDIDATE_MODELS - resolved)

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

        for (model in modelsToTry) {
            var streamSuccess = false
            val accumulated = StringBuilder()
            try {
                val url = "$BASE_URL/$model:streamGenerateContent?alt=sse&key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    response.body!!.byteStream().bufferedReader(Charsets.UTF_8).useLines { lines ->
                        for (line in lines) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("data:")) {
                                val dataStr = trimmed.removePrefix("data:").trim()
                                if (dataStr.isNotEmpty() && dataStr != "[DONE]") {
                                    try {
                                        val jsonObj = JSONObject(dataStr)
                                        val candidates = jsonObj.optJSONArray("candidates")
                                        if (candidates != null && candidates.length() > 0) {
                                            val first = candidates.getJSONObject(0)
                                            val content = first.optJSONObject("content")
                                            val parts = content?.optJSONArray("parts")
                                            if (parts != null && parts.length() > 0) {
                                                val chunk = parts.getJSONObject(0).optString("text", "")
                                                if (chunk.isNotEmpty()) {
                                                    accumulated.append(chunk)
                                                    streamSuccess = true
                                                    emit(accumulated.toString())
                                                }
                                            }
                                        }
                                    } catch (je: Exception) {
                                        // Ignore partial parsing error
                                    }
                                }
                            }
                        }
                    }
                }
                if (streamSuccess && accumulated.isNotEmpty()) {
                    val sanitized = AiOutputValidator.sanitize(accumulated.toString().trim(), cleanInput)
                    if (sanitized.isNotEmpty()) {
                        polishCache[cacheKey] = sanitized
                        emit(sanitized)
                    }
                    return@flow
                }
            } catch (e: Exception) {
                Log.d(TAG, "Streaming ($model) failed: ${e.message}")
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun getSystemInstructionText(mode: PolishMode): String {
        return when (mode) {
            PolishMode.AUTO_FORMAT -> """
                You are an advanced, context-aware Auto-Formatting and Text Polishing engine inside an Android keyboard.
                Your core capability is to understand exactly what kind of text the user has provided and format it intelligently based on that specific text type, while fixing all grammar, spelling, and punctuation errors.

                STEP 1: IDENTIFY WHAT THE TEXT IS AND FORMAT ACCORDINGLY:
                - Email or formal communication (contains greeting like "hi/hello/dear", sign-off like "thanks/regards/best", or recipient context):
                  Format with the greeting on its own line, followed by an empty line, well-structured body paragraphs with blank lines between them, and the sign-off cleanly separated at the end on its own line.
                - To-Do / Task / Grocery / Shopping List (contains action verbs, multiple items, grocery ingredients, or errands):
                  Format as a clean bulleted list using '• ' for each item with capitalized first letter, or numbered items if prioritized.
                - Step-by-Step Instructions or Recipe (sequential actions, words like "first, next, then, finally", or numbered steps):
                  Format as an ordered numbered list (1., 2., 3.) with clear punctuation and capitalized steps.
                - Meeting Notes / Agenda (discussions, decisions, key takeaways, action items):
                  Format with appropriate section labels (e.g. Agenda:, Discussion:, Action Items:) and distinct bullet points.
                - Chat / Conversational Message (short thoughts, banter, quick replies):
                  Format into clean, natural conversational text with proper punctuation, natural spacing, and all emojis preserved.
                - Address or Contact Information (street names, cities, zip codes, phone numbers, emails):
                  Format across separate standard postal/contact lines.
                - Technical / Code / Command / Query:
                  Format with clean indentation, proper newlines, and preserved code syntax/tokens.
                - Multi-sentence Prose / Narrative / Essay:
                  Organize into flowing sentences, breaking up run-on thoughts, and inserting clean paragraph breaks every 2-3 sentences.

                STEP 2: AUTO-CORRECT ALL ERRORS IN THE TEXT:
                - Fix all spelling errors, typos, and mistyped words.
                - Fix missing apostrophes in common contractions (e.g. dont -> don't, cant -> can't, im -> I'm, ive -> I've, thats -> that's, didnt -> didn't).
                - Fix all grammatical errors, tense inconsistencies, and subject-verb agreements.
                - Capitalize the personal pronoun "I", the first word of every sentence, and proper nouns.
                - Standardize punctuation (clean commas, periods, quotation marks, and no double spaces).

                STEP 3: INTEGRITY AND OUTPUT CONSTRAINTS:
                - Strictly preserve ALL user meaning, facts, numbers, dates, addresses, links, and emojis.
                - Never invent extra facts or remove user content.
                - Output ONLY the formatted and corrected text. Do NOT wrap in quotes, do NOT include markdown code fences (```), and do NOT add any introductory explanation or commentary.
            """.trimIndent()

            PolishMode.PROOFREAD, PolishMode.POLISH -> """
                You are an expert writing and proofreading engine inside an Android keyboard.
                Your task is to take the entire input text and thoroughly format, edit, spell-check, and proofread it:
                1. Correct all spelling errors, typos, grammatical mistakes, punctuation errors, apostrophe issues, and capitalization across the entire text.
                2. Format, punctuate, and edit the full text so it reads naturally, fluently, and cleanly from start to finish.
                3. Strictly preserve the original meaning, facts, numbers, dates, URLs, email addresses, and emojis.
                4. Return ONLY the finalized, complete corrected text. Do NOT add any preamble, conversational commentary, quotation marks, or markdown code fences.
            """.trimIndent()

            PolishMode.PROFESSIONAL -> """
                You are an expert executive writing engine inside an Android keyboard.
                Transform the entire input text into a crisp, articulate, respectful, and professional business tone.
                Ensure formal vocabulary, impeccable grammar, proper capitalization and punctuation, and business etiquette while preserving all core facts, names, numbers, and URLs.
                Return ONLY the finalized professional text without any preamble, commentary, quotes, or markdown code fences.
            """.trimIndent()

            PolishMode.CASUAL -> """
                You are an expert communication assistant inside an Android keyboard.
                Rewrite the entire input text into a warm, natural, friendly, and casual conversational tone.
                Make it sound relatable, smooth, and effortless while keeping all important facts, details, and meaning intact. Ensure correct spelling, proper punctuation, and great flow.
                Return ONLY the finalized casual text without preamble, commentary, quotes, or markdown code fences.
            """.trimIndent()

            PolishMode.SHORTEN -> """
                You are an expert text editor inside an Android keyboard.
                Condense and summarize the entire input text into a concise, direct, and punchy version while retaining all vital context, facts, numbers, and URLs.
                Ensure correct spelling, grammar, and clean formatting throughout.
                Return ONLY the shortened text without preamble, quotes, or markdown code fences.
            """.trimIndent()

            PolishMode.EXPAND -> """
                You are an expert writing assistant inside an Android keyboard.
                Elaborate on the entire input text naturally, adding appropriate detail, polite phrasing, and complete sentence structure while strictly preserving context.
                Ensure proper spelling, punctuation, and clean formatting throughout.
                Return ONLY the expanded text without preamble, quotes, or markdown code fences.
            """.trimIndent()

            PolishMode.REPHRASE -> """
                You are an expert writing and rephrasing engine inside an Android keyboard.
                Rewrite and rephrase the entire input text to make it articulate, fluent, well-crafted, and engaging.
                Ensure the full text is completely proofread, spell-checked, and formatted with proper punctuation and natural cadence while strictly preserving the core message, facts, numbers, and URLs.
                Return ONLY the rephrased text without any preamble, conversational commentary, quotes, or markdown code fences.
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
    }
}
