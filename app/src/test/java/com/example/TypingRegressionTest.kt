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

    @Test fun missedSpacePhrasesAreSegmentedAndCorrected() {
        listOf(
            "thankyou" to "thank you",
            "goodmorning" to "good morning",
            "alot" to "a lot",
            "infront" to "in front",
            "atleast" to "at least",
            "ofcourse" to "of course"
        ).forEach { (typed, expected) ->
            val result = predict(typed)
            assertTrue("Expected autocorrect for $typed", result.isCenterAutocorrecting)
            assertEquals("Expected $expected for $typed", expected, result.centerCandidate)
            val commitCandidate = dictionary.gboardEngine.getBestAutocorrectCandidate(typed, emptyList(), dictionary)
            assertEquals("Expected commit candidate $expected for $typed", expected, commitCandidate)
        }
    }

    @Test fun longWordsAndSingleWordTyposAreNeverSplitIntoSeparateWords() {
        listOf(
            "understanding",
            "congratulations",
            "supermarket",
            "relationship",
            "database",
            "international",
            "workds",
            "extracurricular",
            "responsibility",
            "development",
            "environmental",
            "transportation"
        ).forEach { word ->
            val result = predict(word)
            assertFalse("Word '$word' must NEVER be split with a space into multiple words: ${result.centerCandidate}",
                result.centerCandidate.contains(" "))
            val commitCandidate = dictionary.gboardEngine.getBestAutocorrectCandidate(word, emptyList(), dictionary)
            if (commitCandidate != null) {
                assertFalse("Commit candidate for '$word' must NEVER contain a space: $commitCandidate",
                    commitCandidate.contains(" "))
            }
        }
    }

    @Test fun expandedContractionsAreRestored() {
        listOf(
            "shouldve" to "should've",
            "couldve" to "could've",
            "wouldve" to "would've",
            "theyre" to "they're",
            "youre" to "you're",
            "cmon" to "c'mon",
            "yall" to "y'all"
        ).forEach { (typed, expected) ->
            val result = predict(typed)
            assertTrue("Expected contraction autocorrect for $typed", result.isCenterAutocorrecting)
            assertEquals("Expected $expected for $typed", expected, result.centerCandidate)
        }
    }

    @Test fun contextualAmbiguityIsResolvedWithPriorWord() {
        val withContext = predict("ill", listOf("I"))
        assertTrue("Expected contextual autocorrect for 'ill' after 'I'", withContext.isCenterAutocorrecting)
        assertEquals("I'll", withContext.centerCandidate)

        val withoutContext = predict("ill", emptyList())
        assertFalse("Must not autocorrect 'ill' without context", withoutContext.isCenterAutocorrecting)

        val weWell = predict("well", listOf("we"))
        assertTrue("Expected contextual autocorrect for 'well' after 'we'", weWell.isCenterAutocorrecting)
        assertEquals("we'll", weWell.centerCandidate)
    }

    @Test fun bestAutocorrectCandidateCorrectsOnSpaceEvenWithPrefixCompletions() {
        val candidate = dictionary.gboardEngine.getBestAutocorrectCandidate("woudl", emptyList(), dictionary)
        assertEquals("would", candidate)

        val recieveCandidate = dictionary.gboardEngine.getBestAutocorrectCandidate("recieve", emptyList(), dictionary)
        assertEquals("receive", recieveCandidate)
    }

    @Test fun autocorrectSensitivitySettingsAffectGating() {
        settings.autocorrectSensitivity = KeyboardSettings.SENSITIVITY_AGGRESSIVE
        assertEquals(KeyboardSettings.SENSITIVITY_AGGRESSIVE, settings.autocorrectSensitivity)

        settings.autocorrectSensitivity = KeyboardSettings.SENSITIVITY_MILD
        assertEquals(KeyboardSettings.SENSITIVITY_MILD, settings.autocorrectSensitivity)

        settings.autocorrectSensitivity = KeyboardSettings.SENSITIVITY_BALANCED
        assertEquals(KeyboardSettings.SENSITIVITY_BALANCED, settings.autocorrectSensitivity)
    }

    @Test fun popularBrandsProductsSlangAndCountriesAreRecognizedAndCorrected() {
        // 1. Prefix completions for brands and countries
        val googleSuggestions = dictionary.findWordsWithPrefix("goog")
        assertTrue("Prefix 'goog' should suggest 'Google'", googleSuggestions.any { it.equals("Google", ignoreCase = true) })

        val netflixSuggestions = dictionary.findWordsWithPrefix("netf")
        assertTrue("Prefix 'netf' should suggest 'Netflix'", netflixSuggestions.any { it.equals("Netflix", ignoreCase = true) })

        val japanSuggestions = dictionary.findWordsWithPrefix("jap")
        assertTrue("Prefix 'jap' should suggest 'Japan'", japanSuggestions.any { it.equals("Japan", ignoreCase = true) })

        val rizzSuggestions = dictionary.findWordsWithPrefix("riz")
        assertTrue("Prefix 'riz' should suggest 'rizz'", rizzSuggestions.any { it.equals("rizz", ignoreCase = true) })

        // 2. Direct typo corrections for brands and countries
        mapOf(
            "googl" to "Google",
            "netflx" to "Netflix",
            "telsa" to "Tesla",
            "spotfy" to "Spotify",
            "japn" to "Japan",
            "canad" to "Canada",
            "marvl" to "Marvel"
        ).forEach { (typo, expected) ->
            val result = predict(typo)
            assertTrue("Typo '$typo' should autocorrect", result.isCenterAutocorrecting)
            assertEquals("Typo '$typo' should correct to '$expected'", expected, result.centerCandidate)
        }

        // 3. Modern slang and digital abbreviations are valid dictionary words
        listOf("rizz", "sus", "slay", "vibe", "goat", "ngl", "fr", "frfr", "lmk", "hmu", "idk", "tbh").forEach { word ->
            assertTrue("Word '$word' must be in dictionary", dictionary.isWordInDictionary(word))
        }
    }

    @Test fun gestureSwipeToTypeAccuratelyDecodesWords() {
        // 1. Swipe "the": 't' (0.45, 0.16) -> 'h' (0.60, 0.50) -> 'e' (0.25, 0.16)
        val thePath = listOf(
            android.graphics.PointF(0.45f, 0.16f),
            android.graphics.PointF(0.52f, 0.33f),
            android.graphics.PointF(0.60f, 0.50f),
            android.graphics.PointF(0.42f, 0.33f),
            android.graphics.PointF(0.25f, 0.16f)
        )
        val theResult = dictionary.decodeSwipePath(thePath)
        assertTrue("Swipe 'the' should decode 'the': $theResult", theResult.firstOrNull()?.equals("the", ignoreCase = true) == true)

        // 2. Swipe "good": 'g' (0.50, 0.50) -> 'o' (0.85, 0.16) -> 'd' (0.30, 0.50)
        val goodPath = listOf(
            android.graphics.PointF(0.50f, 0.50f),
            android.graphics.PointF(0.68f, 0.33f),
            android.graphics.PointF(0.85f, 0.16f),
            android.graphics.PointF(0.58f, 0.33f),
            android.graphics.PointF(0.30f, 0.50f)
        )
        val goodResult = dictionary.decodeSwipePath(goodPath)
        assertTrue("Swipe 'good' should decode 'good': $goodResult", goodResult.firstOrNull()?.equals("good", ignoreCase = true) == true)

        // 3. Swipe "world": 'w' (0.15, 0.16) -> 'o' (0.85, 0.16) -> 'r' (0.35, 0.16) -> 'l' (0.90, 0.50) -> 'd' (0.30, 0.50)
        val worldPath = listOf(
            android.graphics.PointF(0.15f, 0.16f),
            android.graphics.PointF(0.50f, 0.16f),
            android.graphics.PointF(0.85f, 0.16f),
            android.graphics.PointF(0.60f, 0.16f),
            android.graphics.PointF(0.35f, 0.16f),
            android.graphics.PointF(0.62f, 0.33f),
            android.graphics.PointF(0.90f, 0.50f),
            android.graphics.PointF(0.60f, 0.50f),
            android.graphics.PointF(0.30f, 0.50f)
        )
        val worldResult = dictionary.decodeSwipePath(worldPath)
        assertTrue("Swipe 'world' should decode 'world': $worldResult", worldResult.firstOrNull()?.equals("world", ignoreCase = true) == true)

        // 4. Swipe popular brand "Google": 'g' -> 'o' -> 'g' -> 'l' -> 'e'
        val googlePath = listOf(
            android.graphics.PointF(0.50f, 0.50f), // 'g'
            android.graphics.PointF(0.85f, 0.16f), // 'o'
            android.graphics.PointF(0.50f, 0.50f), // 'g'
            android.graphics.PointF(0.90f, 0.50f), // 'l'
            android.graphics.PointF(0.25f, 0.16f)  // 'e'
        )
        val googleResult = dictionary.decodeSwipePath(googlePath)
        assertTrue("Swipe 'google' should decode 'Google': $googleResult", googleResult.any { it.equals("Google", ignoreCase = true) })

        // 5. Swipe with context: 'thank' + swipe near 'y' -> 'o' -> 'u'
        val youPath = listOf(
            android.graphics.PointF(0.55f, 0.16f), // 'y'
            android.graphics.PointF(0.85f, 0.16f), // 'o'
            android.graphics.PointF(0.65f, 0.16f)  // 'u'
        )
        val youResult = dictionary.decodeSwipePath(youPath, prevWord = "thank")
        assertTrue("Swipe 'you' after 'thank' should decode 'you': $youResult", youResult.firstOrNull()?.equals("you", ignoreCase = true) == true)
    }

    @Test fun removedAndBlockedWordIsNeverSuggested() {
        // Learn a word first so it would otherwise be suggested
        dictionary.learnWord("supercalifragilistic", explicit = true)
        val suggestionsBefore = dictionary.getSuggestionsForPrefix("super")
        assertTrue("supercalifragilistic should be in suggestions", suggestionsBefore.any { it.equals("supercalifragilistic", ignoreCase = true) })

        // User removes the word via long-press bin action
        dictionary.blockSuggestion("supercalifragilistic")
        assertTrue("Word must be marked blocked", dictionary.isBlocked("supercalifragilistic"))
        assertTrue("Case insensitive check must be true", dictionary.isBlocked("SUPERCALIFRAGILISTIC"))

        // Prefix suggestions must not contain the blocked word
        val suggestionsAfter = dictionary.getSuggestionsForPrefix("super")
        assertFalse("supercalifragilistic must NOT be suggested after removal", suggestionsAfter.any { it.equals("supercalifragilistic", ignoreCase = true) })

        // Prediction engine must never return it in any slot
        val prediction = predict("supercali")
        assertNotEquals("supercalifragilistic", prediction.centerCandidate)
        assertNotEquals("supercalifragilistic", prediction.rightCandidate)

        // Unblocking allows it again
        dictionary.unblockSuggestion("supercalifragilistic")
        assertFalse("Word should no longer be blocked", dictionary.isBlocked("supercalifragilistic"))
    }
}


