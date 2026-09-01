package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.provider.UserDictionary
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Result data class summarizing the outcome of a dictionary synchronization pass.
 */
data class VocabSyncResult(
    val isSuccess: Boolean,
    val trendingWordsAdded: Int,
    val userWordsHarvested: Int,
    val totalWordsActive: Int,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Background Service that periodically updates the local Small Language Model's (SLM)
 * dictionary with popular trending words, contemporary slang, modern tech vocabulary,
 * and user-specific typing & clipboard vocabulary.
 */
class DictionaryUpdateService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): DictionaryUpdateService = this@DictionaryUpdateService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "DictionaryUpdateService onStartCommand received action: ${intent?.action}")
        serviceScope.launch {
            try {
                val result = syncDictionary(applicationContext)
                Log.i(TAG, "Periodic SLM dictionary update completed: ${result.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error in DictionaryUpdateService background execution", e)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "DictionaryUpdateService destroyed")
    }

    companion object {
        private const val TAG = "DictionaryUpdateService"
        const val ACTION_SYNC_DICTIONARY = "com.example.action.SYNC_DICTIONARY"
        const val ACTION_DICTIONARY_UPDATED = "com.example.action.DICTIONARY_UPDATED"

        // ====================================================================
        // POPULAR TRENDING WORDS & CONTEMPORARY VOCABULARY KNOWLEDGE BASE
        // ====================================================================

        val TRENDING_WORDS = listOf(
            // Gen-Z & Contemporary Internet Culture Slang
            "rizz", "skibidi", "sigma", "aura", "delulu", "bussin", "cap", "no cap",
            "bet", "slay", "mid", "yeet", "stan", "sus", "simp", "drip", "based",
            "finna", "glazing", "situationship", "glowup", "ick", "brainrot", "demure",
            "brat", "doomscrolling", "gaslight", "gatekeep", "girlypop", "ragebait",
            "subtweet", "unhinged", "main character", "touch grass", "side eye", "era",
            "lore", "fanum tax", "mewing", "cooked", "let him cook", "mogging", "periodt",
            "lowkey", "highkey", "vibe check", "valid", "cringe", "wholesome", "snack",
            "salty", "ghosting", "breadcrumbing", "orbiting", "soft launch", "hard launch",
            "cheugy", "bop", "opps", "baddie", "yap", "yapping", "yapper", "clout",
            "flex", "fit check", "ratio", "rent free", "caught in 4k", "hits different",
            "understood the assignment", "serving", "mother", "ate and left no crumbs",

            // Modern AI, Tech, Coding & Developer Terminology
            "gemini", "chatgpt", "copilot", "prompting", "prompt", "hallucination",
            "token", "tokenizer", "finetune", "embedding", "rag", "tflite", "whisper",
            "deepfake", "crypto", "web3", "discord", "substack", "threads", "bluesky",
            "airpods", "smartwatch", "docker", "github", "pr", "merge", "commit", "repo",
            "devops", "frontend", "backend", "fullstack", "api", "endpoint", "json",
            "async", "coroutine", "lambda", "compose", "jetpack", "sdk", "apk", "aab",
            "ci/cd", "multimodal", "slm", "llm", "quantization", "lora", "benchmark",
            "inference", "latency", "zero-shot", "few-shot", "pipeline", "agentic", "vector db",

            // Modern Workplace, Gaming & Texting Abbreviations
            "tldr", "fyi", "imo", "imho", "afaik", "btw", "rn", "tbh", "idk", "ngl",
            "fr", "omg", "smh", "irl", "fomo", "jomo", "nsfw", "wfh", "eod", "eta",
            "ootd", "np", "gg", "glhf", "brb", "gtg", "ttyl", "ikr", "hmu", "dm",
            "pm", "amap", "asap", "iykyk", "lmao", "rofl", "wip", "poc", "sync",
            "ping", "huddle", "deck", "standup", "touchbase"
        )

        val TRENDING_BIGRAMS = mapOf(
            "no" to listOf("cap", "problem", "worries", "doubt"),
            "vibe" to listOf("check", "coding", "matcher"),
            "side" to listOf("eye", "hustle", "project"),
            "touch" to listOf("grass", "base"),
            "main" to listOf("character", "branch", "thread"),
            "rent" to listOf("free"),
            "let" to listOf("him cook", "her cook", "them cook", "me know"),
            "fit" to listOf("check"),
            "hits" to listOf("different"),
            "soft" to listOf("launch"),
            "hard" to listOf("launch", "fork"),
            "prompt" to listOf("engineering", "injection", "template"),
            "machine" to listOf("learning"),
            "deep" to listOf("learning", "fake", "dive"),
            "dark" to listOf("mode", "theme"),
            "open" to listOf("source", "access"),
            "ai" to listOf("model", "assistant", "agent", "studio")
        )

        /**
         * Executes the complete SLM dictionary update pipeline:
         * 1. Extracts user-specific vocabulary from local clipboard history & user dictionary
         * 2. Incorporates modern trending internet/tech vocabulary
         * 3. Prunes stale, unused low-frequency entries
         * 4. Updates Room database (`learned_words`)
         * 5. Injects vocabulary into live in-memory Tries and predictive models
         * 6. Updates user settings metrics and dispatches an update broadcast
         */
        suspend fun syncDictionary(context: Context): VocabSyncResult = withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val appContext = context.applicationContext ?: context
            val settings = KeyboardSettings(appContext)
            val db = AppDatabase.getDatabase(appContext)
            val learnedWordDao = db.learnedWordDao()
            val clipboardDao = db.clipboardDao()

            var trendingAddedCount = 0
            var userHarvestedCount = 0

            // 1. Process Trending Words
            val trendingLearnedWords = mutableListOf<LearnedWord>()
            for (word in TRENDING_WORDS) {
                val clean = word.lowercase().trim()
                if (clean.length >= 2) {
                    val existing = learnedWordDao.getWord(clean)
                    if (existing == null) {
                        trendingLearnedWords.add(LearnedWord(word = clean, frequency = 45, timestamp = startTime))
                        trendingAddedCount++
                    } else {
                        // Refresh timestamp & maintain strong weighting
                        trendingLearnedWords.add(existing.copy(frequency = maxOf(existing.frequency, 45), timestamp = startTime))
                    }
                }
            }
            if (trendingLearnedWords.isNotEmpty()) {
                learnedWordDao.insertWords(trendingLearnedWords)
            }

            // 2. Harvest User-Specific Vocabulary from Clipboard History
            val userWordsMap = mutableMapOf<String, Int>()
            try {
                val clipboardItems = clipboardDao.getAllClipsSync()
                for (clip in clipboardItems) {
                    val tokens = clip.text.split(Regex("[^a-zA-Z0-9'_-]+"))
                    for (token in tokens) {
                        val clean = token.lowercase().trim('\'', '"', '.', ',', '!', '?')
                        if (clean.length in 3..25 && clean.any { it.isLetter() } && !clean.all { it.isDigit() }) {
                            userWordsMap[clean] = (userWordsMap[clean] ?: 0) + 1
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error harvesting clipboard vocabulary: ${e.message}")
            }

            // 3. Harvest System User Dictionary if accessible
            try {
                val cursor = appContext.contentResolver.query(
                    UserDictionary.Words.CONTENT_URI,
                    arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY),
                    null, null, null
                )
                cursor?.use {
                    val wordIdx = it.getColumnIndex(UserDictionary.Words.WORD)
                    val freqIdx = it.getColumnIndex(UserDictionary.Words.FREQUENCY)
                    while (it.moveToNext()) {
                        if (wordIdx >= 0) {
                            val w = it.getString(wordIdx)?.lowercase()?.trim()
                            val freq = if (freqIdx >= 0) it.getInt(freqIdx).coerceAtLeast(50) else 50
                            if (!w.isNullOrBlank() && w.length >= 2) {
                                userWordsMap[w] = maxOf(userWordsMap[w] ?: 0, freq)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Provider may not be accessible or empty
            }

            // Insert harvested user vocabulary
            val userLearnedWords = mutableListOf<LearnedWord>()
            for ((w, freq) in userWordsMap) {
                val existing = learnedWordDao.getWord(w)
                val newFreq = if (existing != null) existing.frequency + freq else freq.coerceAtLeast(20)
                userLearnedWords.add(LearnedWord(word = w, frequency = newFreq, timestamp = startTime))
                userHarvestedCount++
            }
            if (userLearnedWords.isNotEmpty()) {
                learnedWordDao.insertWords(userLearnedWords)
            }

            // 4. Prune Stale Entries (Frequency == 1 and not updated in last 30 days)
            val thirtyDaysAgo = startTime - (30L * 24L * 60L * 60L * 1000L)
            try {
                learnedWordDao.pruneStaleWords(maxFrequency = 1, beforeTimestamp = thirtyDaysAgo)
            } catch (e: Exception) {
                Log.w(TAG, "Error pruning stale vocabulary: ${e.message}")
            }

            // 5. Query active database vocabulary and update live memory cache & SLM
            val allWords = learnedWordDao.getAllWords()
            val totalWordsCount = allWords.size

            val dictManager = DictionaryManager(appContext)
            dictManager.bulkInsertTrendingAndUserVocab(
                words = allWords,
                bigrams = TRENDING_BIGRAMS
            )

            // 6. Update Keyboard Settings metadata
            settings.lastVocabSyncTimestamp = startTime
            settings.totalVocabWordsCount = totalWordsCount
            settings.trendingWordsCount = TRENDING_WORDS.size
            settings.userWordsCount = userHarvestedCount
            val statusMsg = "Synced $trendingAddedCount trending terms & $userHarvestedCount personal words"
            settings.lastVocabSyncStatus = statusMsg

            // 7. Dispatch Local Broadcast Notification
            try {
                val broadcastIntent = Intent(ACTION_DICTIONARY_UPDATED).apply {
                    setPackage(appContext.packageName)
                    putExtra("trending_count", trendingAddedCount)
                    putExtra("user_count", userHarvestedCount)
                    putExtra("total_count", totalWordsCount)
                    putExtra("timestamp", startTime)
                }
                appContext.sendBroadcast(broadcastIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send dictionary update broadcast: ${e.message}")
            }

            return@withContext VocabSyncResult(
                isSuccess = true,
                trendingWordsAdded = trendingAddedCount,
                userWordsHarvested = userHarvestedCount,
                totalWordsActive = totalWordsCount,
                message = statusMsg,
                timestamp = startTime
            )
        }
    }
}
