package com.example

import android.service.textservice.SpellCheckerService
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.SentenceSuggestionsInfo
import java.util.Locale

/** System spell checking shares the keyboard decoder and never learns submitted text. */
class TypeRightSpellCheckerService : SpellCheckerService() {
    private lateinit var dictionary: DictionaryManager

    override fun onCreate() {
        super.onCreate()
        dictionary = DictionaryManager(this)
    }

    override fun createSession(): Session = TypeRightSession()

    private fun suggest(info: TextInfo, limit: Int, context: List<String>): SuggestionsInfo {
        val word = info.text.orEmpty().trim()
        val lower = word.lowercase(Locale.ROOT)
        val known = word.isEmpty() || word.length > 32 || dictionary.isCodeOrSpecialToken(word) ||
            dictionary.isWordInDictionary(lower) || word.all(Char::isDigit)
        val result = if (known) {
            SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
        } else if (limit <= 0) {
            SuggestionsInfo(0, emptyArray())
        } else {
            val decoded = dictionary.getGboardPredictions(word, context.takeLast(3), null)
            val candidates = (listOf(decoded.centerCandidate, decoded.rightCandidate, decoded.leftCandidate) +
                decoded.debugTelemetry?.topCandidates.orEmpty().map { it.word })
                .filter { it.isNotBlank() && !it.equals(word, true) }
                .distinctBy { it.lowercase(Locale.ROOT) }.take(limit.coerceAtMost(5))
            val flags = if (candidates.isEmpty()) 0 else SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or
                SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS
            SuggestionsInfo(flags, candidates.toTypedArray())
        }
        result.setCookieAndSequence(info.cookie, info.sequence)
        return result
    }

    private inner class TypeRightSession : Session() {
        override fun onCreate() = Unit

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo =
            textInfo?.let { suggest(it, suggestionsLimit, emptyList()) } ?: SuggestionsInfo(0, emptyArray())

        override fun onGetSuggestionsMultiple(
            textInfos: Array<out TextInfo>?, suggestionsLimit: Int, sequentialWords: Boolean
        ): Array<SuggestionsInfo> {
            val context = ArrayDeque<String>()
            return textInfos.orEmpty().map { info ->
                suggest(info, suggestionsLimit, context.toList()).also {
                    if (sequentialWords) {
                        context.addLast(info.text.orEmpty())
                        if (context.size > 3) context.removeFirst()
                    }
                }
            }.toTypedArray()
        }

        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>?, suggestionsLimit: Int
        ): Array<SentenceSuggestionsInfo> = textInfos.orEmpty().map { info ->
            val results = mutableListOf<SuggestionsInfo>()
            val offsets = mutableListOf<Int>()
            val lengths = mutableListOf<Int>()
            val context = ArrayDeque<String>()
            val text = info.text.orEmpty()
            var previousEnd = 0
            // Unicode letters/marks retain UTF-16 offsets expected by Android editors.
            for (match in Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}]+)*").findAll(text)) {
                if (text.substring(previousEnd, match.range.first).any { it in ".!?\n" }) context.clear()
                val result = suggest(TextInfo(match.value, info.cookie, info.sequence), suggestionsLimit, context.toList())
                if (result.suggestionsCount > 0) {
                    results.add(result)
                    offsets.add(match.range.first)
                    lengths.add(match.value.length)
                }
                context.addLast(match.value)
                if (context.size > 3) context.removeFirst()
                previousEnd = match.range.last + 1
            }
            SentenceSuggestionsInfo(results.toTypedArray(), offsets.toIntArray(), lengths.toIntArray())
        }.toTypedArray()
    }
}
