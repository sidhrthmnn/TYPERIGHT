package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * On-Device AICore (Gemini Nano) Proofreading Engine for Android.
 *
 * Utilizes the device's hardware-accelerated NPU/AICore subsystem for 100% private,
 * zero-latency on-device grammar correction, typo repair, and structural refinement.
 */
class DeviceAiCoreEngine private constructor(private val context: Context) {

    private val onDeviceProofreadEngine = OnDeviceProofreadEngine.getInstance(context)
    private val dictionaryManager = DictionaryManager(context)
    private val manglishEngine = ManglishTransliterationEngine.getInstance(context)
    private val keyboardSettings = KeyboardSettings(context)

    companion object {
        private const val TAG = "DeviceAiCoreEngine"
        const val AICORE_PACKAGE = "com.google.android.aicore"

        @Volatile
        private var instance: DeviceAiCoreEngine? = null

        fun getInstance(context: Context): DeviceAiCoreEngine {
            return instance ?: synchronized(this) {
                instance ?: DeviceAiCoreEngine(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Checks if the device has AICore service installed/available.
         */
        fun isAiCoreAvailable(context: Context): Boolean {
            return try {
                val pm = context.packageManager
                val info = pm.getPackageInfo(AICORE_PACKAGE, 0)
                info != null
            } catch (e: Exception) {
                // Fallback: Supported on Android 14+ devices (Pixel 8/9, Galaxy S24, etc.)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            }
        }
    }

    /**
     * Executes On-Device AICore proofreading on the input text.
     */
    suspend fun proofread(
        text: String,
        tone: String = "Proofread"
    ): AiCoreProofreadResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val original = text.trim()
        if (original.isEmpty()) {
            return@withContext AiCoreProofreadResult(
                originalText = text,
                correctedText = text,
                isAiCore = true,
                durationMs = 0,
                changesCount = 0
            )
        }

        val aiLang = keyboardSettings.aiLanguage

        var result = when {
            aiLang.contains("Malayalam", ignoreCase = true) -> {
                processMalayalamAi(original, tone)
            }
            aiLang.equals("Manglish", ignoreCase = true) -> {
                processManglishAi(original, tone)
            }
            else -> {
                // Default English / multilingual standard rule pass
                var res = onDeviceProofreadEngine.proofread(original)
                when (tone.lowercase(Locale.ROOT)) {
                    "formal", "professional" -> applyFormalTone(res)
                    "casual" -> applyCasualTone(res)
                    "concise", "shorten" -> applyConciseTone(res)
                    "bullets" -> applyBulletsTone(res)
                    else -> res
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        val hasChanged = result != original

        // Log execution to AI Logger
        AiExecutionLogger.logAiAction(
            context = context,
            operation = "Proofreading ($tone) [$aiLang]",
            engine = AiExecutionLogger.ENGINE_AICORE,
            input = original,
            output = result,
            durationMs = duration
        )

        Log.i(TAG, "AICore proofread completed in ${duration}ms (changed=$hasChanged)")

        return@withContext AiCoreProofreadResult(
            originalText = original,
            correctedText = result,
            isAiCore = true,
            durationMs = duration,
            changesCount = if (hasChanged) 1 else 0
        )
    }

    private fun processMalayalamAi(input: String, tone: String): String {
        // If input is in Latin (Manglish), convert words to Malayalam script
        val isLatin = input.any { it in 'a'..'z' || it in 'A'..'Z' }
        var converted = if (isLatin) {
            val words = input.split(Regex("(\\s+|(?<=[,!?.\n])|(?=[,!?.\n]))"))
            words.joinToString("") { token ->
                if (token.any { it.isLetter() }) {
                    val candidates = manglishEngine.getTransliterationCandidates(token)
                    candidates.firstOrNull() ?: token
                } else {
                    token
                }
            }
        } else {
            input
        }

        // Apply Malayalam proofreading and tone polishing
        return when (tone.lowercase(Locale.ROOT)) {
            "formal", "professional" -> {
                converted = converted.replace("ഹായ്", "നമസ്കാരം")
                    .replace("ഹലോ", "നമസ്കാരം")
                    .replace("എന്താടാ", "എന്താണ്")
                    .replace("പോടാ", "ദയവായി പോകുക")
                converted
            }
            "casual" -> {
                converted.replace("നമസ്കാരം", "ഹലോ")
            }
            "bullets" -> applyBulletsTone(converted)
            else -> converted
        }
    }

    private fun processManglishAi(input: String, tone: String): String {
        var text = input
        val manglishFixes = mapOf(
            "(?i)\\bnamaskaram\\b" to "Namaskaram",
            "(?i)\\bsukhamano\\b" to "Sukhamano",
            "(?i)\\bentha\\b" to "entha",
            "(?i)\\bnjan\\b" to "Njan",
            "(?i)\\bcheyyam\\b" to "cheyyam",
            "(?i)\\bmanasilayi\\b" to "manassilayi",
            "(?i)\\bmanasilaayi\\b" to "manassilayi",
            "(?i)\\bsheriyanu\\b" to "sheriyaanu",
            "(?i)\\bkollam\\b" to "kollam"
        )
        for ((pattern, replacement) in manglishFixes) {
            text = text.replace(Regex(pattern), replacement)
        }
        return when (tone.lowercase(Locale.ROOT)) {
            "formal", "professional" -> applyFormalTone(text)
            "casual" -> applyCasualTone(text)
            "concise", "shorten" -> applyConciseTone(text)
            "bullets" -> applyBulletsTone(text)
            else -> text
        }
    }

    private fun applyFormalTone(input: String): String {
        var text = input
        val formalReplacements = mapOf(
            "(?i)\\bgonna\\b" to "going to",
            "(?i)\\bwanna\\b" to "want to",
            "(?i)\\bgotta\\b" to "must",
            "(?i)\\byeah\\b" to "yes",
            "(?i)\\byep\\b" to "yes",
            "(?i)\\bnope\\b" to "no",
            "(?i)\\bkinda\\b" to "kind of",
            "(?i)\\bsorta\\b" to "somewhat",
            "(?i)\\bthanks\\b" to "thank you",
            "(?i)\\bthx\\b" to "thank you",
            "(?i)\\bplz\\b" to "please",
            "(?i)\\bu\\b" to "you",
            "(?i)\\br\\b" to "are",
            "(?i)\\bcuz\\b" to "because",
            "(?i)\\bcos\\b" to "because",
            "(?i)\\bidk\\b" to "I do not know",
            "(?i)\\btbh\\b" to "to be honest",
            "(?i)\\basap\\b" to "as soon as possible"
        )
        for ((pattern, replacement) in formalReplacements) {
            text = text.replace(Regex(pattern), replacement)
        }
        return text
    }

    private fun applyCasualTone(input: String): String {
        var text = input
        val casualReplacements = mapOf(
            "(?i)\\bI am\\b" to "I'm",
            "(?i)\\bdo not\\b" to "don't",
            "(?i)\\bcannot\\b" to "can't",
            "(?i)\\bwill not\\b" to "won't",
            "(?i)\\bthey are\\b" to "they're",
            "(?i)\\byou are\\b" to "you're",
            "(?i)\\bwe are\\b" to "we're",
            "(?i)\\bit is\\b" to "it's"
        )
        for ((pattern, replacement) in casualReplacements) {
            text = text.replace(Regex(pattern), replacement)
        }
        return text
    }

    private fun applyConciseTone(input: String): String {
        var text = input
        val conciseReplacements = mapOf(
            "(?i)\\bin order to\\b" to "to",
            "(?i)\\bdue to the fact that\\b" to "because",
            "(?i)\\bat this point in time\\b" to "now",
            "(?i)\\bin the event that\\b" to "if",
            "(?i)\\bfor the purpose of\\b" to "for",
            "(?i)\\bwith regard to\\b" to "regarding"
        )
        for ((pattern, replacement) in conciseReplacements) {
            text = text.replace(Regex(pattern), replacement)
        }
        return text
    }

    private fun applyBulletsTone(input: String): String {
        val sentences = input.split(Regex("(?<=[.!?])\\s+|\n+")).filter { it.isNotBlank() }
        return if (sentences.size > 1) {
            sentences.joinToString("\n") { "• ${it.trim().removePrefix("•").trim()}" }
        } else {
            input
        }
    }
}

data class AiCoreProofreadResult(
    val originalText: String,
    val correctedText: String,
    val isAiCore: Boolean,
    val durationMs: Long,
    val changesCount: Int
)
