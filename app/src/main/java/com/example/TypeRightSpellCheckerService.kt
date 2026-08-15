package com.example

import android.service.textservice.SpellCheckerService
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.SentenceSuggestionsInfo
import android.util.Log

/**
 * Android System SpellCheckerService implementation providing real-time on-device
 * spell checking and grammatical autocorrection suggestions for Android text fields.
 */
class TypeRightSpellCheckerService : SpellCheckerService() {
    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var localPredictor: LocalGrammarSpellPredictor

    override fun onCreate() {
        super.onCreate()
        dictionaryManager = DictionaryManager(this)
        localPredictor = LocalGrammarSpellPredictor(this)
    }

    override fun createSession(): Session {
        return TypeRightSpellCheckerSession()
    }

    private inner class TypeRightSpellCheckerSession : Session() {
        override fun onCreate() {
            // Initialized
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            if (textInfo == null) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }
            val word = textInfo.text ?: ""
            val cleanWord = word.trim()
            val lowerWord = cleanWord.lowercase()
            
            if (lowerWord.isEmpty() || lowerWord.length < 2) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }

            // Check if word is already valid in dictionary or user learned words
            val inDict = dictionaryManager.isWordInDictionary(lowerWord)
            if (inDict) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }

            // Check if word has a direct local grammar correction
            val grammarFix = localPredictor.checkGrammarLocally(lowerWord, emptyList(), "")
            if (grammarFix != null) {
                val suggestions = arrayOf(grammarFix)
                return SuggestionsInfo(
                    SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS,
                    suggestions
                )
            }

            // Word is a typo, query high-precision multi-signal spelling corrections
            val corrections = dictionaryManager.getSpellingCorrections(lowerWord)
            val limit = suggestionsLimit.coerceIn(1, 5)
            val suggestionsArray = corrections.take(limit).toTypedArray()

            return if (suggestionsArray.isNotEmpty()) {
                SuggestionsInfo(
                    SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS,
                    suggestionsArray
                )
            } else {
                SuggestionsInfo(
                    SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO,
                    emptyArray()
                )
            }
        }

        override fun onGetSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int,
            sequentialWords: Boolean
        ): Array<SuggestionsInfo> {
            if (textInfos == null || textInfos.isEmpty()) {
                return emptyArray()
            }
            val results = ArrayList<SuggestionsInfo>(textInfos.size)
            var prevWord: String? = null
            var prevWord2: String? = null

            for (textInfo in textInfos) {
                val word = textInfo.text ?: ""
                val lowerWord = word.lowercase().trim()
                if (lowerWord.isEmpty() || lowerWord.length < 2 || dictionaryManager.isWordInDictionary(lowerWord)) {
                    results.add(SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray()))
                } else {
                    val contextWords = listOfNotNull(prevWord2, prevWord)
                    val grammarFix = localPredictor.checkGrammarLocally(lowerWord, contextWords, "")
                    val corrections = if (grammarFix != null) {
                        listOf(grammarFix) + dictionaryManager.getSpellingCorrections(lowerWord, prevWord, prevWord2).filter { it.lowercase() != grammarFix.lowercase() }
                    } else {
                        dictionaryManager.getSpellingCorrections(lowerWord, prevWord, prevWord2)
                    }
                    val limit = suggestionsLimit.coerceIn(1, 5)
                    results.add(
                        SuggestionsInfo(
                            SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or (if (corrections.isNotEmpty()) SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS else 0),
                            corrections.take(limit).toTypedArray()
                        )
                    )
                }
                if (sequentialWords) {
                    prevWord2 = prevWord
                    prevWord = lowerWord
                }
            }
            return results.toTypedArray()
        }

        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int
        ): Array<SentenceSuggestionsInfo> {
            if (textInfos == null || textInfos.isEmpty()) {
                return emptyArray()
            }

            val sentenceResults = ArrayList<SentenceSuggestionsInfo>(textInfos.size)

            for (textInfo in textInfos) {
                val fullText = textInfo.text ?: ""
                if (fullText.isBlank()) {
                    sentenceResults.add(SentenceSuggestionsInfo(emptyArray(), IntArray(0), IntArray(0)))
                    continue
                }

                val suggestionsInfoList = mutableListOf<SuggestionsInfo>()
                val offsetList = mutableListOf<Int>()
                val lengthList = mutableListOf<Int>()

                // Tokenize words with offsets
                val wordRegex = Regex("\\b[\\w']+\\b")
                val matches = wordRegex.findAll(fullText).toList()
                val contextWords = mutableListOf<String>()

                for (match in matches) {
                    val word = match.value
                    val lowerWord = word.lowercase().trim()
                    val offset = match.range.first
                    val length = word.length

                    if (lowerWord.length >= 2 && !dictionaryManager.isWordInDictionary(lowerWord)) {
                        val prev1 = contextWords.lastOrNull()
                        val prev2 = if (contextWords.size >= 2) contextWords[contextWords.size - 2] else null

                        val grammarFix = localPredictor.checkGrammarLocally(lowerWord, contextWords, fullText)
                        val corrections = if (grammarFix != null) {
                            listOf(grammarFix) + dictionaryManager.getSpellingCorrections(lowerWord, prev1, prev2).filter { it.lowercase() != grammarFix.lowercase() }
                        } else {
                            dictionaryManager.getSpellingCorrections(lowerWord, prev1, prev2)
                        }

                        val limit = suggestionsLimit.coerceIn(1, 5)
                        val suggestionsArray = corrections.take(limit).toTypedArray()
                        val attr = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or (if (suggestionsArray.isNotEmpty()) SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS else 0)

                        suggestionsInfoList.add(SuggestionsInfo(attr, suggestionsArray))
                        offsetList.add(offset)
                        lengthList.add(length)
                    }

                    contextWords.add(lowerWord)
                }

                sentenceResults.add(
                    SentenceSuggestionsInfo(
                        suggestionsInfoList.toTypedArray(),
                        offsetList.toIntArray(),
                        lengthList.toIntArray()
                    )
                )
            }

            return sentenceResults.toTypedArray()
        }
    }
}
