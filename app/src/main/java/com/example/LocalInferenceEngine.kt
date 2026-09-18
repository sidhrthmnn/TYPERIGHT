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
    val onDeviceProofreader = OnDeviceProofreadEngine.getInstance(context)
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
        val isNemotronEnabled = keyboardSettings.nemotronAiEnabled

        if (!isOfflineEnabled && !isGeminiEnabled && !isNemotronEnabled) {
            return@withContext AiResult(
                text = originalText,
                confidence = 1.0f,
                changed = false,
                source = AiSource.ORIGINAL
            )
        }

        // Requirement: Use proofread using local small llm. Use cloud for changing format.
        if (mode == PolishMode.PROOFREAD || mode == PolishMode.VOICE_CLEANUP || mode == PolishMode.RAMBLE) {
            if (!isOfflineEnabled && (isGeminiEnabled || isNemotronEnabled)) {
                // Try Nemotron first, then escalate to Gemini Cloud if offline engine is disabled
                var cloudResponse: String? = null
                var usedNemotron = false
                
                // Try Nemotron first
                if (isNemotronEnabled) {
                    try {
                        cloudResponse = NemotronApiClient.generatePolish(originalText, mode, context)
                        if (!cloudResponse.isNullOrBlank()) {
                            usedNemotron = true
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w(TAG, "Nemotron cloud proofreading failed: ${e.message}")
                    }
                }
                
                // Fallback to Gemini if Nemotron failed or not enabled
                if (cloudResponse.isNullOrBlank() && isGeminiEnabled) {
                    try {
                        cloudResponse = GeminiApiClient.generatePolish(originalText, mode, context)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w(TAG, "Gemini cloud proofreading failed: ${e.message}")
                    }
                }
                
                if (!cloudResponse.isNullOrBlank()) {
                    val sanitizedCloud = AiOutputValidator.sanitize(cloudResponse, originalText)
                    if (AiOutputValidator.isValid(originalText, sanitizedCloud, mode)) {
                        val hasChanged = sanitizedCloud != originalText
                        return@withContext AiResult(
                            text = sanitizedCloud,
                            confidence = 0.98f,
                            changed = hasChanged,
                            source = if (hasChanged) (if (usedNemotron) AiSource.NEON else AiSource.CLOUD) else AiSource.ORIGINAL,
                            changes = computeEdits(originalText, sanitizedCloud)
                        )
                    }
                }
                return@withContext AiResult(
                    text = originalText,
                    confidence = 1.0f,
                    changed = false,
                    source = AiSource.ORIGINAL
                )
            }

            // Local Processing for Proofread / Cleanup
            var locallyCorrected = onDeviceProofreader.proofread(originalText)
            locallyCorrected = applyDeterministicCorrections(locallyCorrected, mode)
            val beforeTfLite = locallyCorrected
            locallyCorrected = try {
                tfLiteCorrectionModel.correctText(locallyCorrected)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                locallyCorrected
            }
            val tfLiteContributed = locallyCorrected != beforeTfLite
            
            if (mode == PolishMode.RAMBLE) {
                locallyCorrected = WhisperCppBrain.whisperRambleIntentPolish(locallyCorrected)
            }
            
            val localConfidence = evaluateLocalQuality(originalText, locallyCorrected, mode)
            val sanitized = AiOutputValidator.sanitize(locallyCorrected, originalText)
            val isValid = AiOutputValidator.isValid(originalText, sanitized, mode)
            val finalText = if (isValid) sanitized else originalText
            val hasChanged = finalText != originalText
            val source = if (!hasChanged) {
                AiSource.ORIGINAL
            } else if (tfLiteContributed) {
                AiSource.LOCAL_MODEL
            } else {
                AiSource.LOCAL_RULES
            }
            
            return@withContext AiResult(
                text = finalText,
                confidence = if (isValid) localConfidence else 0.5f,
                changed = hasChanged,
                source = source,
                changes = computeEdits(originalText, finalText)
            )
        } else {
            // Cloud Processing for Format/Style Changes - Try Nemotron first, then Gemini
            var cloudResponse: String? = null
            var usedNemotron = false
            
            // Try Nemotron first if enabled
            if (isNemotronEnabled) {
                try {
                    cloudResponse = NemotronApiClient.generatePolish(originalText, mode, context)
                    if (!cloudResponse.isNullOrBlank()) {
                        usedNemotron = true
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "Nemotron cloud inference failed: ${e.message}.")
                }
            }
            
            // Fallback to Gemini if Nemotron failed or not enabled
            if (cloudResponse.isNullOrBlank() && isGeminiEnabled) {
                try {
                    cloudResponse = GeminiApiClient.generatePolish(originalText, mode, context)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "Gemini cloud inference failed: ${e.message}.")
                }
            }
            
            if (!cloudResponse.isNullOrBlank()) {
                val sanitizedCloud = AiOutputValidator.sanitize(cloudResponse, originalText)
                if (AiOutputValidator.isValid(originalText, sanitizedCloud, mode)) {
                    val hasChanged = sanitizedCloud != originalText
                    return@withContext AiResult(
                        text = sanitizedCloud,
                        confidence = 0.98f,
                        changed = hasChanged,
                        source = if (hasChanged) (if (usedNemotron) AiSource.NEON else AiSource.CLOUD) else AiSource.ORIGINAL,
                        changes = computeEdits(originalText, sanitizedCloud)
                    )
                }
            }
            
            // Fallback to local only if offline AI is enabled
            if (!isOfflineEnabled) {
                return@withContext AiResult(
                    text = originalText,
                    confidence = 1.0f,
                    changed = false,
                    source = AiSource.ORIGINAL,
                    changes = emptyList()
                )
            }

            var baseCorrected = onDeviceProofreader.proofread(originalText)
            baseCorrected = applyDeterministicCorrections(baseCorrected, mode)
            val beforeTfLite = baseCorrected
            baseCorrected = try {
                tfLiteCorrectionModel.correctText(baseCorrected)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                baseCorrected
            }
            val tfLiteContributed = baseCorrected != beforeTfLite

            val localResult = applyLocalStyleTransformation(baseCorrected, mode)
            val sanitized = AiOutputValidator.sanitize(localResult, originalText)
            val isValid = AiOutputValidator.isValid(originalText, sanitized, mode)
            val finalText = if (isValid) sanitized else originalText
            val hasChanged = finalText != originalText
            val localConfidence = evaluateLocalQuality(originalText, finalText, mode)
            val source = if (!hasChanged) {
                AiSource.ORIGINAL
            } else if (tfLiteContributed) {
                AiSource.LOCAL_MODEL
            } else {
                AiSource.LOCAL_RULES
            }

            return@withContext AiResult(
                text = finalText,
                confidence = if (isValid) localConfidence else 0.88f,
                changed = hasChanged,
                source = source,
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
     * Local neural rule-based transformation when offline for style modes.
     */
    private fun applyLocalStyleTransformation(input: String, mode: PolishMode): String {
        val tone = when (mode) {
            PolishMode.POLISH -> "Eloquent"
            PolishMode.REPHRASE -> "Formal"
            PolishMode.PROFESSIONAL -> "Formal"
            PolishMode.CASUAL -> "Casual"
            PolishMode.SHORTEN -> "Concise"
            PolishMode.EXPAND -> "Formal"
            PolishMode.VOICE_CLEANUP -> "Voice"
            else -> "Proofread"
        }
        return OnDeviceNeuralPolishEngine.getInstance(context).polish(input, tone).polishedText
    }

    private fun fixCommonTyposAndGrammar(input: String): String {
        return OnDeviceNeuralPolishEngine.getInstance(context).quickProofread(input)
    }

    /**
     * Computes word-level diffs between original and modified text using an
     * insertion/deletion-aware dynamic programming sequence alignment algorithm.
     */
    private fun computeEdits(original: String, modified: String): List<Edit> {
        if (original == modified) return emptyList()

        val origWords = original.split(" ").filter { it.isNotEmpty() }
        val modWords = modified.split(" ").filter { it.isNotEmpty() }

        val n = origWords.size
        val m = modWords.size

        if (n == 0) {
            return modWords.map { Edit(original = "", replacement = it) }
        }
        if (m == 0) {
            return origWords.map { Edit(original = it, replacement = "") }
        }

        // Longest Common Subsequence (LCS) matrix
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) {
            for (j in 1..m) {
                dp[i][j] = if (origWords[i - 1] == modWords[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Backtrack to assemble edits
        val reversedEdits = mutableListOf<Edit>()
        var i = n
        var j = m

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && origWords[i - 1] == modWords[j - 1]) {
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i - 1][j - 1] >= dp[i - 1][j] && dp[i - 1][j - 1] >= dp[i][j - 1]) {
                reversedEdits.add(Edit(original = origWords[i - 1], replacement = modWords[j - 1]))
                i--
                j--
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                reversedEdits.add(Edit(original = "", replacement = modWords[j - 1]))
                j--
            } else if (i > 0 && (j == 0 || dp[i - 1][j] >= dp[i][j - 1])) {
                reversedEdits.add(Edit(original = origWords[i - 1], replacement = ""))
                i--
            } else {
                break
            }
        }

        return reversedEdits.reversed()
    }
}
