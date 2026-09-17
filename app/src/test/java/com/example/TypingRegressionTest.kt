package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TypingRegressionTest {
    private lateinit var dictionary: DictionaryManager
    private lateinit var settings: KeyboardSettings

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("typeright_dictionary", Context.MODE_PRIVATE).edit().clear().commit()
        settings = KeyboardSettings(context)
        settings.autocorrectEnabled = true
        dictionary = DictionaryManager(context)
    }

    private fun predict(word: String, context: List<String> = emptyList()) =
        dictionary.getGboardPredictions(word, context, null)

    @Test fun clearTyposCorrectAndKeepLiteralAvailable() {
        mapOf("teh" to "the", "adn" to "and", "helo" to "hello", "thid" to "this",
            "recieve" to "receive", "definately" to "definitely", "tommorow" to "tomorrow",
            "dont" to "don't", "im" to "I'm").forEach { (typed, expected) ->
            val result = predict(typed)
            assertTrue("$typed: $result", result.isCenterAutocorrecting)
            assertEquals(expected, result.centerCandidate)
            assertEquals(typed, result.leftCandidate)
        }
    }

    @Test fun ambiguousRealWordsAreNotExpandedIntoContractions() {
        listOf("were", "well", "ill", "its", "lets", "wed", "id", "your", "their", "fir", "hello").forEach {
            assertFalse("Must preserve $it", predict(it).isCenterAutocorrecting)
            assertNull(TypingPolicy.correction(it))
        }
    }

    @Test fun partialWordsAreSuggestionsNotAutomaticReplacements() {
        listOf("hel", "th", "tha", "go", "ple").forEach { assertFalse(it, predict(it).isCenterAutocorrecting) }
    }

    @Test fun explicitCorrectionRejectionTakesEffectImmediately() {
        assertTrue(predict("teh").isCenterAutocorrecting)
        dictionary.suppressCorrection("teh", "the")
        assertFalse(predict("teh").isCenterAutocorrecting)
        assertNull(dictionary.gboardEngine.immediateCorrection("teh", dictionary))
    }

    @Test fun disablingAutocorrectInvalidatesPreviousDecision() {
        assertTrue(predict("teh").isCenterAutocorrecting)
        settings.autocorrectEnabled = false
        assertFalse(predict("teh").isCenterAutocorrecting)
        assertNull(dictionary.gboardEngine.immediateCorrection("teh", dictionary))
    }

    @Test fun changingCaseDoesNotReuseAnOldCachedResult() {
        assertEquals("the", predict("teh").centerCandidate)
        assertEquals("The", predict("Teh").centerCandidate)
        assertEquals("THE", predict("TEH").centerCandidate)
    }

    @Test fun namesCodeNumbersAndLongTokensArePreserved() {
        listOf("Siddharth", "myVariable", "JSON", "utf8", "https://example.com", "a@b.com", "3.14", "x".repeat(200)).forEach {
            assertFalse(it, predict(it).isCenterAutocorrecting)
        }
    }

    @Test fun sensitiveInputsHaveNoCandidatesOrTelemetry() {
        val result = dictionary.getGboardPredictions("secret", listOf("private"), null, true)
        assertEquals(GboardSuggestionResult("", "", "", false), result)
        settings.keyboardLanguage = "Malayalam"
        assertTrue(dictionary.getSuggestionsForPrefix("secret", isSensitiveField = true).isEmpty())
    }

    @Test fun strongestNextWordOccupiesCenterAndSlotsAreDistinct() {
        val result = predict("", listOf("how", "are"))
        assertEquals("you", result.centerCandidate)
        val slots = listOf(result.leftCandidate, result.centerCandidate, result.rightCandidate)
        assertEquals(3, slots.distinct().size)
        assertTrue(slots.all { it.isNotBlank() && !it.contains(' ') })
        assertFalse(result.isCenterAutocorrecting)
    }

    @Test fun decoderUsesTheLivePersonalizedLanguageModel() {
        dictionary.nGramModel.addTrigram("my", "project", "typeright", 10000)
        assertEquals("typeright", predict("", listOf("my", "project")).centerCandidate)
    }

    @Test fun probabilityFloorDoesNotEraseContextRanking() {
        val model = NGramLanguageModel()
        assertTrue(model.getProbability("you", listOf("how", "are")) > model.getProbability("zqx", listOf("how", "are")))
        assertTrue(model.getProbability("zqx", emptyList()) < 0.01f)
    }

    @Test fun doubleSpaceRequiresEnabledSettingAndRecentWordBoundary() {
        assertTrue(TypingPolicy.shouldInsertPeriod("o ", true, 200))
        assertFalse(TypingPolicy.shouldInsertPeriod("o ", false, 200))
        assertFalse(TypingPolicy.shouldInsertPeriod("o ", true, 1500))
        assertFalse(TypingPolicy.shouldInsertPeriod(" ", true, 200))
        assertFalse(TypingPolicy.shouldInsertPeriod(". ", true, 200))
        assertFalse(TypingPolicy.shouldInsertPeriod("  ", true, 200))
    }

    @Test fun backspaceRemovesWholeGraphemes() {
        listOf("a", "😀", "👍🏽", "🇮🇳", "👨‍👩‍👧‍👦", "e\u0301").forEach { char ->
            assertEquals(char, char.length, TypingPolicy.lastCharacterLength("prefix$char"))
        }
    }

    @Test fun contractionsAndCombiningMarksStayInTheComposingWord() {
        assertTrue(TypingPolicy.isWordCharacter('\''))
        assertTrue(TypingPolicy.isWordCharacter('’'))
        assertTrue(TypingPolicy.isWordCharacter('\u0301'))
        assertFalse(TypingPolicy.isWordCharacter('.'))
    }

    @Test fun trieReturnsStableTopKIncludingFrequencyUpdates() {
        val trie = WordTrie()
        trie.insert("hello", 30); trie.insert("help", 20); trie.insert("held", 10)
        assertEquals(listOf("hello", "help"), trie.findByPrefix("he", 2))
        trie.insert("held", 50)
        assertEquals(listOf("held", "hello"), trie.findByPrefix("he", 2))
        assertTrue(trie.findByPrefix("he", 0).isEmpty())
    }

    @Test fun proofreadingCannotInventNumbersOrTextFromNothing() {
        assertFalse(AiOutputValidator.isValid("send it", "Send 42 items.", PolishMode.PROOFREAD))
        assertFalse(AiOutputValidator.isValid("", "Invented text.", PolishMode.PROOFREAD))
        assertEquals("\"", AiOutputValidator.sanitize("\""))
    }
}
