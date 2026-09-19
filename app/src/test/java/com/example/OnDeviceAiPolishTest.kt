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
    fun testPolishPromptFactoryFormatsCorrectly() {
        val promptProofread = PolishPromptFactory.getSystemInstruction(PolishMode.PROOFREAD)
        assertTrue(promptProofread.contains("Proofread"))
        assertTrue(promptProofread.contains("text editing engine"))

        val promptProf = PolishPromptFactory.getSystemInstruction(PolishMode.PROFESSIONAL)
        assertTrue(promptProf.contains("Professional"))

        val promptCasual = PolishPromptFactory.getSystemInstruction(PolishMode.CASUAL)
        assertTrue(promptCasual.contains("Friendly"))

        val promptShorten = PolishPromptFactory.getSystemInstruction(PolishMode.SHORTEN)
        assertTrue(promptShorten.contains("Shorten"))

        val userMessage = PolishPromptFactory.buildUserMessage("helo wrld")
        assertTrue(userMessage.contains("helo wrld"))
        assertTrue(userMessage.contains("Text to edit:"))
    }

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
}
