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
                CRITICAL DIRECTIVE: You must process and proofread the ENTIRE input text from the very first word to the very last word.
                1. Correct all spelling errors, typos, grammatical mistakes, punctuation errors, apostrophe issues, and capitalization across all sentences.
                2. Polish the flow and clarity of every sentence so the full text reads cleanly, smoothly, and naturally from beginning to end.
                3. Strictly preserve original meaning, facts, numbers, dates, URLs, email addresses, and emojis. Never omit or cut off any part of the text.
                4. Return ONLY the finalized, complete corrected text. Do NOT add any preamble, conversational commentary, quotation marks, or markdown code fences.
            """.trimIndent()

            PolishMode.PROFESSIONAL -> """
                You are an expert executive writing engine inside an Android keyboard.
                CRITICAL MANDATORY DIRECTIVE: You must actively transform and rewrite the ENTIRE input text from start to finish into a polished, crisp, articulate, respectful, and professional business tone.
                - Do NOT merely fix typos. Actively upgrade the vocabulary, sentence cadence, and tone across every single sentence into executive-level professional English (e.g. replace casual phrasing with polite, articulate, constructive phrasing).
                - Ensure impeccable grammar, formal etiquette, and crystal-clear business communication.
                - Preserve all core facts, names, dates, numbers, links, and original intent. Never drop or skip any thoughts or sentences.
                - Return ONLY the finalized professional text without any preamble, commentary, quotes, or markdown code fences.
            """.trimIndent()

            PolishMode.CASUAL -> """
                You are an expert communication assistant inside an Android keyboard.
                CRITICAL MANDATORY DIRECTIVE: You must actively transform and rewrite the ENTIRE input text from start to finish into a warm, natural, friendly, and casual conversational tone.
                - Do NOT merely fix typos. Actively adapt the style across every sentence so it sounds engaging, relatable, warm, and effortless, like a friendly text message or casual conversation.
                - Keep all important facts, details, numbers, and intended meaning intact. Never drop or skip any thoughts or sentences.
                - Return ONLY the finalized friendly casual text without preamble, commentary, quotes, or markdown code fences.
            """.trimIndent()

            PolishMode.SHORTEN -> """
                You are an expert editor inside an Android keyboard.
                CRITICAL MANDATORY DIRECTIVE: Condense and tighten the ENTIRE input text into a concise, direct, high-impact version while retaining all vital context, facts, numbers, dates, and URLs.
                - Cut unnecessary fluff, redundancy, and verbose filler across all sentences.
                - Ensure correct spelling, grammar, and punchy, clean delivery.
                - Return ONLY the shortened text without preamble, quotes, or markdown code fences.
            """.trimIndent()

            PolishMode.EXPAND -> """
                You are an expert writing assistant inside an Android keyboard.
                CRITICAL MANDATORY DIRECTIVE: Elaborate and expand upon the ENTIRE input text naturally from beginning to end.
                - Add thoughtful context, polite phrasing, and complete, well-formed sentence structure to every point made by the user.
                - Never invent fictitious facts, names, or fake dates; expand through clarity, depth, and articulate phrasing.
                - Ensure proper spelling, punctuation, and clean formatting throughout.
                - Return ONLY the expanded text without preamble, quotes, or markdown code fences.
            """.trimIndent()

            PolishMode.REPHRASE -> """
                You are an expert writing and rephrasing engine inside an Android keyboard.
                CRITICAL MANDATORY DIRECTIVE: Completely rephrase and recast the ENTIRE input text using alternate, articulate, elegant vocabulary and varied sentence structure.
                - Do NOT return the same sentence structure or merely fix typos. Actively restyle the phrasing of every single sentence to make it more engaging and expressive while strictly preserving the exact same meaning, facts, numbers, and URLs.
                - Return ONLY the rephrased text without any preamble, conversational commentary, quotes, or markdown code fences.
            """.trimIndent()

            PolishMode.VOICE_CLEANUP -> """
                You are a voice speech-to-text cleanup engine inside an Android keyboard.
                Clean up the entire spoken transcript by removing speech disfluencies, filler words (um, uh, like, you know, er), stutters, repeated words, and resolving spoken self-corrections.
                Format into clear, grammatically correct sentences across the full text.
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
                4. Structure the finalized thoughts into clear, natural, well-punctuated, properly capitalized text covering the entire user thought.
                5. Output ONLY the finalized, transformed text. Do NOT include any conversational preamble, commentary, quotes, or markdown code fences.
            """.trimIndent()
        }
    }

    /**
     * Transcribes an audio byte array (WAV or PCM converted to WAV) using Gemini multimodal audio models.
     * Provides a cloud STT fallback when system SpeechRecognizer is unavailable or failing.
     */
    suspend fun transcribeAudio(
        audioData: ByteArray,
        mimeType: String = "audio/wav",
        promptInstruction: String = "Transcribe this audio recording verbatim into clean, accurately punctuated text. Return ONLY the transcribed text, with no explanations, no quotes, and no preambles."
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
        } catch (e: Exception) {
            null
        }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY" || audioData.isEmpty()) {
            return@withContext null
        }

        val base64Audio = android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
        val modelsToTry = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite-preview")

        for (model in modelsToTry) {
            try {
                val url = "$BASE_URL/$model:generateContent?key=$apiKey"
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64Audio)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", promptInstruction)
                            })
                        })
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
                            val text = parts.getJSONObject(0).optString("text", "").trim()
                            if (text.isNotEmpty()) {
                                Log.i(TAG, "Audio transcribed successfully via $model: $text")
                                return@withContext text
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini audio transcription error ($model): ${e.message}")
            }
        }
        return@withContext null
    }

    /**
     * Converts raw 16-bit Mono PCM byte data to standard WAV byte array with 44-byte RIFF header.
     */
    fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 16000, channels: Int = 1): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Linear PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16 // 16 bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val wavData = ByteArray(44 + pcmData.size)
        System.arraycopy(header, 0, wavData, 0, 44)
        System.arraycopy(pcmData, 0, wavData, 44, pcmData.size)
        return wavData
    }
}
