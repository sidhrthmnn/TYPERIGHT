package com.example

import android.content.Context
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

        val shortcut = userDictionaryRepository.getShortcutExpansion(prefix)?.takeUnless(dictionaryManager::isBlocked)
        val ranker = dictionaryManager.correctionPipeline
        ranker.awaitDictionaries()
        val corrections = if (prefix.isNotEmpty()) ranker.rank(prefix, contextWords, buffer.tapCoords, layout = buffer.layout) else null
        val ranked = if (prefix.isEmpty()) ranker.nextWords(contextWords, 6) else {
            val correctionWords = corrections?.candidates.orEmpty().map { it.word }
            val completions = ranker.prefix(prefix, contextWords, buffer.tapCoords, buffer.layout).candidates.map { it.word }
            if (corrections?.tier != ConfidenceTier.LOW) correctionWords + completions else completions + correctionWords
        }.distinctBy(MultilingualLexicon::normalize).filter { !dictionaryManager.isBlocked(it) }.take(6)
        val center = shortcut ?: (corrections?.takeIf { it.tier != ConfidenceTier.LOW }?.suggestion ?: ranked.firstOrNull().orEmpty())
        val left = if (prefix.isNotEmpty() && !center.equals(prefix,true)) prefix else ranked.firstOrNull { !it.equals(center,true) }.orEmpty()
        val right = ranked.firstOrNull { !it.equals(center,true) && !it.equals(left,true) }.orEmpty()
        PredictiveTextSuggestions(left, center, right, (listOf(center,left,right)+ranked).filter(String::isNotBlank).distinct().take(6),
            if(prefix.isEmpty()) ranker.nextPhrases(contextWords) else emptyList(), shortcut, corrections?.automatic != null, buffer)
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
        if(!KeyboardSettings(context).personalizedLearningEnabled || dictionaryManager.resettingLearning) return@withContext
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
