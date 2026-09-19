package com.example

import android.content.Context
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Google on-device spell check engine using Android's TextServicesManager.
 * Interacts with the device's system spell checker (Google Spell Checker)
 * with thread-safe in-memory caching and offline fallback for ultra-fast,
 * accurate typing autocorrection and proofreading.
 */
class GoogleDeviceSpellChecker private constructor(private val context: Context) : SpellCheckerSessionListener {

    private var spellCheckerSession: SpellCheckerSession? = null
    private val suggestionsCache = ConcurrentHashMap<String, List<String>>()
    private val pendingQueries = ConcurrentHashMap<String, Long>()

    init {
        initSession()
    }

    @Synchronized
    private fun initSession() {
        try {
            val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            if (tsm != null) {
                spellCheckerSession = tsm.newSpellCheckerSession(null, Locale.getDefault(), this, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize TextServicesManager: ${e.message}")
        }
    }

    /**
     * Synchronously returns cached suggestions if available, or enqueues the query to the
     * Android on-device spell checker session for future immediate hits.
     */
    fun getSpellCheckSuggestions(word: String): List<String> {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.length < 2) return emptyList()

        suggestionsCache[clean]?.let { return it }

        querySession(clean)
        return emptyList()
    }

    private fun querySession(word: String) {
        try {
            if (spellCheckerSession == null) {
                initSession()
            }
            val session = spellCheckerSession ?: return
            pendingQueries[word] = System.currentTimeMillis()
            session.getSuggestions(TextInfo(word), 5)
        } catch (e: Exception) {
            Log.w(TAG, "Spell check query failed: ${e.message}")
        }
    }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
        if (results == null) return
        for (info in results) {
            val count = info.suggestionsCount
            if (count <= 0) continue
            val suggestions = mutableListOf<String>()
            for (i in 0 until count) {
                val s = info.getSuggestionAt(i)
                if (!s.isNullOrBlank()) {
                    suggestions.add(s)
                }
            }
            if (suggestions.isNotEmpty()) {
                val pendingKey = pendingQueries.keys.firstOrNull()
                if (pendingKey != null) {
                    suggestionsCache[pendingKey] = suggestions
                    pendingQueries.remove(pendingKey)
                }
            }
        }
    }

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        if (results == null) return
        for (sentenceInfo in results) {
            val count = sentenceInfo.suggestionsCount
            for (i in 0 until count) {
                val info = sentenceInfo.getSuggestionsInfoAt(i)
                val suggestions = mutableListOf<String>()
                for (j in 0 until info.suggestionsCount) {
                    val s = info.getSuggestionAt(j)
                    if (!s.isNullOrBlank()) {
                        suggestions.add(s)
                    }
                }
            }
        }
    }

    /**
     * Performs ultra-fast proofread of sentence tokens using cached device spell check
     * and local rule-based corrections.
     */
    fun proofreadSentenceFast(sentence: String): String {
        if (sentence.isBlank()) return sentence
        val words = sentence.split(Regex("(?<=\\s)|(?=\\s)"))
        val sb = StringBuilder()
        for (token in words) {
            val trimmed = token.trim()
            if (trimmed.isEmpty() || !trimmed.all { it.isLetter() || it == '\'' }) {
                sb.append(token)
                continue
            }
            val lower = trimmed.lowercase(Locale.ROOT)
            val cached = suggestionsCache[lower]
            if (!cached.isNullOrEmpty()) {
                val correction = TypingPolicy.restoreCase(trimmed, cached.first())
                sb.append(correction)
            } else {
                val direct = TypingPolicy.correction(trimmed)
                if (direct != null) {
                    sb.append(direct)
                } else {
                    sb.append(token)
                }
                querySession(lower)
            }
        }
        return sb.toString()
    }

    fun close() {
        try {
            spellCheckerSession?.close()
        } catch (_: Exception) {}
        spellCheckerSession = null
    }

    companion object {
        private const val TAG = "GoogleDeviceSpellChecker"

        @Volatile
        private var instance: GoogleDeviceSpellChecker? = null

        fun getInstance(context: Context): GoogleDeviceSpellChecker {
            return instance ?: synchronized(this) {
                instance ?: GoogleDeviceSpellChecker(context.applicationContext).also { instance = it }
            }
        }
    }
}
