package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TypingBestPracticesTest {
    private lateinit var context: Context
    private lateinit var dictionary: DictionaryManager
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        KeyboardSettings(context).autocorrectEnabled = true
        KeyboardSettings(context).autocorrectSensitivity = KeyboardSettings.SENSITIVITY_BALANCED
        dictionary = DictionaryManager(context).also { kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Default) { it.correctionPipeline.awaitDictionaries() } }
    }
    @Test fun ambiguousNearMatchesStaySuggestionsInBothCommitAndStrip() {
        dictionary.findDictionaryCorrections("cst", 2f, 16) // Warm the shared correction index.
        listOf("cst", "bxt", "mep").forEach { word ->
            assertNull(word, dictionary.gboardEngine.getBestAutocorrectCandidate(word, emptyList(), dictionary))
            assertFalse(word, dictionary.getGboardPredictions(word, emptyList(), null).isCenterAutocorrecting)
        }
    }
    @Test fun acceptingAPersonalCompletionDoesNotMakePrefixesAutomatic() = runBlocking {
        val repository = UserDictionaryRepository.getInstance(context)
        val database = AppDatabase.getDatabase(context)
        database.customDictionaryDao().clearAll()
        repository.addCustomEntry("Zorabel")
        val predictions = PredictiveTextSuggestionService(context, dictionary, repository)
            .fetchSuggestions(TextInputBufferState(typedWord = "Zor", activePrefix = "Zor"))
        assertEquals("Zorabel", predictions.centerCandidate)
        assertFalse(predictions.isCenterAutocorrecting)
        database.customDictionaryDao().clearAll()
    }
    @Test fun learnedWordsAppearWithoutContextAndPredictionsRemainDistinct() {
        val model = NGramLanguageModel()
        model.seedUnigramFrequencies(mapOf("hello" to 100, "world" to 20))
        repeat(5) { model.observeSentence("zorabel") }
        assertEquals("zorabel", model.predictNextWords(emptyList(), "zor", 3).single())
        val results = model.predictNextWords(listOf("unknown"), "", 20)
        assertEquals(results.size, results.distinct().size)
        assertTrue(model.predictNextWords(emptyList(), "", 0).isEmpty())
    }
    @Test fun rareHigherOrderEvidenceDoesNotOverruleReliableContext() {
        val model = NGramLanguageModel()
        model.addBigram("morning", "coffee", 5000)
        model.addQuadgram("after", "my", "morning", "penguin", 1)
        assertEquals("coffee", model.predictNextWords(listOf("after", "my", "morning"), "", 1).single())
        model.addQuadgram("after", "my", "morning", "tea", 10000)
        assertEquals("tea", model.predictNextWords(listOf("after", "my", "morning"), "", 1).single())
    }
    @Test fun rapidTypingCancelsObsoleteDebouncedSuggestions() = runBlocking {
        val predictions = PredictiveTextSuggestionService(context, dictionary)
        val first = TextInputBufferState(typedWord = "old", activePrefix = "old")
        val latest = TextInputBufferState(typedWord = "new", activePrefix = "new")
        val buffer = MutableStateFlow(first)
        val seen = mutableListOf<TextInputBufferState?>()
        val ready = CompletableDeferred<Unit>()
        val job = launch {
            predictions.observeSuggestions(buffer, debounceMs = 150).collect {
                seen.add(it.sourceBuffer); if (it.sourceBuffer == latest) ready.complete(Unit)
            }
        }
        delay(20); buffer.value = latest
        withTimeout(5000) { ready.await() }
        job.cancelAndJoin()
        assertEquals(listOf(latest), seen)
    }
}
