package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates local deterministic rules, on-device neural/TFLite models,
 * quality/confidence evaluation, and cloud fallback via Gemini.
 */
class LocalInferenceEngine private constructor(private val context: Context) {

    val localPredictor = LocalGrammarSpellPredictor(context)
    val tfLiteCorrectionModel = TfLiteCorrectionModel.getInstance(context)
    val keyboardSettings = KeyboardSettings(context)
    val dictionaryManager by lazy { DictionaryManager(context) }
    private val db by lazy { AppDatabase.getDatabase(context) }
    private val grammarRuleDao by lazy { db.grammarRuleDao() }

    companion object {
        private const val TAG = "LocalInferenceEngine"
        const val DEFAULT_LOCAL_CONFIDENCE_THRESHOLD = 0.90f

        @Volatile
        private var instance: LocalInferenceEngine? = null

        fun getInstance(context: Context): LocalInferenceEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalInferenceEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class EngineState {
        READY
    }

    val currentState = EngineState.READY

    /**
     * Performs instant real-time on-device local analysis for spell checking, grammar, and predictions as the user types.
     */
    fun analyzeLocalTyping(
        typedWord: String,
        previousWords: List<String> = emptyList(),
        sentenceContext: String = "",
        tapCoords: List<android.graphics.PointF>? = null
    ): LocalGrammarSpellPredictor.LocalAnalysisResult {
        return localPredictor.analyzeTypingLocally(typedWord, previousWords, sentenceContext, tapCoords)
    }

    /**
     * Performs instant local on-device sentence grammar & spelling cleanup.
     */
    fun polishSentenceLocally(sentence: String): String {
        return localPredictor.polishSentenceLocally(sentence)
    }

    /**
     * Primary Pipeline:
     * Input text
     * → TextContext
     * → deterministic correction
     * → on-device semantic proofreading
     * → confidence/quality evaluation
     * → local result OR Gemini fallback
     * → output validation
     * → apply result
     */
    suspend fun process(
        text: String,
        mode: PolishMode = PolishMode.PROOFREAD,
        context: TextContext = TextContext(mode = mode),
        confidenceThreshold: Float = DEFAULT_LOCAL_CONFIDENCE_THRESHOLD
    ): AiResult = withContext(Dispatchers.Default) {
        val originalText = text.trim()
        if (originalText.isEmpty()) {
            return@withContext AiResult(
                text = text,
                confidence = 1.0f,
                changed = false,
                source = AiSource.ORIGINAL
            )
        }

        val isOfflineEnabled = keyboardSettings.offlineAiEnabled
        val isGeminiEnabled = keyboardSettings.geminiAiEnabled

        if (!isOfflineEnabled && !isGeminiEnabled) {
            return@withContext AiResult(
                text = originalText,
                confidence = 1.0f,
                changed = false,
                source = AiSource.ORIGINAL
            )
        }

        // Requirement: Use proofread using local small llm. Use gemini for changing format.
        if (mode == PolishMode.PROOFREAD || mode == PolishMode.VOICE_CLEANUP || mode == PolishMode.RAMBLE) {
            if (!isOfflineEnabled && isGeminiEnabled) {
                // Escalate to Gemini Cloud if offline engine is disabled
                try {
                    val cloudResponse = GeminiApiClient.generatePolish(originalText, mode, context)
                    if (!cloudResponse.isNullOrBlank()) {
                        val sanitizedCloud = AiOutputValidator.sanitize(cloudResponse, originalText)
                        if (AiOutputValidator.isValid(originalText, sanitizedCloud, mode)) {
                            val hasChanged = sanitizedCloud != originalText
                            return@withContext AiResult(
                                text = sanitizedCloud,
                                confidence = 0.98f,
                                changed = hasChanged,
                                source = if (hasChanged) AiSource.CLOUD else AiSource.ORIGINAL,
                                changes = computeEdits(originalText, sanitizedCloud)
                            )
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Gemini cloud proofreading failed: ${e.message}")
                }
                return@withContext AiResult(
                    text = originalText,
                    confidence = 1.0f,
                    changed = false,
                    source = AiSource.ORIGINAL
                )
            }

            // Local Processing for Proofread / Cleanup
            var locallyCorrected = applyDeterministicCorrections(originalText, mode)
            locallyCorrected = try {
                tfLiteCorrectionModel.correctText(locallyCorrected)
            } catch (e: Exception) {
                locallyCorrected
            }
            
            if (mode == PolishMode.RAMBLE) {
                locallyCorrected = WhisperCppBrain.whisperRambleIntentPolish(locallyCorrected)
            }
            
            val localConfidence = evaluateLocalQuality(originalText, locallyCorrected, mode)
            val sanitized = AiOutputValidator.sanitize(locallyCorrected, originalText)
            val isValid = AiOutputValidator.isValid(originalText, sanitized, mode)
            val finalText = if (isValid) sanitized else originalText
            val hasChanged = finalText != originalText
            
            return@withContext AiResult(
                text = finalText,
                confidence = if (isValid) localConfidence else 0.5f,
                changed = hasChanged,
                source = if (hasChanged) AiSource.LOCAL_MODEL else AiSource.ORIGINAL,
                changes = computeEdits(originalText, finalText)
            )
        } else {
            // Gemini Processing for Format/Style Changes
            try {
                val cloudResponse = GeminiApiClient.generatePolish(originalText, mode, context)
                if (!cloudResponse.isNullOrBlank()) {
                    val sanitizedCloud = AiOutputValidator.sanitize(cloudResponse, originalText)
                    if (AiOutputValidator.isValid(originalText, sanitizedCloud, mode)) {
                        val hasChanged = sanitizedCloud != originalText
                        return@withContext AiResult(
                            text = sanitizedCloud,
                            confidence = 0.98f,
                            changed = hasChanged,
                            source = if (hasChanged) AiSource.CLOUD else AiSource.ORIGINAL,
                            changes = computeEdits(originalText, sanitizedCloud)
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Gemini cloud inference failed: ${e.message}.")
            }
            
            // Fallback to local rules if Gemini fails
            var locallyCorrected = applyDeterministicCorrections(originalText, mode)
            val localResult = applyLocalStyleTransformation(locallyCorrected, mode)
            val sanitized = AiOutputValidator.sanitize(localResult, originalText)
            val isValid = AiOutputValidator.isValid(originalText, sanitized, mode)
            val finalText = if (isValid) sanitized else originalText
            val hasChanged = finalText != originalText
            
            return@withContext AiResult(
                text = finalText,
                confidence = 0.5f,
                changed = hasChanged,
                source = if (hasChanged) AiSource.LOCAL_MODEL else AiSource.ORIGINAL,
                changes = computeEdits(originalText, finalText)
            )
        }
    }

    /**
     * Backward-compatible inference wrapper.
     */
    suspend fun runInference(prompt: String, mode: String): String {
        val polishMode = PolishMode.fromString(mode)
        return process(prompt, polishMode).text
    }

    /**
     * Applies deterministic grammar, spellings, contractions, and Room rules.
     */
    private suspend fun applyDeterministicCorrections(input: String, mode: PolishMode): String {
        var text = input

        // 1. In VOICE_CLEANUP or RAMBLE mode: aggressively remove fillers, stutters, and spoken corrections
        if (mode == PolishMode.VOICE_CLEANUP) {
            text = WhisperCppBrain.whisperCleanAndPolish(text)
        } else if (mode == PolishMode.RAMBLE) {
            text = WhisperCppBrain.whisperRambleIntentPolish(text)
        }

        // 2. Room database active rules (if available)
        try {
            val activeRules = grammarRuleDao.getActiveRulesSync()
            for (rule in activeRules) {
                if (rule.pattern.isNotBlank() && rule.replacement.isNotBlank()) {
                    val regex = Regex("(?i)\\b" + Regex.escape(rule.pattern) + "\\b")
                    text = regex.replace(text, rule.replacement)
                }
            }
        } catch (e: Exception) {
            // Non-fatal if room db is not yet populated
        }

        // 3. Local Grammar & Spell checking engine
        text = localPredictor.polishSentenceLocally(text)

        // 4. Common phonetic typos and homophones
        text = fixCommonTyposAndGrammar(text)

        return text
    }

    /**
     * Evaluates the confidence score of local corrections (0.0 to 1.0).
     */
    private fun evaluateLocalQuality(original: String, corrected: String, mode: PolishMode): Float {
        if (original.isEmpty()) return 1.0f

        val origWords = original.split(Regex("\\s+")).filter { it.isNotBlank() }
        val corrWords = corrected.split(Regex("\\s+")).filter { it.isNotBlank() }
        val dictManager = dictionaryManager

        // Check if original text was already fully valid
        val allOriginalWordsKnown = origWords.all { w ->
            val clean = w.lowercase().replace(Regex("[^a-z']"), "")
            clean.isEmpty() || dictManager.isValidOrKnownWord(clean) || localPredictor.checkGrammarDetailed(clean, emptyList()) == null
        }

        // If text was already clean and unchanged
        if (original == corrected && allOriginalWordsKnown) {
            return 0.95f
        }

        // If local rules made targeted corrections
        if (corrected != original) {
            // If the words in corrected are all recognized dictionary words
            val allCorrectedKnown = corrWords.all { w ->
                val clean = w.lowercase().replace(Regex("[^a-z']"), "")
                clean.isEmpty() || dictManager.isValidOrKnownWord(clean)
            }
            if (allCorrectedKnown) {
                return 0.93f
            }
            return 0.88f
        }

        return 0.85f
    }

    /**
     * Local rule-based transformation when offline for style modes.
     */
    private fun applyLocalStyleTransformation(input: String, mode: PolishMode): String {
        return when (mode) {
            PolishMode.PROFESSIONAL -> {
                var p = input
                val profMap = mapOf(
                    Regex("\\bwant to\\b", RegexOption.IGNORE_CASE) to "would like to",
                    Regex("\\bcan you\\b", RegexOption.IGNORE_CASE) to "could you please",
                    Regex("\\bgive me\\b", RegexOption.IGNORE_CASE) to "please provide",
                    Regex("\\bthanks\\b", RegexOption.IGNORE_CASE) to "thank you",
                    Regex("\\bthx\\b", RegexOption.IGNORE_CASE) to "thank you",
                    Regex("\\bmake sure\\b", RegexOption.IGNORE_CASE) to "ensure",
                    Regex("\\bhelp\\b", RegexOption.IGNORE_CASE) to "assistance",
                    Regex("\\bask\\b", RegexOption.IGNORE_CASE) to "enquire",
                    Regex("\\bbuy\\b", RegexOption.IGNORE_CASE) to "purchase",
                    Regex("\\bget\\b", RegexOption.IGNORE_CASE) to "obtain",
                    Regex("\\bstart\\b", RegexOption.IGNORE_CASE) to "commence",
                    Regex("\\babout\\b", RegexOption.IGNORE_CASE) to "regarding"
                )
                for ((k, v) in profMap) p = p.replace(k, v)
                p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            PolishMode.CASUAL -> {
                var c = input
                val casMap = mapOf(
                    Regex("\\bwould like to\\b", RegexOption.IGNORE_CASE) to "want to",
                    Regex("\\brequire\\b", RegexOption.IGNORE_CASE) to "need",
                    Regex("\\bassistance\\b", RegexOption.IGNORE_CASE) to "help",
                    Regex("\\bregarding\\b", RegexOption.IGNORE_CASE) to "about",
                    Regex("\\bpurchase\\b", RegexOption.IGNORE_CASE) to "get"
                )
                for ((k, v) in casMap) c = c.replace(k, v)
                c
            }
            PolishMode.SHORTEN -> {
                var s = input
                val shortMap = mapOf(
                    Regex("\\bI was wondering if you could please\\b", RegexOption.IGNORE_CASE) to "Could you",
                    Regex("\\bin order to\\b", RegexOption.IGNORE_CASE) to "to",
                    Regex("\\bat the present time\\b", RegexOption.IGNORE_CASE) to "now",
                    Regex("\\bdue to the fact that\\b", RegexOption.IGNORE_CASE) to "because",
                    Regex("\\bplease feel free to\\b", RegexOption.IGNORE_CASE) to "",
                    Regex("\\bjust wanted to\\b", RegexOption.IGNORE_CASE) to ""
                )
                for ((k, v) in shortMap) s = s.replace(k, v)
                s.replace(Regex(" +"), " ").trim()
            }
            PolishMode.EXPAND -> {
                if (!input.startsWith("Please note that", ignoreCase = true)) {
                    "Please note that " + input.replaceFirstChar { it.lowercase() }
                } else input
            }
            else -> input
        }
    }

    private fun fixCommonTyposAndGrammar(input: String): String {
        var text = input
        val fixes = listOf(
            Regex("\\bteh\\b", RegexOption.IGNORE_CASE) to "the",
            Regex("\\brecieve\\b", RegexOption.IGNORE_CASE) to "receive",
            Regex("\\bseperate\\b", RegexOption.IGNORE_CASE) to "separate",
            Regex("\\bdefinately\\b", RegexOption.IGNORE_CASE) to "definitely",
            Regex("\\btommorrow\\b", RegexOption.IGNORE_CASE) to "tomorrow",
            Regex("\\bbeleive\\b", RegexOption.IGNORE_CASE) to "believe",
            Regex("\\boccured\\b", RegexOption.IGNORE_CASE) to "occurred",
            Regex("\\buntill\\b", RegexOption.IGNORE_CASE) to "until",
            Regex("\\btruely\\b", RegexOption.IGNORE_CASE) to "truly",
            Regex("\\bfreind\\b", RegexOption.IGNORE_CASE) to "friend",
            Regex("\\bwierd\\b", RegexOption.IGNORE_CASE) to "weird",
            Regex("\\bbecuase\\b", RegexOption.IGNORE_CASE) to "because",
            Regex("\\btogeather\\b", RegexOption.IGNORE_CASE) to "together",
            Regex("\\bthier\\b", RegexOption.IGNORE_CASE) to "their",
            Regex("\\bshoud\\b", RegexOption.IGNORE_CASE) to "should",
            Regex("\\bwhould\\b", RegexOption.IGNORE_CASE) to "would",
            Regex("\\bcoud\\b", RegexOption.IGNORE_CASE) to "could",
            Regex("\\bI\\s+has\\s+went\\b", RegexOption.IGNORE_CASE) to "I went",
            Regex("\\bI\\s+has\\b", RegexOption.IGNORE_CASE) to "I have",
            Regex("\\bhe\\s+have\\b", RegexOption.IGNORE_CASE) to "he has",
            Regex("\\bshe\\s+have\\b", RegexOption.IGNORE_CASE) to "she has",
            Regex("\\bshe\\s+dont\\b", RegexOption.IGNORE_CASE) to "she doesn't",
            Regex("\\bshe\\s+don't\\b", RegexOption.IGNORE_CASE) to "she doesn't",
            Regex("\\bhe\\s+dont\\b", RegexOption.IGNORE_CASE) to "he doesn't",
            Regex("\\bhe\\s+don't\\b", RegexOption.IGNORE_CASE) to "he doesn't",
            Regex("\\bcould\\s+of\\b", RegexOption.IGNORE_CASE) to "could have",
            Regex("\\bwould\\s+of\\b", RegexOption.IGNORE_CASE) to "would have",
            Regex("\\bshould\\s+of\\b", RegexOption.IGNORE_CASE) to "should have",
            Regex("\\byour\\s+going\\s+to\\b", RegexOption.IGNORE_CASE) to "you're going to",
            Regex("\\btheir\\s+going\\b", RegexOption.IGNORE_CASE) to "they're going"
        )
        for ((pattern, replacement) in fixes) {
            text = text.replace(pattern, replacement)
        }
        return text
    }

    /**
     * Computes word-level diffs between original and modified text.
     */
    private fun computeEdits(original: String, modified: String): List<Edit> {
        if (original == modified) return emptyList()

        val origWords = original.split(" ")
        val modWords = modified.split(" ")

        val edits = mutableListOf<Edit>()
        val minSize = minOf(origWords.size, modWords.size)

        for (i in 0 until minSize) {
            if (origWords[i] != modWords[i]) {
                edits.add(Edit(original = origWords[i], replacement = modWords[i]))
            }
        }
        if (origWords.size > minSize) {
            for (i in minSize until origWords.size) {
                edits.add(Edit(original = origWords[i], replacement = ""))
            }
        } else if (modWords.size > minSize) {
            for (i in minSize until modWords.size) {
                edits.add(Edit(original = "", replacement = modWords[i]))
            }
        }

        return edits
    }
}
