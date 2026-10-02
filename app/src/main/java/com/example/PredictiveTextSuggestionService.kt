package com.example

import android.content.Context
import android.graphics.PointF
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

/**
 * Encapsulated suggestion result containing candidate slot recommendations,
 * next-word predictions, and multi-word phrase completions based on the input buffer.
 */
data class PredictiveTextSuggestions(
    val leftCandidate: String = "",
    val centerCandidate: String = "",
    val rightCandidate: String = "",
    val suggestionsList: List<String> = emptyList(),
    val phraseCompletions: List<String> = emptyList(),
    val shortcutExpansion: String? = null,
    val isCenterAutocorrecting: Boolean = false,
    val sourceBuffer: TextInputBufferState? = null
)

/**
 * Service Layer that integrates the Multi-Order N-gram predictive language model,
 * Room-backed user custom dictionary, and Gboard candidate generation to fetch
 * context-aware text suggestions based on the user's current input buffer.
 */
class PredictiveTextSuggestionService(
    private val context: Context,
    private val dictionaryManager: DictionaryManager = DictionaryManager.getInstance(context),
    private val userDictionaryRepository: UserDictionaryRepository = UserDictionaryRepository.getInstance(context),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val nGramModel: NGramLanguageModel
        get() = dictionaryManager.nGramModel

    /**
     * Synchronously/asynchronously fetches suggestions for the given text input buffer.
     * Evaluates:
     * 1. Sensitive/URL/Email fields (suppresses auto-suggestions for privacy and accuracy)
     * 2. Custom dictionary text expansion shortcuts (e.g. "omw" -> "On my way!")
     * 3. Multi-Order N-gram Language Model (quadgrams, trigrams, bigrams) for next words and phrase completions
     * 4. Gboard SymSpell fuzzy correction + Spatial Key centroid model
     * 5. User custom vocabulary and frequently used words from Room
     */
    suspend fun fetchSuggestions(buffer: TextInputBufferState): PredictiveTextSuggestions = withContext(defaultDispatcher) {
        // Privacy guard: strictly skip suggestion generation in sensitive password fields
        if (buffer.isSensitive) {
            return@withContext PredictiveTextSuggestions(sourceBuffer = buffer)
        }

        val prefix = buffer.activePrefix.trim()
        val contextWords = buffer.previousWords

        // If URL field, return domain-aware completions
        if (buffer.isUrl) {
            val urlSuggestions = dictionaryManager.getSuggestionsForPrefix(
                prefix = prefix,
                isUrlField = true,
                previousWords = contextWords
            )
            val left = urlSuggestions.getOrElse(0) { if (prefix.isNotEmpty()) prefix else "www." }
            val center = urlSuggestions.getOrElse(1) { ".com" }
            val right = urlSuggestions.getOrElse(2) { ".org" }
            val all = (listOf(left, center, right) + urlSuggestions).distinct().take(6)
            return@withContext PredictiveTextSuggestions(
                leftCandidate = left,
                centerCandidate = center,
                rightCandidate = right,
                suggestionsList = all,
                isCenterAutocorrecting = false,
                sourceBuffer = buffer
            )
        }

        // If Email field, return email domain completions
        if (buffer.isEmail) {
            val emailSuggestions = dictionaryManager.getSuggestionsForPrefix(
                prefix = prefix,
                isEmailField = true,
                previousWords = contextWords
            )
            val left = emailSuggestions.getOrElse(0) { if (prefix.isNotEmpty()) prefix else "@gmail.com" }
            val center = emailSuggestions.getOrElse(1) { "@outlook.com" }
            val right = emailSuggestions.getOrElse(2) { "@yahoo.com" }
            val all = (listOf(left, center, right) + emailSuggestions).distinct().take(6)
            return@withContext PredictiveTextSuggestions(
                leftCandidate = left,
                centerCandidate = center,
                rightCandidate = right,
                suggestionsList = all,
                isCenterAutocorrecting = false,
                sourceBuffer = buffer
            )
        }

        // 1. Check for User Custom Dictionary Shortcut Expansion (e.g. "omw" -> "On my way!")
        val shortcutExpansion = if (prefix.isNotEmpty()) {
            userDictionaryRepository.getShortcutExpansion(prefix)
        } else null

        // 2. Fetch Gboard 3-slot candidate generation (Symmetric Deletion + Gaussian Spatial Proximity)
        val gboardResult = dictionaryManager.getGboardPredictions(
            rawTyped = prefix,
            contextWords = contextWords,
            tapCoords = buffer.tapCoords.ifEmpty { null }
        )

        // 3. Multi-Order N-gram Next-Word Suggestions (In-Memory + Room Database)
        val inMemoryNGramPredictions = if (contextWords.isNotEmpty()) {
            nGramModel.predictNextWords(
                contextWords = contextWords,
                prefix = prefix,
                maxResults = 5
            )
        } else if (prefix.isNotEmpty()) {
            nGramModel.predictNextWords(
                contextWords = emptyList(),
                prefix = prefix,
                maxResults = 5
            )
        } else {
            emptyList()
        }

        // Query Room Database for Offline N-gram frequencies
        val roomNGramPredictions = if (contextWords.isNotEmpty()) {
            val order = when {
                contextWords.size >= 2 -> 3 // Trigram
                else -> 2 // Bigram
            }
            val ctxString = when (order) {
                3 -> "${contextWords[contextWords.size - 2]} ${contextWords.last()}"
                else -> contextWords.last()
            }
            userDictionaryRepository.getOfflineNGramPredictions(
                ngramOrder = order,
                context = ctxString,
                prefix = prefix,
                limit = 5
            ).map { it.nextWord }
        } else {
            emptyList()
        }

        // Merge in-memory and Room offline N-gram predictions (prioritizing Room offline learned terms)
        val nGramPredictions = (roomNGramPredictions + inMemoryNGramPredictions)
            .distinct()
            .filter { !dictionaryManager.isBlocked(it) }
            .take(5)

        // 4. Multi-word phrase completions from N-Gram Model (when at word boundary)
        val phraseCompletions = if (prefix.isEmpty() && contextWords.isNotEmpty()) {
            nGramModel.predictNextPhrases(contextWords, maxResults = 3).filter { phrase ->
                phrase.split(" ").none { dictionaryManager.isBlocked(it) }
            }
        } else {
            emptyList()
        }

        // 5. Query user custom dictionary & frequently used words matching prefix
        val customMatches = if (prefix.length >= 2) {
            withContext(ioDispatcher) {
                userDictionaryRepository.searchPrefix(prefix, limit = 3).filter { !dictionaryManager.isBlocked(it) }
            }
        } else {
            emptyList()
        }

        // 6. Assemble and rank candidates for the suggestion strip
        val rawLeft: String
        val rawCenter: String
        val rawRight: String
        val isCenterAutocorrecting: Boolean

        if (shortcutExpansion != null && !dictionaryManager.isBlocked(shortcutExpansion)) {
            // Text expansion shortcut triggered
            rawLeft = if (!dictionaryManager.isBlocked(prefix)) prefix else ""
            rawCenter = shortcutExpansion
            rawRight = nGramPredictions.firstOrNull { it != rawCenter } ?: gboardResult.rightCandidate
            isCenterAutocorrecting = true
        } else {
            rawLeft = gboardResult.leftCandidate.ifEmpty { if (!dictionaryManager.isBlocked(prefix)) prefix else "" }
            
            // If custom word exists matching prefix, elevate it to center candidate
            val topCustom = customMatches.firstOrNull()
            if (topCustom != null && !topCustom.equals(prefix, ignoreCase = true) && !gboardResult.isCenterAutocorrecting) {
                rawCenter = topCustom
                isCenterAutocorrecting = false // A personal prefix completion still requires a tap.
            } else {
                rawCenter = gboardResult.centerCandidate.ifEmpty {
                    nGramPredictions.firstOrNull() ?: if (!dictionaryManager.isBlocked(prefix)) prefix else ""
                }
                isCenterAutocorrecting = gboardResult.isCenterAutocorrecting && !dictionaryManager.isBlocked(rawCenter)
            }

            // Right slot: high-probability next word from N-gram model or Gboard prediction
            rawRight = nGramPredictions.firstOrNull { it != rawCenter && it != rawLeft }
                ?: gboardResult.rightCandidate
        }

        val leftCandidate = if (!dictionaryManager.isBlocked(rawLeft)) rawLeft else ""
        val centerCandidate = if (!dictionaryManager.isBlocked(rawCenter)) rawCenter else (nGramPredictions.firstOrNull { it != leftCandidate } ?: "")
        val rightCandidate = if (!dictionaryManager.isBlocked(rawRight)) rawRight else (nGramPredictions.firstOrNull { it != centerCandidate && it != leftCandidate } ?: "")

        // Aggregate deduplicated suggestions list
        val aggregateList = mutableListOf<String>()
        if (shortcutExpansion != null && !dictionaryManager.isBlocked(shortcutExpansion)) {
            aggregateList.add(shortcutExpansion)
        }
        if (centerCandidate.isNotEmpty() && !dictionaryManager.isBlocked(centerCandidate)) aggregateList.add(centerCandidate)
        if (leftCandidate.isNotEmpty() && !aggregateList.contains(leftCandidate) && !dictionaryManager.isBlocked(leftCandidate)) aggregateList.add(leftCandidate)
        if (rightCandidate.isNotEmpty() && !aggregateList.contains(rightCandidate) && !dictionaryManager.isBlocked(rightCandidate)) aggregateList.add(rightCandidate)
        for (w in customMatches) {
            if (!aggregateList.contains(w) && !dictionaryManager.isBlocked(w)) aggregateList.add(w)
        }
        for (w in nGramPredictions) {
            if (!aggregateList.contains(w) && !dictionaryManager.isBlocked(w)) aggregateList.add(w)
        }

        PredictiveTextSuggestions(
            leftCandidate = leftCandidate,
            centerCandidate = centerCandidate,
            rightCandidate = rightCandidate,
            suggestionsList = aggregateList.filter { !dictionaryManager.isBlocked(it) }.take(6),
            phraseCompletions = phraseCompletions,
            shortcutExpansion = shortcutExpansion?.takeIf { !dictionaryManager.isBlocked(it) },
            isCenterAutocorrecting = isCenterAutocorrecting && !dictionaryManager.isBlocked(centerCandidate),
            sourceBuffer = buffer
        )
    }

    /**
     * Explicitly blocks a word from future suggestions.
     */
    fun blockSuggestion(word: String) {
        dictionaryManager.blockSuggestion(word)
    }

    /**
     * Creates a reactive Flow emitting predictive suggestions debounced for the input buffer.
     */
    fun observeSuggestions(
        bufferFlow: Flow<TextInputBufferState>,
        debounceMs: Long = 20L
    ): Flow<PredictiveTextSuggestions> = channelFlow {
        bufferFlow.distinctUntilChanged().collectLatest { buffer ->
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            try {
                val suggestions = fetchSuggestions(buffer)
                send(suggestions)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("PredictiveTextService", "Suggestion generation error: ${e.message}")
                send(PredictiveTextSuggestions(sourceBuffer = buffer))
            }
        }
    }.flowOn(defaultDispatcher)

    /**
     * Records word selection and commits it to both Room persistence and the N-gram language model.
     * Teaches the on-device model the user's typing style and increases the word's frequency score.
     */
    suspend fun recordWordSelection(
        selectedWord: String,
        previousWords: List<String>
    ) = withContext(ioDispatcher) {
        val word = selectedWord.trim()
        if (word.isEmpty()) return@withContext

        // 1. Record frequency in Room database
        userDictionaryRepository.recordWordUsage(word, dictionaryManager)

        // 2. Train N-Gram Language Model with the new bigram/trigram context
        val context = previousWords.takeLast(3)
        if (context.isNotEmpty()) {
            val sentenceFragment = (context + word).joinToString(" ")
            dictionaryManager.nGramModel.observeSentence(sentenceFragment)

            // Persist Bigram to Room
            val prev1 = context.last().lowercase().trim()
            if (prev1.isNotEmpty()) {
                userDictionaryRepository.recordNGram(ngramOrder = 2, context = prev1, nextWord = word)
            }

            // Persist Trigram to Room if 2 context words exist
            if (context.size >= 2) {
                val prev2 = context[context.size - 2].lowercase().trim()
                val triContext = "$prev2 $prev1"
                userDictionaryRepository.recordNGram(ngramOrder = 3, context = triContext, nextWord = word)
            }
        } else {
            dictionaryManager.nGramModel.observeSentence(word)
        }

        // Persist Unigram to Room
        userDictionaryRepository.recordNGram(ngramOrder = 1, context = "", nextWord = word)
    }

    companion object {
        @Volatile
        private var INSTANCE: PredictiveTextSuggestionService? = null

        fun getInstance(context: Context): PredictiveTextSuggestionService {
            return INSTANCE ?: synchronized(this) {
                val service = PredictiveTextSuggestionService(context.applicationContext)
                INSTANCE = service
                service
            }
        }
    }
}
