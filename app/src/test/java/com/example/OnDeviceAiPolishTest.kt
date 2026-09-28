package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnDeviceAiPolishTest {

    @Test
    fun testAiOutputValidatorSanitizesOutput() {
        // Strip think tags
        val rawWithThink = "<think>Let's fix spelling</think>Hello world"
        val cleaned = AiOutputValidator.sanitize(rawWithThink, "helo wrld")
        assertEquals("Hello world", cleaned)

        // Strip markdown backticks
        val rawWithCode = "```\nHello world\n```"
        val cleanedCode = AiOutputValidator.sanitize(rawWithCode, "helo wrld")
        assertEquals("Hello world", cleanedCode)

        // Strip surrounding quotes
        val rawWithQuotes = "\"Hello world\""
        val cleanedQuotes = AiOutputValidator.sanitize(rawWithQuotes, "helo wrld")
        assertEquals("Hello world", cleanedQuotes)

        // Strip commentary header lines
        val rawWithCommentary = "Here is the corrected text:\nHello world"
        val cleanedCommentary = AiOutputValidator.sanitize(rawWithCommentary, "helo wrld")
        assertEquals("Hello world", cleanedCommentary)
    }

    @Test
    fun testKeyboardHeightShortEnforced() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = KeyboardSettings(context)
        assertEquals(KeyboardSettings.HEIGHT_SHORT, settings.height)
    }

    @Test
    fun testThemeSwitchingLightDarkNight() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = KeyboardSettings(context)

        // 1. Test Light theme
        settings.theme = KeyboardSettings.THEME_LIGHT
        assertEquals(KeyboardSettings.THEME_LIGHT, settings.theme)
        assertFalse(settings.isDarkMode)

        // 2. Test Dark theme
        settings.theme = KeyboardSettings.THEME_DARK
        assertEquals(KeyboardSettings.THEME_DARK, settings.theme)
        assertTrue(settings.isDarkMode)

        // 3. Test Night theme
        settings.theme = KeyboardSettings.THEME_NIGHT
        assertEquals(KeyboardSettings.THEME_NIGHT, settings.theme)
        assertTrue(settings.isDarkMode)
    }

    @Test
    fun testGboardAndSwiftKeyPredictiveSystems() {
        // 1. SwiftKey Multi-Order N-Gram Language Model
        val nGram = NGramLanguageModel()
        val nextWords = nGram.predictNextWords(listOf("how", "are"), prefix = "")
        assertTrue(nextWords.contains("you"))

        // 2. Gboard SymSpell Edit Distance Lookup
        val symSpell = SymSpellCorrectionEngine()
        symSpell.insertWord("hello", 1000)
        symSpell.insertWord("world", 900)
        val suggestions = symSpell.lookup("helo", maxDistance = 2f)
        assertTrue(suggestions.any { it.term == "hello" })

        // 3. Apple QuickType Candidate Slot Verification
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dict = DictionaryManager.getInstance(context)
        val gboardResult = dict.getGboardPredictions(
            rawTyped = "hell",
            contextWords = listOf("say"),
            tapCoords = null
        )
        assertNotNull(gboardResult.centerCandidate)
        assertTrue(gboardResult.centerCandidate.isNotEmpty())
    }
}
