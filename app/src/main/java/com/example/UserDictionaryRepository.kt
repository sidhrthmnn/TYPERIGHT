package com.example

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository layer managing user custom dictionary entries and frequently used words.
 * Encapsulates Room database access and coordinates bidirectional synchronization with
 * the in-memory DictionaryManager and NGramLanguageModel for zero-latency keystroke predictions.
 */
class UserDictionaryRepository(
    private val frequentlyUsedWordDao: FrequentlyUsedWordDao,
    private val customDictionaryDao: CustomDictionaryDao,
    private val learnedWordDao: LearnedWordDao,
    private val ngramFrequencyDao: NGramFrequencyDao,
    private val blockedSuggestionDao: BlockedSuggestionDao
) {

    // Fast in-memory cache of shortcuts (e.g. "omw" -> "On my way!") for instant buffer matching
    private val shortcutMap = ConcurrentHashMap<String, String>()
    // Fast in-memory cache of custom words
    private val customWordsSet = ConcurrentHashMap.newKeySet<String>()
    // Fast in-memory cache of blocked suggestion words
    private val blockedWordsSet = ConcurrentHashMap.newKeySet<String>()

    /**
     * Reactive stream of blocked suggestions for settings and management UI.
     */
    val allBlockedSuggestionsFlow: Flow<List<BlockedSuggestion>> =
        blockedSuggestionDao.getAllBlockedFlow()

    /**
     * Reactive stream of custom dictionary entries for settings UI.
     */
    val allCustomEntriesFlow: Flow<List<CustomDictionaryEntry>> =
        customDictionaryDao.getAllEntriesFlow()

    /**
     * Reactive stream of top frequently used words for user insight and management.
     */
    fun getFrequentlyUsedWordsFlow(limit: Int = 100): Flow<List<FrequentlyUsedWord>> =
        frequentlyUsedWordDao.getFrequentlyUsedWordsFlow(limit)

    /**
     * Reactive stream of total count of stored N-grams in Room.
     */
    val totalNGramCountFlow: Flow<Int> = ngramFrequencyDao.getTotalCountFlow()

    /**
     * Reactive stream of top N-grams for a given order (e.g. Bigrams or Trigrams).
     */
    fun getTopNGramsFlow(ngramOrder: Int, limit: Int = 20): Flow<List<NGramFrequency>> =
        ngramFrequencyDao.getTopNGramsFlow(ngramOrder, limit)

    /**
     * Fetches offline next-word predictions stored in Room for the given N-gram context.
     */
    suspend fun getOfflineNGramPredictions(
        ngramOrder: Int,
        context: String,
        prefix: String = "",
        limit: Int = 5
    ): List<NGramFrequency> = withContext(Dispatchers.IO) {
        val cleanContext = context.trim().lowercase()
        val cleanPrefix = prefix.trim().lowercase()
        if (cleanPrefix.isNotEmpty()) {
            ngramFrequencyDao.getPredictionsWithPrefix(ngramOrder, cleanContext, cleanPrefix, limit)
        } else {
            ngramFrequencyDao.getPredictions(ngramOrder, cleanContext, limit)
        }
    }

    /**
     * Records an observed N-gram transition in Room for offline prediction persistence.
     */
    suspend fun recordNGram(
        ngramOrder: Int,
        context: String,
        nextWord: String,
        increment: Int = 1
    ) = withContext(Dispatchers.IO) {
        val cleanContext = context.trim().lowercase()
        val cleanWord = nextWord.trim().lowercase()
        if (cleanWord.isEmpty()) return@withContext
        ngramFrequencyDao.recordObservation(
            ngramOrder = ngramOrder,
            context = cleanContext,
            nextWord = cleanWord,
            increment = increment
        )
    }

    /**
     * Seeds initial common conversational N-grams into Room if database is currently empty.
     */
    suspend fun seedInitialNGramsIfEmpty() = withContext(Dispatchers.IO) {
        if (ngramFrequencyDao.getTotalCount() > 0) return@withContext

        val initialList = mutableListOf<NGramFrequency>()

        // Bigrams (Order 2)
        val sampleBigrams = listOf(
            Pair("how", "are") to 1200,
            Pair("are", "you") to 1200,
            Pair("thank", "you") to 1300,
            Pair("you", "so") to 900,
            Pair("you", "very") to 850,
            Pair("good", "morning") to 1100,
            Pair("good", "night") to 1050,
            Pair("good", "afternoon") to 950,
            Pair("great", "job") to 950,
            Pair("great", "idea") to 920,
            Pair("i", "am") to 1200,
            Pair("i", "will") to 1150,
            Pair("i", "have") to 1150,
            Pair("i", "would") to 1050,
            Pair("i", "think") to 1100,
            Pair("i", "know") to 1050,
            Pair("let", "me") to 1150,
            Pair("let", "us") to 900,
            Pair("see", "you") to 1050,
            Pair("talk", "to") to 950,
            Pair("sounds", "good") to 1000,
            Pair("no", "problem") to 1050,
            Pair("of", "course") to 1100
        )
        for ((pair, freq) in sampleBigrams) {
            initialList.add(
                NGramFrequency.create(
                    ngramOrder = 2,
                    context = pair.first,
                    nextWord = pair.second,
                    frequency = freq,
                    probability = freq / 2000f
                )
            )
        }

        // Trigrams (Order 3)
        val sampleTrigrams = listOf(
            Triple("how", "are", "you") to 1200,
            Triple("thank", "you", "so") to 1000,
            Triple("thank", "you", "very") to 950,
            Triple("you", "so", "much") to 980,
            Triple("let", "me", "know") to 1100,
            Triple("looking", "forward", "to") to 1050,
            Triple("have", "a", "great") to 1150,
            Triple("have", "a", "good") to 1100,
            Triple("it", "is", "a") to 950,
            Triple("i", "hope", "you") to 900,
            Triple("hope", "you", "are") to 950,
            Triple("what", "do", "you") to 1000,
            Triple("see", "you", "soon") to 940,
            Triple("talk", "to", "you") to 900
        )
        for ((triple, freq) in sampleTrigrams) {
            initialList.add(
                NGramFrequency.create(
                    ngramOrder = 3,
                    context = "${triple.first} ${triple.second}",
                    nextWord = triple.third,
                    frequency = freq,
                    probability = freq / 1500f
                )
            )
        }

        ngramFrequencyDao.insertAll(initialList)
    }

    /**
     * Loads saved custom entries and shortcuts into memory for instant access.
     */
    suspend fun warmUpCaches(dictionaryManager: DictionaryManager? = null) = withContext(Dispatchers.IO) {
        seedInitialNGramsIfEmpty()
        val entries = customDictionaryDao.getAllEntries()
        shortcutMap.clear()
        customWordsSet.clear()

        for (entry in entries) {
            val normalizedWord = entry.word.trim()
            if (normalizedWord.isNotEmpty()) {
                customWordsSet.add(normalizedWord.lowercase())
                dictionaryManager?.learnWord(normalizedWord, explicit = true)
                dictionaryManager?.nGramModel?.observeSentence(normalizedWord)
            }
            val shortcut = entry.shortcut?.trim()?.lowercase()
            if (!shortcut.isNullOrEmpty()) {
                shortcutMap[shortcut] = normalizedWord
            }
        }

        // Pre-warm blocked suggestion words
        val blocked = blockedSuggestionDao.getAllBlockedWords()
        blockedWordsSet.clear()
        for (b in blocked) {
            val clean = b.trim().lowercase()
            if (clean.isNotEmpty()) {
                blockedWordsSet.add(clean)
            }
        }
        dictionaryManager?.syncBlockedWords(blockedWordsSet)

        // Also pre-warm frequently used words into the N-gram unigram distribution
        val frequentWords = frequentlyUsedWordDao.getTopWords(150)
        for (frequent in frequentWords) {
            if (!blockedWordsSet.contains(frequent.word.trim().lowercase())) {
                dictionaryManager?.learnWord(frequent.word)
            }
        }
    }

    /**
     * Resolves a shortcut to its full expanded word/phrase (e.g. "omw" -> "On my way!").
     */
    fun getShortcutExpansion(shortcut: String): String? {
        val key = shortcut.trim().lowercase()
        return shortcutMap[key]
    }

    /**
     * Returns true if the word is in the user's custom dictionary.
     */
    fun isCustomWord(word: String): Boolean {
        return customWordsSet.contains(word.trim().lowercase())
    }

    /**
     * Records word usage asynchronously: updates Room frequently_used_words,
     * updates learned_words, and informs the dictionary manager.
     */
    suspend fun recordWordUsage(word: String, dictionaryManager: DictionaryManager? = null) = withContext(Dispatchers.IO) {
        val cleanWord = word.trim()
        if (cleanWord.length < 2) return@withContext

        // Increment in frequently_used_words
        val rowsUpdated = frequentlyUsedWordDao.incrementFrequency(cleanWord)
        if (rowsUpdated == 0) {
            frequentlyUsedWordDao.insertOrUpdate(
                FrequentlyUsedWord(
                    word = cleanWord,
                    frequency = 1,
                    count = 1,
                    lastUsedTimestamp = System.currentTimeMillis()
                )
            )
        }

        // Also record in legacy learnedWordDao for backward compatibility
        val existing = learnedWordDao.getWord(cleanWord)
        if (existing != null) {
            learnedWordDao.insertWord(existing.copy(frequency = existing.frequency + 1, timestamp = System.currentTimeMillis()))
        } else {
            learnedWordDao.insertWord(LearnedWord(word = cleanWord, frequency = 1))
        }

        // Update in-memory models
        dictionaryManager?.learnWord(cleanWord)
    }

    /**
     * Adds a custom dictionary entry (with optional shortcut expansion).
     */
    suspend fun addCustomEntry(
        word: String,
        shortcut: String? = null,
        frequency: Int = 200,
        dictionaryManager: DictionaryManager? = null
    ): Long = withContext(Dispatchers.IO) {
        val cleanWord = word.trim()
        val cleanShortcut = shortcut?.trim()?.takeIf { it.isNotEmpty() }

        val entry = CustomDictionaryEntry(
            word = cleanWord,
            shortcut = cleanShortcut,
            frequency = frequency,
            addedTimestamp = System.currentTimeMillis(),
            lastUsedTimestamp = System.currentTimeMillis()
        )
        val id = customDictionaryDao.insertEntry(entry)

        // Update caches
        customWordsSet.add(cleanWord.lowercase())
        if (cleanShortcut != null) {
            shortcutMap[cleanShortcut.lowercase()] = cleanWord
        }

        // Inform dictionary manager & N-Gram model
        dictionaryManager?.learnWord(cleanWord, explicit = true)
        dictionaryManager?.nGramModel?.observeSentence(cleanWord)

        id
    }

    /**
     * Deletes a custom dictionary entry by ID.
     */
    suspend fun deleteCustomEntry(entry: CustomDictionaryEntry) = withContext(Dispatchers.IO) {
        customDictionaryDao.deleteEntry(entry)
        customWordsSet.remove(entry.word.trim().lowercase())
        entry.shortcut?.let { shortcutMap.remove(it.trim().lowercase()) }
    }

    /**
     * Deletes a custom dictionary entry by word.
     */
    suspend fun deleteCustomEntryByWord(word: String) = withContext(Dispatchers.IO) {
        val clean = word.trim()
        val existing = customDictionaryDao.getEntryByWord(clean)
        customDictionaryDao.deleteByWord(clean)
        customWordsSet.remove(clean.lowercase())
        existing?.shortcut?.let { shortcutMap.remove(it.trim().lowercase()) }
    }

    /**
     * Deletes a frequently used word.
     */
    suspend fun deleteFrequentlyUsedWord(word: String) = withContext(Dispatchers.IO) {
        frequentlyUsedWordDao.deleteWord(word.trim())
    }

    /**
     * Clears all frequently used words.
     */
    suspend fun clearFrequentlyUsedWords() = withContext(Dispatchers.IO) {
        frequentlyUsedWordDao.clearAll()
    }

    /**
     * Returns true if the word has been blocked from suggestions by the user.
     */
    fun isBlocked(word: String): Boolean {
        return blockedWordsSet.contains(word.trim().lowercase())
    }

    /**
     * Permanently blocks a word from suggestions.
     * Records the word in Room blocked_suggestions, updates fast in-memory cache,
     * and purges the word from custom entries, frequent words, learned words, and N-gram observations.
     */
    suspend fun blockSuggestion(cleanWord: String, originalWord: String = cleanWord) = withContext(Dispatchers.IO) {
        val clean = cleanWord.trim().lowercase()
        if (clean.isEmpty()) return@withContext
        blockedWordsSet.add(clean)
        val entry = BlockedSuggestion(word = clean, originalWord = originalWord)
        blockedSuggestionDao.insertBlocked(entry)

        // Purge from custom words, frequent words, learned words, and N-gram observations
        customWordsSet.remove(clean)
        customDictionaryDao.deleteByWord(clean)
        frequentlyUsedWordDao.deleteWord(clean)
        learnedWordDao.deleteWord(clean)
        ngramFrequencyDao.deleteWordObservations(clean)
    }

    /**
     * Unblocks a word, allowing it to be suggested again if typed or learned.
     */
    suspend fun unblockSuggestion(cleanWord: String) = withContext(Dispatchers.IO) {
        val clean = cleanWord.trim().lowercase()
        blockedWordsSet.remove(clean)
        blockedSuggestionDao.deleteByWord(clean)
    }

    /**
     * Searches prefix matches across both custom entries and frequently used words,
     * strictly excluding any blocked words.
     */
    suspend fun searchPrefix(prefix: String, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        if (prefix.isBlank()) return@withContext emptyList()
        val customResults = customDictionaryDao.searchPrefix(prefix, limit).map { it.word }
        val frequentResults = frequentlyUsedWordDao.getWordsMatchingPrefix(prefix, limit).map { it.word }
        (customResults + frequentResults)
            .distinct()
            .filter { !isBlocked(it) }
            .take(limit)
    }

    companion object {
        @Volatile
        private var INSTANCE: UserDictionaryRepository? = null

        fun getInstance(context: Context): UserDictionaryRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val repo = UserDictionaryRepository(
                    frequentlyUsedWordDao = db.frequentlyUsedWordDao(),
                    customDictionaryDao = db.customDictionaryDao(),
                    learnedWordDao = db.learnedWordDao(),
                    ngramFrequencyDao = db.ngramFrequencyDao(),
                    blockedSuggestionDao = db.blockedSuggestionDao()
                )
                INSTANCE = repo
                repo
            }
        }
    }
}
