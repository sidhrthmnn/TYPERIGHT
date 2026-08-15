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
 * GeminiApiClient handles server-side / cloud AI requests using low-latency Google Gemini APIs (gemini-3.1-flash-lite, gemini-2.5-flash, gemini-2.0-flash, gemini-1.5-flash).
 * Understands the context of user input/voice dictation, corrects spelling errors, fixes grammar,
 * and recreates sentences naturally to reflect what the user intended to say.
 */
object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private val CANDIDATE_MODELS = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite-preview", "gemini-2.5-flash")
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Sends the text and mode to Gemini API to understand context, fix spelling/grammar,
     * and recreate the sentence.
     */
    suspend fun generatePolish(input: String, mode: String): String? = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
        } catch (e: Exception) {
            null
        }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured or placeholder. Falling back to local engine.")
            return@withContext null
        }

        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) return@withContext ""

        val modeDescription = when (mode.lowercase()) {
            "proofread", "grammar" -> "Perform smart proofreading for voice dictation and raw typing. Remove all fillers (um, uh, er, like, you know, etc.), stutters, repeated words, and spoken self-corrections (e.g. 'five no wait six' -> '6'). Put everything into proper sentences with correct capitalization, grammar, and punctuation. CRITICAL: If the user is listing things out or enumerating items (such as 'buy milk eggs bread', '1 clean room 2 do laundry 3 cook', 'first finish report second email boss', or items separated by commas/and), rewrite and format them neatly as a bulleted list (using '- ') or numbered list (using '1. ', '2. ')."
            "formalize", "professional" -> "Rewrite the input into a crisp, polished, respectful, and professional business tone while preserving the core message and context."
            "rephrase" -> "Rephrase and articulate the sentence into smooth, natural, and expressive English based on what the user intended to communicate."
            "casual" -> "Convert the text into a warm, natural, friendly, and conversational tone without changing the underlying meaning."
            "shorten" -> "Condense and summarize the input into a concise, direct sentence while retaining all vital context."
            "expand" -> "Elaborate on the input text naturally, adding appropriate detail, polite phrasing, and completing incomplete ideas."
            else -> "Proofread, fix all typos, remove fillers and stutters, and recreate the sentence clearly into well-formatted proper sentences."
        }

        val systemInstructionText = """
            You are an expert AI proofreader and sentence reconstruction engine for a mobile keyboard voice dictation system.
            Goal: $modeDescription

            STRICT RULES:
            1. Understand context and user intent from raw speech-to-text dictation.
            2. Remove ALL filler words (um, uh, er, ah, like, you know, basically, actually), stutters, duplicate adjacent words, and verbal self-corrections (e.g. 'three sorry four' -> '4').
            3. Reformat the raw transcript into proper, well-structured sentences with accurate punctuation (periods, commas, question marks) and proper capitalization.
            4. LIST FORMATTING RULE: While proofreading, if the user is listing things out, enumerating multiple items, or specifying a sequence of tasks (e.g., 'things to buy milk eggs bread', 'first clean second wash third cook', '1 finish report 2 send email', or items separated by 'and'/commas), rewrite and format them as a clean vertical list using bullet points ('- Item') or numbered items ('1. Item', '2. Item').
            5. Do NOT add meta explanations, intro/outro commentary, preamble, or quotes (e.g. do NOT output 'Here is the polished text:').
            6. Output ONLY the final clean polished text string.
        """.trimIndent()

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
                            put("text", cleanInput)
                        }))
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
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
                            val textResult = parts.getJSONObject(0).optString("text", "").trim()
                            if (textResult.isNotEmpty()) {
                                Log.i(TAG, "Gemini API ($model) generated result: $textResult")
                                return@withContext textResult
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Gemini API ($model) HTTP Error ${response.code}, trying next model...")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini API ($model) call exception: ${e.message}")
            }
        }

        return@withContext null
    }
}
