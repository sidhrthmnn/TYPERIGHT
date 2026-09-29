package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FrequencyDictionaryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun corpusProvidesBroadVocabularyAndFrequencyOrder() {
        val corpus = EnglishFrequencyLexicon.get(context)
        assertTrue(corpus.frequencies.size > 40_000)
        assertSame(corpus, EnglishFrequencyLexicon.get(context))
        assertTrue(corpus.frequency("you") > corpus.frequency("archaeology"))
        assertTrue(corpus.prefix("photosyn", 3).contains("photosynthesis"))
        val ranked = corpus.prefix("c", 10)
        assertEquals(ranked, ranked.sortedWith(compareByDescending<String> { corpus.frequency(it) }.thenBy { it }))
    }

    @Test fun newVocabularyAppearsInKeyboardCompletionsAndIsPreservedOnCommit() {
        val dictionary = DictionaryManager(context)
        assertTrue(dictionary.isWordInDictionary("photosynthesis"))
        assertTrue(dictionary.findWordsWithPrefix("photosyn", 3).contains("photosynthesis"))
        val prediction = dictionary.getGboardPredictions("photosynthesis", emptyList(), null)
        assertFalse(prediction.isCenterAutocorrecting)
        assertEquals("photosynthesis", prediction.centerCandidate)
        assertEquals(GboardSuggestionResult("", "", "", false),
            dictionary.getGboardPredictions("photosyn", emptyList(), null, true))
        assertFalse(dictionary.isWordInDictionary("helo"))
        assertFalse(dictionary.findWordsWithPrefix("helo", 20).contains("helo"))
    }

    @Test fun prefixDeleteIndexFindsLongWordTyposAndTranspositions() {
        val corpus = EnglishFrequencyLexicon.get(context)
        val index = corpus.ensureCorrectionIndex()
        assertTrue(index.lookup("crocodlie", 2f, 8).any { it.term == "crocodile" })
        assertTrue(index.lookup("photosynhesis", 2f, 8).any { it.term == "photosynthesis" })
    }

    @Test fun corpusBackoffFillsUnknownContextWithoutOverridingLearnedContext() {
        val model = NGramLanguageModel()
        model.seedUnigramFrequencies(mapOf("hello" to 20, "world" to 5))
        assertEquals("hello", model.predictNextWords(listOf("unseen"), "", 1).single())
        model.addTrigram("my", "project", "typeright", 10000)
        assertEquals("typeright", model.predictNextWords(listOf("my", "project"), "", 1).single())
    }
}
