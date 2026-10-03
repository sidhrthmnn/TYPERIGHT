package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the entire shared corpus/index under Android's real app heap limit. */
@RunWith(AndroidJUnit4::class)
class FrequencyDictionaryDeviceTest {
    @Test fun corpusAndCompactIndexWorkOnAndroid() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val corpus = EnglishFrequencyLexicon.get(context)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Default) { corpus.ready.await() }
        assertEquals(46_693, corpus.frequencies.size)
        corpus.ensureCorrectionIndex()
        assertTrue(corpus.corrections("photosynhesis", 2f, 8).any { it.term == "photosynthesis" })
        val dictionary = DictionaryManager(context)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Default) { dictionary.correctionPipeline.awaitDictionaries() }
        assertTrue(dictionary.findWordsWithPrefix("photosyn", 3).contains("photosynthesis"))
        assertFalse(dictionary.isWordInDictionary("helo"))
        assertEquals("hello", dictionary.gboardEngine.getBestAutocorrectCandidate("helo", emptyList(), dictionary))
    }
}
